package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.Route

/**
 * The rows a guided card spends in its main area (one row is about 48 dp), by part. The sum has
 * to stay under [AppLimits.GUIDED_MAIN_ROWS_MAX] so a card never scrolls.
 */
data class MainArea(
    val head: Int,
    val readout: Int,
    val controls: Int,
    val reaction: Int,
    val expected: Int,
) {
    val total: Int get() = head + readout + controls + reaction + expected
}

private const val HEAD_ROWS = 2 // title with Details, then the confidence chip
private const val EXPECTED_ROWS = 2 // the one line of Expected here
private const val REACTION_ROWS = 2 // "Did the pad react?" and its three buttons
private const val REASON_ROWS = 1

private fun readoutRows(kind: ReadoutKind): Int = when (kind) {
    ReadoutKind.STICK -> 5
    ReadoutKind.TRIGGER -> 3
    ReadoutKind.BUTTONS -> 3
    ReadoutKind.MOTION -> 5
    ReadoutKind.FINGER -> 6
    ReadoutKind.TEXT -> 1
    ReadoutKind.BYTES -> 2
    ReadoutKind.NONE -> 0
}

private fun controlRows(kind: TryKind): Int = when (kind) {
    TryKind.LIGHTBAR -> 3
    TryKind.PLAYER_LEDS -> 3
    TryKind.MUTE_LED -> 2
    TryKind.RUMBLE -> 3
    TryKind.TRIGGERS -> 2
    TryKind.ALL_OFF -> 5
    TryKind.MIC -> 3
    TryKind.SPEAKER, TryKind.HEADPHONES -> 2
}

/** What a card declares for its main area. */
fun mainAreaOf(card: EndpointCard): MainArea {
    val tryIt = card.tryIt
    val isAction = tryIt is TryIt.Action
    return MainArea(
        head = HEAD_ROWS,
        readout = readoutRows(readoutKindOf(card)),
        controls = if (tryIt is TryIt.Action) controlRows(tryIt.kind) else REASON_ROWS,
        reaction = if (isAction) REACTION_ROWS else 0,
        expected = EXPECTED_ROWS,
    )
}

/**
 * The words the main area shows: the title and the one line of what to expect on the live
 * connection (none while no pad is connected).
 */
fun mainTextOf(card: EndpointCard, connection: Connection): List<String> {
    val expected =
        if (connection == Connection.NONE) emptyList() else listOf(expectationOn(card.id, connection).text)
    return listOf(card.title) + expected
}

/** The words the Details sheet carries: every location line and the guess. */
fun detailsTextOf(card: EndpointCard, route: Route, playerLeds: Int, noAndroidEvent: String): List<String> =
    locationLines(card, playerLeds, route, noAndroidEvent) + guessText(card, route)

/** The Back chevron shows after the first card, until the user has swiped once. */
fun chevronBackShown(page: Int, hasSwiped: Boolean): Boolean = page > 0 && !hasSwiped

/** The Next chevron shows until the last page, until the user has swiped once. */
fun chevronNextShown(page: Int, pageCount: Int, hasSwiped: Boolean): Boolean {
    require(pageCount > 0 && page in 0 until pageCount) { "page $page of $pageCount" }
    return page < pageCount - 1 && !hasSwiped
}
