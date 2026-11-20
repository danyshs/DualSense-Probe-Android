package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.EventFeed

/**
 * Debug Mode's one subscription to the feed: while it is open the trace, the footer counters and the
 * runner all read the same drained events. Closed, the feed publishes nothing. Main thread only.
 */
class DebugHub(private val feed: EventFeed) {
    val trace = TraceModel()
    val stats = FeedStats()
    private val saved = LinkedHashSet<TraceRow>()
    private var isOpen = false

    val feedOverflow: Long get() = feed.overflowCount

    fun open() {
        if (isOpen) return
        isOpen = true
        trace.clear()
        feed.subscribe()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        feed.unsubscribe()
    }

    /** Once per frame while the screen is open: drain, then let each reader see the events. */
    fun pump(runner: DebugRunner?) {
        if (!isOpen) return
        val events = feed.drain()
        trace.ingest(events)
        stats.ingest(events)
        runner?.onEvents(events)
        runner?.tick()
    }

    /** "Save": these rows go into the log even after they scroll out of the visible list. */
    fun save(rows: List<TraceRow>) {
        for (row in rows) {
            if (saved.size >= AppLimits.ROUTE_TRACE_ROWS_MAX) return
            saved.add(row)
        }
    }

    /** Saved rows and the ones still on the list, newest first, each once. */
    fun logRows(): List<TraceRow> =
        (saved + trace.rows).distinct().sortedByDescending { it.timeMs }.take(AppLimits.ROUTE_TRACE_ROWS_MAX * 2)
}
