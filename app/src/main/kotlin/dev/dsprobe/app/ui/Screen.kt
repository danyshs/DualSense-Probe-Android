package dev.dsprobe.app.ui

import dev.dsprobe.app.live.Frame
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState

/** The Outputs and Debug screens join with their own build steps. */
enum class Screen { LIVE, TOO_MANY }

/** One frame of everything the live screen draws: what the pad did and the time-based effects on it. */
data class LiveUi(val frame: Frame, val feel: FeelFrame) {
    companion object {
        val EMPTY = LiveUi(Frame.EMPTY, FeelFrame.EMPTY)
    }
}

fun screenFor(state: RouteState): Screen = if (state is RouteState.TooMany) Screen.TOO_MANY else Screen.LIVE

/** The title names the route that is live, or the one being left. */
fun routeOf(state: RouteState): Route = when (state) {
    is RouteState.DirectUsb, is RouteState.Releasing -> Route.DIRECT_USB
    else -> Route.ANDROID
}

fun padOf(state: RouteState): FoundPad? = when (state) {
    is RouteState.AndroidMode -> state.pad
    is RouteState.AskingUsbPermission -> state.pad
    is RouteState.DirectUsb -> state.pad
    is RouteState.Releasing -> state.pad
    is RouteState.WaitingForAndroid -> state.pad
    is RouteState.NoPad, is RouteState.TooMany, is RouteState.Stranded -> null
}

fun reducedOf(state: RouteState): List<Reduction> = (state as? RouteState.AndroidMode)?.reduced ?: emptyList()
