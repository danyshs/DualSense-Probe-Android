package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route

// six hex pairs joined by one kind of separator: how a Bluetooth address is written
private val BT_ADDRESS = Regex("(?i)\\b[0-9a-f]{2}([:-])[0-9a-f]{2}(\\1[0-9a-f]{2}){4}\\b")

const val BT_ADDRESS_MASK = "xx:xx:xx:xx:xx:xx"

private const val CONTROL_COLUMN_CHARS = 26
private const val ROUTE_COLUMN_CHARS = 7

/**
 * Strips anything shaped like a Bluetooth address. Runs over every line the screen shows and the
 * log writes.
 */
fun scrubAddresses(text: String): String = BT_ADDRESS.replace(text, BT_ADDRESS_MASK)

fun routeTag(route: Route): String = when (route) {
    Route.ANDROID -> "android"
    Route.DIRECT_USB -> "usb"
}

/** One finished trace line: the control, the route, then what was touched. One line, scrubbed. */
fun traceLine(control: String, route: Route, detail: String): String {
    require(control.isNotEmpty() && '\n' !in detail) { "a trace line is one line with a control" }
    return scrubAddresses(
        control.padEnd(CONTROL_COLUMN_CHARS) + " " + routeTag(route).padEnd(ROUTE_COLUMN_CHARS) +
            " " +
            detail,
    )
}

/**
 * One row of the trace: [control] is the ID the checklist matches by prefix, [text] the one-line
 * form the log keeps.
 * [headline] and [location] are the two lines the screen shows; empty means "derive them from
 * [text]".
 */
data class TraceRow(
    val timeMs: Long,
    val route: Route,
    val control: String,
    val text: String,
    val headline: String = "",
    val location: String = "",
)

/** What a row shows on the screen: what happened, then where it comes from or goes. */
data class RowLines(val headline: String, val location: String) {
    /** The two lines as one block, for the clipboard. */
    fun copyText(): String = headline + "\n" + location
}

/** The "Cross  pressed" style name: the last ID segment, capitalised. */
fun controlLabel(control: String): String =
    control.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * The two display lines of [row], scrubbed. A row built without them falls back to its control and
 * the detail part of its text.
 */
fun rowLines(row: TraceRow): RowLines {
    val marker = " " + routeTag(row.route).padEnd(ROUTE_COLUMN_CHARS) + " "
    val headline = row.headline.ifEmpty { row.control }
    val location = row.location.ifEmpty { row.text.substringAfter(marker, row.text).trim() }
    return RowLines(scrubAddresses(headline), scrubAddresses(location))
}

/**
 * True for the controls that would flood the list: sticks, triggers, motion and touch positions.
 */
fun isAxisControl(control: String): Boolean = when {
    control.startsWith("in.stick.") || control.startsWith("in.trigger.") -> true
    control.startsWith("in.gyro") || control.startsWith("in.accel") -> true
    control.startsWith("in.touch.") -> !control.endsWith(".id") && !control.endsWith(".active")
    else -> false
}
