package dev.dsprobe.app.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.DebugRunner
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.route.boundary
import dev.dsprobe.app.ui.theme.NumberStyle

private const val TAG = "LogScreen"

private val PREVIEW_STYLE = TextStyle(fontFamily = NumberStyle.fontFamily, fontSize = 11.sp)

/** The log as Share log would send it: one button to share, one to rebuild the preview, then the first part of the text. */
@Composable
fun LogScreen(session: PadSession, runner: DebugRunner, actions: ScreenActions) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.debug_share)
    var text by remember { mutableStateOf(session.exportLog(runner.results)) }
    Column(Modifier.fillMaxSize()) {
        TopBar(R.string.log_title, actions.onUp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton({
                boundary("share log", { Log.w(TAG, it) }) { shareLog(context, session.exportLog(runner.results), shareTitle) }
            }, Modifier.fillMaxWidth()) { OneLine(R.string.debug_share) }
            QuietButton({ text = session.exportLog(runner.results) }, Modifier.fillMaxWidth()) { OneLine(R.string.log_refresh) }
            Text(stringResource(R.string.log_preview_note, AppLimits.LOG_PREVIEW_CHARS_MAX), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text.take(AppLimits.LOG_PREVIEW_CHARS_MAX), style = PREVIEW_STYLE)
        }
    }
}
