package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits

/**
 * The lines the guided test's log sheet shows, newest first. By default only the rows of the open
 * card's
 * endpoint; [showAll] lifts the filter. A card that has no rows of its own (a free report range)
 * shows what
 * the tour saw under it. Never more than [AppLimits.SHEET_ROWS_MAX].
 */
fun sheetLines(
    rows: List<TraceRow>,
    seen: List<RowLines>,
    card: EndpointCard,
    showAll: Boolean,
): List<RowLines> {
    val own = if (showAll) {
        rows
    } else {
        rows.filter { row ->
            card.listens.any { controlUnder(row.control, it) }
        }
    }
    val ordered = own.sortedByDescending { it.timeMs }
    if (ordered.isEmpty() && !showAll) return seen.asReversed().take(AppLimits.SHEET_ROWS_MAX)
    return ordered.take(AppLimits.SHEET_ROWS_MAX).map(::rowLines)
}
