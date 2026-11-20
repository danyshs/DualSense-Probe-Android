package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import dev.dsprobe.app.R
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.AndroidAnswers
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed

@Composable
private fun Section(titleRes: Int, content: @Composable () -> Unit) {
    SectionCard(stringResource(titleRes)) { content() }
}

@Composable
private fun LockNote(reduction: Reduction) {
    val reason = stringResource(reduction.reasonRes, *reduction.reasonArgs.toTypedArray())
    Text(stringResource(R.string.locked_reason, reason), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerLedSection(enabled: Boolean, mask: Int, outputs: OutputController) {
    PlayerLedDots(mask, enabled, outputs::setPlayerLeds)
    // buttons keep their natural width and the row wraps, so no label ever breaks
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QuietButton({ outputs.setPlayerLeds(0) }, enabled = enabled) { OneLine(R.string.leds_off) }
        QuietButton({ outputs.setPlayerLeds(Limits.PLAYER_LED_MASK_MAX) }, enabled = enabled) { OneLine(R.string.leds_all) }
    }
}

private val MUTE_MODES = listOf(MuteLed.OFF to R.string.mute_off, MuteLed.ON to R.string.mute_on, MuteLed.PULSE to R.string.mute_pulse)

@Composable
private fun MuteLedSection(enabled: Boolean, current: MuteLed, outputs: OutputController) {
    Segmented(MUTE_MODES, current, enabled, outputs::setMuteLed)
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
        TopBar(R.string.open_outputs, actions.onUp)
        HeaderStrip(state, ui.frame.state.battery, ui.frame.state.headphonesIn, locked, answers, actions.onRefreshAnswers, actions.onSwitch, actions.onBack, actions.onCrashNow)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ControllerDrawing(ui, reasons.keys, shown)
            AnswerCard(shown, routeOf(state) == Route.ANDROID, outputs)
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
