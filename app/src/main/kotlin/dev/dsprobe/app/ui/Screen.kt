package dev.dsprobe.app.ui

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.Frame
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.route.AndroidAnswers
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.route.reductions

enum class Screen { LIVE, OUTPUTS, DEBUG, GUIDED, LOG, TOO_MANY }

/** The screens that read the pad's event feed: it is subscribed only while one of them is on top. */
val FEED_SCREENS: Set<Screen> = setOf(Screen.DEBUG, Screen.GUIDED, Screen.LOG)

/**
 * Where the user has been, newest last, Live first. Back pops exactly one step. Opening a screen that is
 * already in the stack goes back to it, so the stack never holds a screen twice and never outgrows the screens.
 */
data class ScreenStack(val items: List<Screen> = listOf(Screen.LIVE)) {
    init {
        require(items.isNotEmpty() && items.first() == Screen.LIVE) { "stack must start at Live: $items" }
        require(Screen.TOO_MANY !in items) { "Too many pads is a state, not a place to go back to" }
        require(items.size <= AppLimits.SCREEN_STACK_DEPTH_MAX && items.toSet().size == items.size) { "stack depth or repeat: $items" }
    }

    val top: Screen get() = items.last()

    val canPop: Boolean get() = items.size > 1

    fun push(screen: Screen): ScreenStack {
        val at = items.indexOf(screen)
        return if (at >= 0) ScreenStack(items.take(at + 1)) else ScreenStack(items + screen)
    }

    fun pop(): ScreenStack = if (canPop) ScreenStack(items.dropLast(1)) else this
}

/** One frame of everything the live screen draws: what the pad did and the time-based effects on it. */
data class LiveUi(val frame: Frame, val feel: FeelFrame) {
    companion object {
        val EMPTY = LiveUi(Frame.EMPTY, FeelFrame.EMPTY)
    }
}

/** Too many pads wins over everything; otherwise the screen on top of the stack. */
fun screenFor(state: RouteState, stack: ScreenStack): Screen = if (state is RouteState.TooMany) Screen.TOO_MANY else stack.top

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
    val onOpen: (Screen) -> Unit,
    val onUp: () -> Unit,
)

/** What Android offers nothing for, live. Only Android mode asks Android; Direct USB locks nothing. */
fun lockedFor(state: RouteState, answers: AndroidAnswers?): List<Reduction> =
    if (state is RouteState.AndroidMode && answers != null) reductions(answers) else emptyList()

/** Outputs can go somewhere only while a route is live. */
fun canSendOutputs(state: RouteState): Boolean = state is RouteState.AndroidMode || state is RouteState.DirectUsb
