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

    /** Goes up whenever the rows change, so the screen copies the list only then. */
    var version = 0L
        private set

    fun ingest(events: List<FeedEvent>) {
        for (event in events) {
            val timeMs = event.timeNs / NANOS_PER_MS
            val raw = event.raw
            if (event.control == null && raw != null) {
                usbInput(raw, timeMs)
            } else if (event.described != null && event.control != null) {
                add(TraceRow(timeMs, event.route, event.control, event.described))
            }
        }
    }

    private fun usbInput(report: ByteArray, timeMs: Long) {
        val prev = previousReport
        previousReport = report
        // a report of the wrong shape was counted by the route; there is nothing to decode
        if (prev == null || report.size != prev.size || report.size != Limits.USB_INPUT_REPORT_BYTES) return
        for (row in usbInputRows(prev, report, timeMs)) add(row)
    }

    private fun add(row: TraceRow) {
        if (isAxisControl(row.control)) {
            val last = lastAxisRowMs[row.control]
            if (last != null && row.timeMs - last < AppLimits.ROUTE_TRACE_AXIS_INTERVAL_MS_MIN) {
                axisRowsSkipped += 1
                return
            }
            lastAxisRowMs[row.control] = row.timeMs
        }
        kept.addFirst(row)
        if (kept.size > AppLimits.ROUTE_TRACE_ROWS_MAX) kept.removeLast()
        version += 1
    }

    fun clear() {
        kept.clear()
        version += 1
        previousReport = null
        lastAxisRowMs.clear()
    }
}

/** The rows that belong to the followed control: its ID or anything under it. Null follows nothing. */
fun followRows(rows: List<TraceRow>, follow: String?): List<TraceRow> =
    if (follow == null) rows else rows.filter { it.control == follow || it.control.startsWith("$follow.") }
