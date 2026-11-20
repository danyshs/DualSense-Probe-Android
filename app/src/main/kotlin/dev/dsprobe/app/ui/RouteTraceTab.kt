package dev.dsprobe.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.DebugHub
import dev.dsprobe.app.debug.TraceRow
import dev.dsprobe.app.debug.followRows
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle

// the controls a developer can follow; a row follows a control when its ID is this or starts with it
private val FOLLOW_CONTROLS = listOf(
    "in.button", "in.dpad", "in.stick.left", "in.stick.right", "in.trigger", "in.touch", "in.gyro", "in.accel", "in.key",
    "out.lightbar", "out.led", "out.rumble", "out.trigger",
)

// wide enough for the longest row at the row font, so a row is never wrapped or cut
private const val ROW_WIDTH_DP = 2400
private val ROW_STYLE = TextStyle(fontFamily = NumberStyle.fontFamily, fontSize = 12.sp, color = DsColors.Text)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowChips(follow: String?, onFollow: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (id in FOLLOW_CONTROLS) {
            val label: @Composable () -> Unit = { Text(id, maxLines = 1, softWrap = false) }
            if (follow == id) Button(onClick = { onFollow(null) }, content = { label() })
            else OutlinedButton(onClick = { onFollow(id) }, content = { label() })
        }
    }
}

/** Debug Mode's second tab: one row per input change and output send, newest on top. */
@Composable
fun RouteTraceTab(hub: DebugHub, traceVersion: Long) {
    var follow by remember { mutableStateOf<String?>(null) }
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<TraceRow>>(emptyList()) }
    // the list is copied only when it changed; a paused list is the copy taken at the tap
    val live = remember(traceVersion) { hub.trace.rows }
    val shown = followRows(if (paused) frozen else live, follow)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(follow?.let { stringResource(R.string.trace_following, it) } ?: stringResource(R.string.trace_hint), color = DsColors.TextDim)
        FollowChips(follow) { follow = if (it == follow) null else it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (!paused) frozen = live
                paused = !paused
            }) { Text(stringResource(if (paused) R.string.trace_resume else R.string.trace_pause)) }
            OutlinedButton(onClick = { hub.save(shown) }) { Text(stringResource(R.string.trace_save)) }
        }
        if (paused) Text(stringResource(R.string.trace_paused), color = DsColors.Warn)
        if (shown.isEmpty()) Text(stringResource(R.string.trace_empty), color = DsColors.TextDim)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            LazyColumn(Modifier.width(ROW_WIDTH_DP.dp)) {
                items(shown) { row -> Text(row.text, style = ROW_STYLE, maxLines = 1, softWrap = false) }
            }
        }
    }
}
