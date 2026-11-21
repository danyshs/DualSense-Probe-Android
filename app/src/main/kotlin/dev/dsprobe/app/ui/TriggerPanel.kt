package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.TriggerShown
import dev.dsprobe.app.ui.theme.NumberStyle
import dev.dsprobe.pad.Limits
import kotlin.math.roundToInt

private const val MS_PER_SECOND = 1000L

/** One trigger: its live press bar next to start and force sliders, then Resist and Off. */
@Composable
private fun TriggerRow(
    isRight: Boolean,
    enabled: Boolean,
    shown: TriggerShown,
    ui: LiveUi,
    outputs: OutputController,
) {
    val value = if (isRight) ui.frame.state.r2 else ui.frame.state.l2
    val peak = if (isRight) ui.feel.r2Peak else ui.feel.l2Peak
    val label =
        stringResource(if (isRight) R.string.trigger_label_r2 else R.string.trigger_label_l2)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TriggerBar(label, value, peak)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.trigger_start, shown.start),
                maxLines = 1,
                softWrap = false,
            )
            Slider(
                shown.start.toFloat(),
                {
                    outputs.setTriggerLevels(isRight, it.roundToInt(), shown.force)
                },
                enabled = enabled,
                valueRange = 0f..AppLimits.TRIGGER_START_MAX.toFloat(),
            )
            Text(
                stringResource(R.string.trigger_force, shown.force, Limits.TRIGGER_FORCE_MAX),
                maxLines = 1,
                softWrap = false,
            )
            Slider(
                shown.force.toFloat(),
                {
                    outputs.setTriggerLevels(isRight, shown.start, it.roundToInt())
                },
                enabled = enabled,
                valueRange = 0f..Limits.TRIGGER_FORCE_MAX.toFloat(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton({
                    outputs.pressTrigger(isRight)
                }, enabled = enabled) {
                    Text(stringResource(R.string.trigger_resist), maxLines = 1, softWrap = false)
                }
                QuietButton({
                    outputs.clearTrigger(isRight)
                }, enabled = enabled) {
                    Text(stringResource(R.string.trigger_off), maxLines = 1, softWrap = false)
                }
            }
            val state =
                stringResource(
                    if (shown.on) R.string.trigger_status_on else R.string.trigger_status_off,
                )
            Text(
                stringResource(
                    R.string.trigger_status,
                    state,
                    (shown.remainingMs + MS_PER_SECOND - 1) / MS_PER_SECOND,
                ),
                style = NumberStyle,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Resistance on each trigger, the only effect mode with a proven report; the bars show the press
 * the user feels.
 */
@Composable
fun TriggerSection(
    enabled: Boolean,
    ui: LiveUi,
    r2: TriggerShown,
    l2: TriggerShown,
    outputs: OutputController,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TriggerRow(false, enabled, l2, ui, outputs)
        TriggerRow(true, enabled, r2, ui, outputs)
        Text(
            stringResource(R.string.trigger_other_modes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
