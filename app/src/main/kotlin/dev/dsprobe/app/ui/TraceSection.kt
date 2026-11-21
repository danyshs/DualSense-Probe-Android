package dev.dsprobe.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.DebugHub
import dev.dsprobe.app.debug.TRACE_GROUPS
import dev.dsprobe.app.debug.TraceRow
import dev.dsprobe.app.debug.filterByGroups
import dev.dsprobe.app.debug.rowLines
import dev.dsprobe.app.ui.theme.NumberStyle

private val HEADLINE_STYLE =
    TextStyle(fontFamily = NumberStyle.fontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
private val LOCATION_STYLE = TextStyle(fontFamily = NumberStyle.fontFamily, fontSize = 11.sp)

/**
 * The checklist that picks which groups of rows the trace shows. The choice lives in the hub, so it
 * survives leaving the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TraceChecklist(checked: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.debug_show_title), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton({
                onChange(TRACE_GROUPS.map { it.id }.toSet())
            }) { OneLine(R.string.debug_all) }
            QuietButton({ onChange(emptySet()) }) { OneLine(R.string.debug_none) }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            for (group in TRACE_GROUPS) {
                val on = group.id in checked
                Row(
                    Modifier.heightIn(min = TOUCH_TARGET_DP.dp).clickable {
                        onChange(
                            if (on) {
                                checked -
                                    group.id
                            } else {
                                checked + group.id
                            },
                        )
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, null)
                    Text(stringResource(group.labelRes), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun TraceRowView(row: TraceRow, onCopy: (String) -> Unit) {
    val lines = rowLines(row)
    Column(
        Modifier.fillMaxWidth().heightIn(min = TOUCH_TARGET_DP.dp).clickable {
            onCopy(lines.copyText().take(AppLimits.TRACE_COPY_CHARS_MAX))
        }.padding(vertical = 3.dp),
    ) {
        Text(lines.headline, style = HEADLINE_STYLE)
        Text(
            lines.location,
            style = LOCATION_STYLE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Checklist, Pause / Clear / Save, and the trace list: two lines per row, wrapping, never cut. */
@Composable
fun TraceSection(hub: DebugHub, traceVersion: LongState) {
    var checked by remember { mutableStateOf(hub.checkedGroups) }
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<TraceRow>>(emptyList()) }
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // the list is copied only when it changed; a paused list is the copy taken at the tap
    val live = remember(traceVersion.longValue) { hub.trace.rows }
    val shown = filterByGroups(if (paused) frozen else live, checked)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TraceChecklist(checked) {
            checked = it
            hub.checkedGroups = it
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton(onClick = {
                if (!paused) frozen = live
                paused = !paused
            }) {
                Text(
                    stringResource(if (paused) R.string.trace_resume else R.string.trace_pause),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            QuietButton(onClick = {
                hub.trace.clear()
            }) { Text(stringResource(R.string.trace_clear), maxLines = 1, softWrap = false) }
            QuietButton(onClick = {
                hub.save(shown)
            }) { Text(stringResource(R.string.trace_save), maxLines = 1, softWrap = false) }
        }
        if (paused) {
            Text(
                stringResource(R.string.trace_paused),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (copied) {
            Text(
                stringResource(R.string.trace_copied),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.trace_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().height(AppLimits.TRACE_LIST_HEIGHT_DP.dp)) {
            items(shown) { row ->
                TraceRowView(row) { text ->
                    context.getSystemService(
                        ClipboardManager::class.java,
                    ).setPrimaryClip(ClipData.newPlainText("trace row", text))
                    copied = true
                }
            }
        }
    }
}
