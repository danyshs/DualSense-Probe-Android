package dev.dsprobe.app.route

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.OutputField
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

private const val POLL_STEP_MS = 5L
private const val POLL_MS_MAX = 3_000L
private const val FAKE_WAIT_MS = 15L
private const val SLOW_WRITE_MS = 60L

/** Hands out scripted reads, then timeouts; records what the route did to it, in order. */
private class FakeLink(private val script: List<ReadResult>) : UsbLink {
    val events: MutableList<String> = Collections.synchronizedList(ArrayList())
    val writes = CopyOnWriteArrayList<ByteArray>()
    private var next = 0

    /**
     * A slow pad: a write still in flight is what a release that skips the writer join would
     * trample.
     */
    @Volatile var writeDelayMs = 0L

    @Volatile private var readerThread: Thread? = null

    @Volatile private var writerThread: Thread? = null

    override fun readReport(timeoutMs: Int): ReadResult {
        readerThread = Thread.currentThread()
        val i = next++
        if (i < script.size) return script[i]
        Thread.sleep(FAKE_WAIT_MS)
        return ReadResult.Timeout
    }

    override fun writeReport(report: ByteArray): Boolean {
        if (Thread.currentThread().name.endsWith("writer")) writerThread = Thread.currentThread()
        Thread.sleep(writeDelayMs)
        writes.add(report.copyOf())
        events.add("write")
        return true
    }

    /** What a feature read answers; null is a pad that doesn't. */
    @Volatile var firmware: ByteArray? = null

    override fun readFeature(reportId: Int, length: Int): ByteArray? = firmware

    override fun releaseInterfaces() {
        events.add(
            "release readerAlive=${readerThread?.isAlive} writerAlive=${writerThread?.isAlive}",
        )
    }

    override fun close() {
        events.add("close")
    }
}

class DirectUsbRouteTest {
    private val live = LiveState()
    private val faults = CopyOnWriteArrayList<String>()
    private var route: DirectUsbRoute? = null

    private fun open(vararg script: ReadResult): FakeLink {
        val link = FakeLink(script.toList())
        route = DirectUsbRoute(link, live, EventFeed(), { faults.add(it) }, {}).also { it.start() }
        return link
    }

    private fun report(name: String) = ReadResult.Report(TestFixtures.report(name))

    private fun waitUntil(what: String, cond: () -> Boolean) {
        var waited = 0L
        while (!cond()) {
            check(waited < POLL_MS_MAX) { "gave up waiting for $what" }
            Thread.sleep(POLL_STEP_MS)
            waited += POLL_STEP_MS
        }
    }

    @After fun closeWhateverIsLeft() {
        route?.release()
    }

    @Test fun inputReportsReachLiveStateThroughTheParser() {
        open(report("mute"), report("touch-1"))
        waitUntil("the touch report") { live.takeFrame().state.touches.size == 2 }
        val s = live.takeFrame().state
        assertEquals(2, s.touches.size)
        assertEquals(TestFixtures.report("touch-1")[7].toInt() and 0xff, s.sequence)
    }

    @Test fun muteIsHeldWhileItsReportIsTheNewestOne() {
        open(report("mute"))
        waitUntil("the mute report") { Button.MUTE in live.takeFrame().state.held }
    }

    @Test fun aBadReportIsCountedAndDroppedNotPublishedAndNotAFault() {
        open(ReadResult.Report(ByteArray(10)), report("cross"))
        waitUntil("the good report") { Button.CROSS in live.takeFrame().state.held }
        assertEquals(1L, route!!.rejectedCount)
        assertTrue(faults.isEmpty())
    }

    @Test fun aFailedReadIsOneFaultAndTheReaderStops() {
        open(ReadResult.Failed("gone"), ReadResult.Failed("gone again"))
        waitUntil("the fault") { faults.isNotEmpty() }
        assertEquals(1, faults.size)
        assertTrue(faults[0].contains("gone"))
    }

    @Test fun releaseSendsAllOffLastThenReleasesTheInterfaceThenClosesWithBothThreadsGone() {
        val link = open(report("idle"))
        link.writeDelayMs = SLOW_WRITE_MS
        waitUntil("the first report") { live.takeFrame().state.sequence != null }
        route!!.writer.change(OutputField.LIGHTBAR) {
            it.copy(lightbar = dev.dsprobe.pad.Rgb(9u, 9u, 9u))
        }
        waitUntil("the lightbar write") { link.writes.size == 1 }
        route!!.release()
        assertArrayEquals(TestFixtures.allOffGolden, link.writes.last())
        assertEquals(2, link.writes.size)
        assertEquals(
            listOf("write", "write", "release readerAlive=false writerAlive=false", "close"),
            link.events.toList(),
        )
        assertNull(DirectUsbRoute.current)
    }

    @Test fun releaseWithNothingEverSetStillSendsAllOff() {
        val link = open()
        route!!.release()
        assertEquals(1, link.writes.size)
        assertArrayEquals(TestFixtures.allOffGolden, link.writes[0])
    }

    @Test fun releaseTwiceDoesItsWorkOnce() {
        val link = open()
        route!!.release()
        route!!.release()
        assertEquals(1, link.writes.size)
        assertEquals(1, link.events.count { it.startsWith("release") })
        assertEquals(1, link.events.count { it == "close" })
    }

    @Test fun theCrashPathSendsOneAllOffItselfAndReleasesOnceWithoutJoining() {
        val link = open()
        assertSame(route, DirectUsbRoute.current)
        route!!.emergencyRelease()
        assertEquals(1, link.writes.size)
        assertArrayEquals(TestFixtures.allOffGolden, link.writes[0])
        assertNull(DirectUsbRoute.current)
        route!!.release()
        assertEquals(
            "the writer is halted: the normal release sends nothing more",
            1,
            link.writes.size,
        )
        assertEquals(1, link.events.count { it.startsWith("release") })
        assertEquals(1, link.events.count { it == "close" })
    }

    @Test fun afterAReleaseTheScreenStateIsEmpty() {
        open(report("cross"))
        waitUntil("the report") { Button.CROSS in live.takeFrame().state.held }
        route!!.release()
        assertEquals(emptySet<Button>(), live.takeFrame().state.held)
    }
}
