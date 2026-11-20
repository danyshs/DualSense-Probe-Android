package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.output.PatternId
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb

private class Swatch(val labelRes: Int, val rgb: Rgb)

private val SWATCHES = listOf(
    Swatch(R.string.try_red, Rgb(255u, 0u, 0u)),
    Swatch(R.string.try_green, Rgb(0u, 255u, 0u)),
    Swatch(R.string.try_blue, Rgb(0u, 0u, 255u)),
    Swatch(R.string.try_white, Rgb(255u, 255u, 255u)),
    Swatch(R.string.leds_off, Rgb(0u, 0u, 0u)),
)

private val MUTE_MODES = listOf(MuteLed.OFF to R.string.mute_off, MuteLed.ON to R.string.mute_on, MuteLed.PULSE to R.string.mute_pulse)

@Composable
private fun Chip(labelRes: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled) { Text(stringResource(labelRes), maxLines = 1, softWrap = false) }
}

@Composable
private fun Label(labelRes: Int) {
    Text(stringResource(labelRes), fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightbarRow(enabled: Boolean, shown: OutputsShown, outputs: OutputController) {
    var custom by remember { mutableStateOf(false) }
    Label(R.string.try_lightbar)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (swatch in SWATCHES) {
            Chip(swatch.labelRes, enabled) {
                outputs.setLightbar(swatch.rgb)
                outputs.flushLightbar()
            }
        }
        Chip(R.string.try_custom, enabled) { custom = !custom }
    }
    if (custom) ColourPicker(enabled, shown.lightbar, outputs::setLightbar, outputs::flushLightbar)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerLedRow(enabled: Boolean, mask: Int, outputs: OutputController) {
    Label(R.string.try_player_leds)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until AppLimits.PLAYER_LED_COUNT) {
            val bit = 1 shl i
            val toggle = { outputs.setPlayerLeds(mask xor bit) }
            if (mask and bit != 0) Button(toggle, enabled = enabled) { Text((i + 1).toString()) }
            else OutlinedButton(toggle, enabled = enabled) { Text((i + 1).toString()) }
        }
        Chip(R.string.leds_off, enabled) { outputs.setPlayerLeds(0) }
        Chip(R.string.leds_all, enabled) { outputs.setPlayerLeds(Limits.PLAYER_LED_MASK_MAX) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuteRumbleRows(enabled: Boolean, current: MuteLed, outputs: OutputController) {
    Label(R.string.try_mute_led)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((mode, labelRes) in MUTE_MODES) {
            val pick = { outputs.setMuteLed(mode) }
            if (mode == current) Button(pick, enabled = enabled) { Text(stringResource(labelRes)) }
            else OutlinedButton(pick, enabled = enabled) { Text(stringResource(labelRes)) }
        }
    }
    Label(R.string.try_rumble)
    val strength = AppLimits.RUMBLE_INTENSITY_DEFAULT
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(R.string.try_left, enabled) { outputs.setRumble(strength, 0) }
        Chip(R.string.try_right, enabled) { outputs.setRumble(0, strength) }
        Chip(R.string.try_both, enabled) { outputs.setRumble(strength, strength) }
        Chip(R.string.try_pattern, enabled) {
            outputs.playPattern(PatternId.HEARTBEAT, strength, strength, AppLimits.RUMBLE_PATTERN_DURATION_MS_MIN, AppLimits.RUMBLE_PULSE_RATE_HZ_DEFAULT)
        }
        Chip(R.string.try_stop, enabled) { outputs.setRumble(0, 0) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerRow(enabled: Boolean, outputs: OutputController) {
    Label(R.string.try_triggers)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(R.string.try_r2, enabled) { outputs.pressTrigger(true) }
        Chip(R.string.try_l2, enabled) { outputs.pressTrigger(false) }
        Chip(R.string.try_triggers_off, enabled) {
            outputs.clearTrigger(true)
            outputs.clearTrigger(false)
        }
    }
}

/** Fires outputs from Debug Mode so their trace rows can be watched; the same OutputController calls as the Outputs screen. */
@Composable
fun TryOutputPanel(canSend: Boolean, shown: OutputsShown, outputs: OutputController) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.try_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LightbarRow(canSend, shown, outputs)
        PlayerLedRow(canSend, shown.playerLeds, outputs)
        MuteRumbleRows(canSend, shown.muteLed, outputs)
        TriggerRow(canSend, outputs)
        val last = shown.lastAnswer
        if (last is ApiAnswer.Failed) Text(stringResource(R.string.answer_refused, last.reason), color = DsColors.Warn)
        shown.ask?.let { ask ->
            Text(stringResource(R.string.ask_changed, ask.sent), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ outputs.answerChanged(true) }) { Text(stringResource(R.string.yes)) }
                OutlinedButton({ outputs.answerChanged(false) }) { Text(stringResource(R.string.no)) }
            }
        }
    }
}
