package dev.dsprobe.app.route

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Rgb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val POLL_STEP_MS = 5L
private const val POLL_MS_MAX = 3_000L
private const val IDLE_WAIT_MS = 10L

/** Scripted reads, then timeouts; every write is kept. */
private class ScriptLink(private val script: List<ReadResult>) : UsbLink {
    val writes = CopyOnWriteArrayList<ByteArray>()
    private var next = 0

    override fun readReport(timeoutMs: Int): ReadResult {
        val i = next++
        if (i < script.size) return script[i]
        Thread.sleep(IDLE_WAIT_MS)
        return ReadResult.Timeout
    }

    override fun writeReport(report: ByteArray): Boolean {
        writes.add(report.copyOf())
        return true
    }

    override fun readFeature(reportId: Int, length: Int): ByteArray? = null
    override fun releaseInterfaces() = Unit
    override fun close() = Unit
}

class DirectUsbTraceTest {
    private val feed = EventFeed()
    private var route: DirectUsbRoute? = null
    private val collected = CopyOnWriteArrayList<FeedEvent>()

    private fun open(vararg script: ReadResult): ScriptLink {
        val link = ScriptLink(script.toList())
        route = DirectUsbRoute(link, LiveState(), feed, {}, {}).also { it.start() }
        return link
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        var waited = 0L
        while (true) {
            collected.addAll(feed.drain())
            if (cond()) return
            check(waited < POLL_MS_MAX) { "gave up waiting for $what" }
            Thread.sleep(POLL_STEP_MS)
            waited += POLL_STEP_MS
        }
    }

    @After fun closeWhateverIsLeft() {
        route?.release()
    }

    @Test fun anOutputSendReachesTheFeedWithItsBytesTheChangedByteAndTheRequest() {
        feed.subscribe()
        val link = open()
        assertEquals(ApiAnswer.Ok, route!!.send(OutputRequest.Lightbar(Rgb(255u, 0u, 0u))))
        waitFor("the lightbar row") { collected.any { it.control == "out.lightbar" } }
        val event = collected.first { it.control == "out.lightbar" }
        val text = checkNotNull(event.described)
        assertTrue(text, text.contains("request Lightbar(rgb ff0000)") && text.contains("bytes 45: 00 -> ff") && text.contains("sent ok"))
        assertTrue(text, text.contains("[P43]"))
        assertEquals("the bytes in the row are the bytes the pad got", link.writes.first().toList(), checkNotNull(event.raw).toList())
        assertEquals(63, event.raw.size)
    }

    @Test fun theSecondColourListsOldToNewAgainstWhatThePadWasToldNotAgainstZero() {
        feed.subscribe()
        open()
        route!!.send(OutputRequest.Lightbar(Rgb(255u, 0u, 0u)))
        waitFor("the first row") { collected.count { it.control == "out.lightbar" } == 1 }
        route!!.send(OutputRequest.Lightbar(Rgb(0u, 255u, 0u)))
        waitFor("the second row") { collected.count { it.control == "out.lightbar" } == 2 }
        val text = checkNotNull(collected.last { it.control == "out.lightbar" }.described)
        assertTrue(text, text.contains("bytes 45: ff -> 00, 46: 00 -> ff"))
    }

    @Test fun theShadowKeepsMovingWhileNobodyIsSubscribedSoALateSubscriberStillSeesTrueOldValues() {
        open()
        route!!.send(OutputRequest.Lightbar(Rgb(255u, 0u, 0u)))
        // sentCount moves after the trace has seen the send, so the shadow already holds red
        waitFor("the first send") { route!!.writer.sentCount >= 1 }
        feed.subscribe()
        route!!.send(OutputRequest.Lightbar(Rgb(0u, 255u, 0u)))
        waitFor("the row after subscribing") { collected.any { it.control == "out.lightbar" } }
        assertTrue(checkNotNull(collected.first { it.control == "out.lightbar" }.described).contains("45: ff -> 00"))
    }

    @Test fun inputReportsReachTheFeedWithTheirBytesAndTheParsedState() {
        feed.subscribe()
        open(ReadResult.Report(TestFixtures.report("cross")))
        waitFor("the report") { collected.any { it.raw?.size == 64 } }
        val event = collected.first { it.raw?.size == 64 }
        assertEquals(null, event.control)
        assertNotNull(event.state)
        assertTrue(Button.CROSS in event.state!!.held)
        assertEquals(TestFixtures.report("cross").toList(), event.raw!!.toList())
    }

    @Test fun aRejectedReportStillReachesTheFeedWithoutAState() {
        feed.subscribe()
        open(ReadResult.Report(ByteArray(10)))
        waitFor("the short report") { collected.any { it.raw?.size == 10 } }
        assertEquals(null, collected.first { it.raw?.size == 10 }.state)
    }

    @Test fun nothingIsPublishedWhileNobodyIsSubscribed() {
        val link = open(ReadResult.Report(TestFixtures.report("cross")))
        route!!.send(OutputRequest.Lightbar(Rgb(1u, 2u, 3u)))
        waitFor("the write") { link.writes.isNotEmpty() }
        Thread.sleep(IDLE_WAIT_MS * 3)
        feed.subscribe()
        assertEquals(emptyList<FeedEvent>(), feed.drain())
    }
}
