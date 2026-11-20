package dev.dsprobe.app.route

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.pad.RumbleMode
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val POLL_STEP_MS = 5L
private const val POLL_MS_MAX = 3_000L
private const val IDLE_READ_MS = 5L
private const val HALF: UByte = 0x80u

private class WriteLink(private val firmware: ByteArray?) : UsbLink {
    val writes = CopyOnWriteArrayList<ByteArray>()

    override fun readReport(timeoutMs: Int): ReadResult {
        Thread.sleep(IDLE_READ_MS)
        return ReadResult.Timeout
    }

    override fun writeReport(report: ByteArray): Boolean {
        writes.add(report.copyOf())
        return true
    }

    override fun readFeature(reportId: Int, length: Int): ByteArray? = firmware

    override fun releaseInterfaces() = Unit

    override fun close() = Unit
}

class DirectUsbRumbleModeTest {
    private var route: DirectUsbRoute? = null
    private lateinit var link: WriteLink

    private fun open(firmware: ByteArray?): DirectUsbRoute {
        link = WriteLink(firmware)
        return DirectUsbRoute(link, LiveState(), EventFeed(), {}, {}).also {
            route = it
            it.start()
        }
    }

    @After fun closeWhateverIsLeft() {
        route?.release()
    }

    private fun waitForWrites(n: Int) {
        var waited = 0L
        while (link.writes.size < n) {
            check(waited < POLL_MS_MAX) { "gave up waiting for write $n, have ${link.writes.size}" }
            Thread.sleep(POLL_STEP_MS)
            waited += POLL_STEP_MS
        }
    }

    /** Picks [mode], rumbles both motors, stops, and checks both reports against the goldens named. */
    private fun pickAndCheck(mode: RumbleMode, both: String, off: String) {
        val r = route!!
        assertEquals(ApiAnswer.Ok, r.send(OutputRequest.SetRumbleMode(mode)))
        assertEquals(mode, r.rumbleMode)
        val before = link.writes.size
        r.send(OutputRequest.Rumble(HALF, HALF, 1_000))
        waitForWrites(before + 1)
        assertArrayEquals(mode.name + " both", TestFixtures.bytes("golden/$both.hex"), link.writes[before])
        r.send(OutputRequest.Rumble(0u, 0u, 1))
        waitForWrites(before + 2)
        assertArrayEquals(mode.name + " off", TestFixtures.bytes("golden/$off.hex"), link.writes[before + 1])
    }

    @Test fun theDefaultComesFromTheFirmwareAndEitherModeCanBePickedAfterwards() {
        open(TestFixtures.bytes("feature/firmware-0x20.hex"))
        assertEquals(RumbleMode.V2, route!!.rumbleMode)
        pickAndCheck(RumbleMode.COMPATIBLE, "rumble-both", "rumble-off")
        pickAndCheck(RumbleMode.V2, "rumble-v2-both", "rumble-v2-off")
    }

    @Test fun withoutAFirmwareAnswerTheDefaultIsCompatibleAndV2CanStillBePicked() {
        open(null)
        assertEquals(RumbleMode.COMPATIBLE, route!!.rumbleMode)
        pickAndCheck(RumbleMode.V2, "rumble-v2-both", "rumble-v2-off")
        pickAndCheck(RumbleMode.COMPATIBLE, "rumble-both", "rumble-off")
    }

    @Test fun pickingAModeSendsNothingByItself() {
        open(null)
        route!!.send(OutputRequest.SetRumbleMode(RumbleMode.V2))
        Thread.sleep(4 * IDLE_READ_MS)
        assertTrue(link.writes.isEmpty())
    }

    @Test fun aPatternThroughTheRouteStartsWithItsFirstStepAndEndsAtRumbleOff() {
        open(null)
        val steps = listOf(RumbleStep(128, 128, 30), RumbleStep(0, 0, 30))
        assertEquals(ApiAnswer.Ok, route!!.send(OutputRequest.RumblePattern(steps)))
        waitForWrites(2)
        assertArrayEquals(TestFixtures.bytes("golden/rumble-both.hex"), link.writes[0])
        assertArrayEquals(TestFixtures.bytes("golden/rumble-off.hex"), link.writes[1])
    }

    @Test fun aModeOrPatternAfterTheReleaseIsRefused() {
        open(null)
        route!!.release()
        assertTrue(route!!.send(OutputRequest.SetRumbleMode(RumbleMode.V2)) is ApiAnswer.Failed)
        assertTrue(route!!.send(OutputRequest.RumblePattern(listOf(RumbleStep(1, 1, 10)))) is ApiAnswer.Failed)
    }
}
