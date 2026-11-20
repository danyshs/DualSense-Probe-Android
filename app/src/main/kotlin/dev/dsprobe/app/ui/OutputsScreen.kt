package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameMillis
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.AndroidAnswers
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed

@Composable
private fun Section(titleRes: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(titleRes), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun LockNote(reduction: Reduction) {
    val reason = stringResource(reduction.reasonRes, *reduction.reasonArgs.toTypedArray())
    Text(stringResource(R.string.locked_reason, reason), color = DsColors.TextDim)
}

@Composable
private fun answerText(answer: ApiAnswer): String = when (answer) {
    ApiAnswer.Ok -> stringResource(R.string.answer_accepted)
    is ApiAnswer.Failed -> stringResource(R.string.answer_refused, answer.reason)
}

/** Android's answer to the last request, and the question it cannot answer itself. */
@Composable
private fun AnswerCard(shown: OutputsShown, route: Route, onChanged: (Boolean) -> Unit) {
    val last = shown.lastAnswer
    // Direct USB's effect is on the pad itself; only a refusal needs saying
    if (last != null && (route == Route.ANDROID || last is ApiAnswer.Failed)) {
        Text(answerText(last), color = if (last is ApiAnswer.Failed) DsColors.Warn else DsColors.TextDim, maxLines = 2)
    }
    val ask = shown.ask ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ask_changed, ask.sent), fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onChanged(true) }) { Text(stringResource(R.string.yes)) }
            OutlinedButton(onClick = { onChanged(false) }) { Text(stringResource(R.string.no)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerLedSection(enabled: Boolean, mask: Int, outputs: OutputController) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (i in 0 until AppLimits.PLAYER_LED_COUNT) {
            val bit = 1 shl i
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text((i + 1).toString(), style = NumberStyle)
                Switch(mask and bit != 0, { outputs.setPlayerLeds(mask xor bit) }, enabled = enabled)
            }
        }
    }
    // buttons keep their natural width and the row wraps, so no label ever breaks
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LedPreset(R.string.leds_off, enabled) { outputs.setPlayerLeds(0) }
        LedPreset(R.string.leds_all, enabled) { outputs.setPlayerLeds(Limits.PLAYER_LED_MASK_MAX) }
        for (count in 1 until AppLimits.PLAYER_LED_COUNT) {
            OutlinedButton({ outputs.setPlayerLeds((1 shl count) - 1) }, enabled = enabled) { Text(count.toString(), maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun LedPreset(labelRes: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled) { Text(stringResource(labelRes), maxLines = 1, softWrap = false) }
}

@Composable
private fun MuteLedSection(enabled: Boolean, current: MuteLed, outputs: OutputController) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((mode, labelRes) in listOf(MuteLed.OFF to R.string.mute_off, MuteLed.ON to R.string.mute_on, MuteLed.PULSE to R.string.mute_pulse)) {
            val pick = { outputs.setMuteLed(mode) }
            val each = Modifier.weight(1f)
            if (mode == current) Button(pick, each, enabled = enabled) { Text(stringResource(labelRes)) }
            else OutlinedButton(pick, each, enabled = enabled) { Text(stringResource(labelRes)) }
        }
    }
}

/** Every output the route can drive; a control Android offers nothing for stays visible, disabled, with its reason. */
@Composable
fun OutputsScreen(
    state: RouteState,
    live: LiveState,
    locked: List<Reduction>,
    answers: AndroidAnswers?,
    shown: OutputsShown,
    outputs: OutputController,
    actions: ScreenActions,
) {
    val ui = rememberLiveUi(live)
    LaunchedEffect(outputs) {
        while (true) withFrameMillis { outputs.tick() }
    }
    val canSend = canSendOutputs(state)
    val reasons = locked.associate { it.feature to it }
    fun usable(f: Feature) = canSend && f !in reasons
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderStrip(state, ui.frame.state.battery, ui.frame.state.headphonesIn, locked, answers, actions.onRefreshAnswers, actions.onSwitch, actions.onBack, actions.onCrashNow)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = actions.onCloseOutputs) { Text(stringResource(R.string.back_to_live)) }
            ControllerDrawing(ui, reasons.keys, shown)
            AnswerCard(shown, routeOf(state), outputs::answerChanged)
            Section(R.string.section_lightbar) {
                reasons[Feature.LIGHTBAR]?.let { LockNote(it) }
                ColourPicker(usable(Feature.LIGHTBAR), shown.lightbar, outputs::setLightbar, outputs::flushLightbar)
            }
            Section(R.string.section_rumble) {
                reasons[Feature.RUMBLE]?.let { LockNote(it) }
                RumbleSection(usable(Feature.RUMBLE), shown, outputs)
            }
            Section(R.string.section_player_leds) {
                reasons[Feature.PLAYER_LEDS]?.let { LockNote(it) }
                PlayerLedSection(usable(Feature.PLAYER_LEDS), shown.playerLeds, outputs)
            }
            Section(R.string.section_mute_led) {
                reasons[Feature.MUTE_LED]?.let { LockNote(it) }
                MuteLedSection(usable(Feature.MUTE_LED), shown.muteLed, outputs)
            }
            Section(R.string.section_triggers) {
                reasons[Feature.ADAPTIVE_TRIGGERS]?.let { LockNote(it) }
                TriggerSection(usable(Feature.ADAPTIVE_TRIGGERS), ui, shown.r2, shown.l2, outputs)
            }
        }
    }
}
