package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route

// six hex pairs joined by one kind of separator: how a Bluetooth address is written
private val BT_ADDRESS = Regex("(?i)\\b[0-9a-f]{2}([:-])[0-9a-f]{2}(\\1[0-9a-f]{2}){4}\\b")

const val BT_ADDRESS_MASK = "xx:xx:xx:xx:xx:xx"

private const val CONTROL_COLUMN_CHARS = 26

/** Strips anything shaped like a Bluetooth address. Runs over every line the screen shows and the log writes. */
fun scrubAddresses(text: String): String = BT_ADDRESS.replace(text, BT_ADDRESS_MASK)

fun routeTag(route: Route): String = when (route) {
    Route.ANDROID -> "android"
    Route.DIRECT_USB -> "usb"
}

/** One finished trace line: the control, the route, then what was touched. One line, scrubbed. */
fun traceLine(control: String, route: Route, detail: String): String {
    require(control.isNotEmpty() && '\n' !in detail) { "a trace line is one line with a control" }
    return scrubAddresses(control.padEnd(CONTROL_COLUMN_CHARS) + " " + routeTag(route).padEnd(7) + " " + detail)
}

/** One row of the Route trace: [control] is the ID the follow filter matches by prefix. */
data class TraceRow(val timeMs: Long, val route: Route, val control: String, val text: String)

/** True for the controls that would flood the list: sticks, triggers, motion and touch positions. */
fun isAxisControl(control: String): Boolean = when {
    control.startsWith("in.stick.") || control.startsWith("in.trigger.") -> true
    control.startsWith("in.gyro") || control.startsWith("in.accel") -> true
    control.startsWith("in.touch.") -> !control.endsWith(".id") && !control.endsWith(".active")
    else -> false
}
