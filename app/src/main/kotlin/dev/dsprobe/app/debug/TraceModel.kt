package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.pad.Limits

private const val NANOS_PER_MS = 1_000_000L

/**
 * Turns the feed into the Route trace's rows, newest first and bounded. A bare raw report is a
 * Direct USB input and is decoded against INPUT_MAP; every other event arrives described. Axes get at
 * most one row per interval. Main thread only.
 */
class TraceModel {
    private val kept = ArrayDeque<TraceRow>()
    private var previousReport: ByteArray? = null
    private val lastAxisRowMs = HashMap<String, Long>()

    /** Axis rows skipped because their control already had a row inside the interval. */
    var axisRowsSkipped = 0L
        private set

    val rows: List<TraceRow> get() = kept.toList()

    /** The newest Direct USB input report, for cards that show a live raw value. */
    val latestReport: ByteArray? get() = previousReport

    /** Goes up whenever the rows change, so the screen copies the list only then. */
    var version = 0L
        private set

    /** Returns the rows this call added, so a listener sees only what is new. */
    fun ingest(events: List<FeedEvent>): List<TraceRow> {
        val before = version
        val fresh = ArrayList<TraceRow>()
        for (event in events) {
            val timeMs = event.timeNs / NANOS_PER_MS
            val raw = event.raw
            if (event.control == null && raw != null) {
                usbInput(raw, timeMs, fresh)
            } else if (event.described != null && event.control != null) {
                val row = TraceRow(timeMs, event.route, event.control, event.described, event.headline.orEmpty(), event.location.orEmpty())
                if (add(row)) fresh.add(row)
            }
        }
        check(version - before == fresh.size.toLong()) { "fresh rows and version disagree" }
        return fresh
    }

    private fun usbInput(report: ByteArray, timeMs: Long, fresh: MutableList<TraceRow>) {
        val prev = previousReport
        previousReport = report
        // a report of the wrong shape was counted by the route; there is nothing to decode
        if (prev == null || report.size != prev.size || report.size != Limits.USB_INPUT_REPORT_BYTES) return
        for (row in usbInputRows(prev, report, timeMs)) if (add(row)) fresh.add(row)
    }

    private fun add(row: TraceRow): Boolean {
        if (isAxisControl(row.control)) {
            val last = lastAxisRowMs[row.control]
            if (last != null && row.timeMs - last < AppLimits.ROUTE_TRACE_AXIS_INTERVAL_MS_MIN) {
                axisRowsSkipped += 1
                return false
            }
            lastAxisRowMs[row.control] = row.timeMs
        }
        kept.addFirst(row)
        if (kept.size > AppLimits.ROUTE_TRACE_ROWS_MAX) kept.removeLast()
        version += 1
        return true
    }

    fun clear() {
        kept.clear()
        version += 1
        previousReport = null
        lastAxisRowMs.clear()
    }
}
