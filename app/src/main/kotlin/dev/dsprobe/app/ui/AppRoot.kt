package dev.dsprobe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.ui.theme.DsProbeTheme

private val STACK_SAVER = listSaver<ScreenStack, Int>(
    save = { stack -> stack.items.map { it.ordinal } },
    restore = { ordinals -> ScreenStack(ordinals.map { Screen.entries[it] }) },
)

@Composable
fun AppRoot(session: PadSession) {
    val state by session.controller.state.collectAsState()
    val answers by session.androidRoute.answers.collectAsState()
    val shown by session.outputs.shown.collectAsState()
    var stack by rememberSaveable(stateSaver = STACK_SAVER) { mutableStateOf(ScreenStack()) }
    val locked = lockedFor(state, answers)
    val actions = remember(session) {
        ScreenActions(
            onRefreshAnswers = session.androidRoute::requery,
            onSwitch = session.controller::switchToDirectUsb,
            onBack = session.controller::backToAndroidMode,
            onCrashNow = if (session.crashNowAvailable) session::crashNow else null,
            onOpen = { stack = stack.push(it) },
            onUp = { stack = stack.pop() },
        )
    }
    val hub = session.debug
    val runner = remember(session) { session.newRunner() }
    val pump = remember { FeedPump() }
    val screen = screenFor(state, stack)
    // one subscription for all three feed screens, so moving between them keeps the trace
    val feedOn = screen in FEED_SCREENS
    DisposableEffect(feedOn) {
        if (feedOn) hub.open()
        onDispose { if (feedOn) hub.close() }
    }
    if (feedOn) LaunchedEffect(session) { pump.run(session, runner) }
    BackHandler(enabled = stack.canPop && state !is RouteState.TooMany) { actions.onUp() }
    DsProbeTheme {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val view = RouteView(state, locked, answers, shown)
                when (screen) {
                    Screen.LIVE -> LiveScreen(view, session.live, actions)
                    Screen.OUTPUTS -> OutputsScreen(view, session.live, session.outputs, actions)
                    Screen.DEBUG -> DebugScreen(session, runner, pump, actions)
                    Screen.GUIDED -> GuidedScreen(session, runner, view, pump, actions)
                    Screen.LOG -> LogScreen(session, runner, actions)
                    Screen.TOO_MANY -> TooManyScreen()
                }
            }
        }
    }
}
