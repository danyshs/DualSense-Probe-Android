package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.DsProbeTheme

@Composable
fun AppRoot(session: PadSession) {
    val state by session.controller.state.collectAsState()
    DsProbeTheme {
        Surface(Modifier.fillMaxSize(), color = DsColors.Background, contentColor = DsColors.Text) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (screenFor(state)) {
                Screen.LIVE -> LiveScreen(
                    state = state,
                    live = session.live,
                    directUsbAvailable = session.directUsbAvailable,
                    onSwitch = session.controller::switchToDirectUsb,
                    onBack = session.controller::backToAndroidMode,
                )
                Screen.TOO_MANY -> TooManyScreen()
            }
            }
        }
    }
}
