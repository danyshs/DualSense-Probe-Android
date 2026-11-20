package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.TestFixtures
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NS_PER_MS = 1_000_000L

private fun usb(report: ByteArray, ms: Long) = FeedEvent(ms * NS_PER_MS, Route.DIRECT_USB, report, null)

private fun described(control: String, ms: Long, text: String = "row $control") =
    FeedEvent(ms * NS_PER_MS, Route.ANDROID, null, traceLine(control, Route.ANDROID, text), control)

private fun withSequence(r: ByteArray, seq: Int) = r.copyOf().also { it[7] = seq.toByte() }

class TraceModelTest {
    @Test fun theFirstReportIsTheBaselineAndTheNextChangeIsTheRowNewestOnTop() {
        val m = TraceModel()
        m.ingest(listOf(usb(TestFixtures.report("idle"), 0), usb(TestFixtures.report("cross"), 10), usb(TestFixtures.report("idle"), 20)))
        val controls = m.rows.map { it.control }
        assertTrue(controls.toString(), "in.button.cross" in controls)
        assertEquals("the release is the newest row", "in.button.cross", controls.first { it.startsWith("in.button") })
        val cross = m.rows.filter { it.control == "in.button.cross" }
        assertEquals(2, cross.size)
        assertTrue(cross[0].text.contains("decoded 1 -> 0") && cross[1].text.contains("decoded 0 -> 1"))
    }

    @Test fun oneRowPerChangeNotPerReport() {
        val m = TraceModel()
        val idle = TestFixtures.report("idle")
        m.ingest((0 until 50).map { usb(withSequence(idle, it), it.toLong()) })
        assertEquals(emptyList<TraceRow>(), m.rows)
    }

    @Test fun aSticksRowsAreRateLimitedPerControlButButtonsAreNot() {
        val m = TraceModel()
        val idle = TestFixtures.report("idle")
        val events = ArrayList<FeedEvent>()
        events.add(usb(idle, 0))
        for (i in 1..20) events.add(usb(idle.copyOf().also { it[1] = (i * 5).toByte() }, i.toLong() * 5)) // 5 ms apart, 100 ms in all
        m.ingest(events)
        val stick = m.rows.filter { it.control == "in.stick.left.x" }
        assertTrue("rows ${stick.size}", stick.size in 1..2)
        assertTrue(m.axisRowsSkipped >= 18)
        val buttons = TraceModel()
        buttons.ingest(listOf(usb(idle, 0), usb(TestFixtures.report("cross"), 1), usb(idle, 2), usb(TestFixtures.report("cross"), 3)))
        assertEquals(3, buttons.rows.count { it.control == "in.button.cross" })
    }

    @Test fun anAxisSpeaksAgainOnceTheIntervalHasPassed() {
        val m = TraceModel()
        val gap = AppLimits.ROUTE_TRACE_AXIS_INTERVAL_MS_MIN
        m.ingest(listOf(described("in.stick.left.x", 0), described("in.stick.left.x", gap - 1), described("in.stick.left.x", gap)))
        assertEquals(2, m.rows.size)
    }

    @Test fun describedEventsPassThroughWithTheirControlAndRoute() {
        val m = TraceModel()
        m.ingest(listOf(described("out.lightbar", 5)))
        val row = m.rows.single()
        assertEquals("out.lightbar", row.control)
        assertEquals(Route.ANDROID, row.route)
        assertEquals(5L, row.timeMs)
    }

    @Test fun theListHoldsTheNewestRowsMaxAndDropsTheOldest() {
        val m = TraceModel()
        m.ingest((0 until AppLimits.ROUTE_TRACE_ROWS_MAX + 25).map { described("in.button.cross", it.toLong(), "n$it") })
        assertEquals(AppLimits.ROUTE_TRACE_ROWS_MAX, m.rows.size)
        assertTrue(m.rows.first().text.endsWith("n524"))
        assertTrue(m.rows.last().text.endsWith("n25"))
    }

    @Test fun clearingStartsOverWithoutAStaleBaseline() {
        val m = TraceModel()
        m.ingest(listOf(usb(TestFixtures.report("idle"), 0)))
        m.clear()
        m.ingest(listOf(usb(TestFixtures.report("cross"), 5)))
        assertEquals(emptyList<TraceRow>(), m.rows)
    }

    @Test fun theVersionMovesOnlyWhenTheRowsDo() {
        val m = TraceModel()
        val v0 = m.version
        m.ingest(listOf(usb(TestFixtures.report("idle"), 0)))
        assertEquals(v0, m.version)
        m.ingest(listOf(described("in.button.cross", 1)))
        assertTrue(m.version > v0)
    }

    @Test fun statsCountDroppedReportsFromTheSequenceByteModulo256() {
        val s = FeedStats()
        val idle = TestFixtures.report("idle")
        val seqs = listOf(250, 251, 255, 0, 1, 3)
        s.ingest(seqs.mapIndexed { i, q -> usb(withSequence(idle, q), (i * 10).toLong()) })
        assertEquals(6L, s.reportsReceived)
        assertEquals(4L, s.reportsDropped) // 252, 253 and 254 missing, none between 255 and 0, then 2
        assertEquals(10L, s.longestGapMs)
    }

    @Test fun statsIgnoreOutputsAndDescribedEvents() {
        val s = FeedStats()
        s.ingest(listOf(described("in.button.cross", 0), FeedEvent(0L, Route.DIRECT_USB, ByteArray(63), "x", "out.lightbar")))
        assertEquals(0L, s.reportsReceived)
    }
}
