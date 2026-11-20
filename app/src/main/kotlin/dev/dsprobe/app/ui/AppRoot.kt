package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.DsProbeTheme

@Composable
fun AppRoot(session: PadSession) {
    val state by session.controller.state.collectAsState()
    val answers by session.androidRoute.answers.collectAsState()
    val shown by session.outputs.shown.collectAsState()
    var wantsOutputs by rememberSaveable { mutableStateOf(false) }
    val locked = lockedFor(state, answers)
    val actions = remember(session) {
        ScreenActions(
            onRefreshAnswers = session.androidRoute::requery,
            onSwitch = session.controller::switchToDirectUsb,
            onBack = session.controller::backToAndroidMode,
            onCrashNow = if (session.crashNowAvailable) session::crashNow else null,
            onOpenOutputs = { wantsOutputs = true },
            onCloseOutputs = { wantsOutputs = false },
        )
    }
    DsProbeTheme {
        Surface(Modifier.fillMaxSize(), color = DsColors.Background, contentColor = DsColors.Text) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (screenFor(state, wantsOutputs)) {
                    Screen.LIVE -> LiveScreen(state, session.live, locked, answers, shown, actions)
                    Screen.OUTPUTS -> OutputsScreen(state, session.live, locked, answers, shown, session.outputs, actions)
                    Screen.TOO_MANY -> TooManyScreen()
                }
            }
        }
    }
}
