package dev.dsprobe.app.ui

import dev.dsprobe.app.live.Frame
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.route.AndroidAnswers
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.route.reductions

enum class Screen { LIVE, OUTPUTS, DEBUG, TOO_MANY }

/** One frame of everything the live screen draws: what the pad did and the time-based effects on it. */
data class LiveUi(val frame: Frame, val feel: FeelFrame) {
    companion object {
        val EMPTY = LiveUi(Frame.EMPTY, FeelFrame.EMPTY)
    }
}

/** Too many pads wins over everything; then Debug Mode, then Outputs, while the user asked for them. */
fun screenFor(state: RouteState, wantsOutputs: Boolean, wantsDebug: Boolean = false): Screen = when {
    state is RouteState.TooMany -> Screen.TOO_MANY
    wantsDebug -> Screen.DEBUG
    wantsOutputs -> Screen.OUTPUTS
    else -> Screen.LIVE
}

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

/** What the screens can ask of the app; the callbacks of the buttons they draw. */
class ScreenActions(
    val onRefreshAnswers: () -> Unit,
    val onSwitch: () -> Unit,
    val onBack: () -> Unit,
    val onCrashNow: (() -> Unit)?,
    val onOpenOutputs: () -> Unit,
    val onCloseOutputs: () -> Unit,
    val onOpenDebug: () -> Unit,
    val onCloseDebug: () -> Unit,
)

/** What Android offers nothing for, live. Only Android mode asks Android; Direct USB locks nothing. */
fun lockedFor(state: RouteState, answers: AndroidAnswers?): List<Reduction> =
    if (state is RouteState.AndroidMode && answers != null) reductions(answers) else emptyList()

/** Outputs can go somewhere only while a route is live. */
fun canSendOutputs(state: RouteState): Boolean = state is RouteState.AndroidMode || state is RouteState.DirectUsb
