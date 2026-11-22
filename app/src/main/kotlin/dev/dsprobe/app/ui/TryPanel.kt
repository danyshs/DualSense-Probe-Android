package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.audio.AndroidMic
import dev.dsprobe.app.audio.AndroidTone
import dev.dsprobe.app.audio.MicFailure
import dev.dsprobe.app.audio.MicState
import dev.dsprobe.app.audio.ToneState
import dev.dsprobe.app.debug.AUDIO_STEP_REASON
import dev.dsprobe.app.debug.EndpointCard
import dev.dsprobe.app.debug.TryChoice
import dev.dsprobe.app.debug.TryIt
import dev.dsprobe.app.debug.TryKind
import dev.dsprobe.app.debug.featureOf
import dev.dsprobe.app.debug.fireChoice
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.pad.MuteLed
import kotlin.math.roundToInt

private const val PERCENT_MAX = 100
private const val MS_PER_SECOND = 1_000L

@Composable
fun ReasonLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TryItButton(enabled: Boolean, onClick: () -> Unit) {
    PrimaryButton(onClick, Modifier.fillMaxWidth(), enabled) {
        Text(
            stringResource(R.string.tour_try_it),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun muteOf(choice: TryChoice): MuteLed? = when (choice) {
    TryChoice.MUTE_OFF -> MuteLed.OFF
    TryChoice.MUTE_ON -> MuteLed.ON
    TryChoice.MUTE_PULSE -> MuteLed.PULSE
    else -> null
}

// why a control is off: Android offers nothing for it, or there is no live route
@Composable
private fun OutputReason(lock: Reduction?, env: TryEnv) {
    if (lock != null) {
        ReasonLine(stringResource(R.string.locked_reason, reasonText(lock)))
    } else if (!canSendOutputs(env.state)) {
        ReasonLine(stringResource(R.string.tour_no_route))
    }
}

/** The main area's rumble: Try it and Stop side by side, and a button to the full panel. */
@Composable
private fun RumbleTry(env: TryEnv, onTried: () -> Unit, onMore: () -> Unit) {
    val lock = env.locked.firstOrNull { it.feature == Feature.RUMBLE }
    val enabled = canSendOutputs(env.state) && lock == null
    val fire = { choice: TryChoice ->
        fireChoice(choice, env.outputs)
        onTried()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton({ fire(TryChoice.RUMBLE_BOTH) }, Modifier.weight(1f), enabled) {
            OneLine(R.string.tour_try_it)
        }
        QuietButton({ fire(TryChoice.RUMBLE_STOP) }, Modifier.weight(1f), enabled) {
            OneLine(R.string.rumble_stop)
        }
    }
    TonalButton(onMore, Modifier.fillMaxWidth()) { OneLine(R.string.rumble_more) }
    OutputReason(lock, env)
    val isRumbling = env.shown.rumbleRemainingMs > 0L
    LaunchedEffect(isRumbling) { if (isRumbling) onTried() }
}

/**
 * The Outputs screen's rumble panel with every pattern as a button that plays at once. The
 * question comes once something is rumbling. Lives in the Details sheet of the rumble card.
 */
@Composable
fun RumbleFullControls(env: TryEnv, onTried: () -> Unit) {
    val lock = env.locked.firstOrNull { it.feature == Feature.RUMBLE }
    val enabled = canSendOutputs(env.state) && lock == null
    RumbleSection(enabled, env.shown, env.outputs, playOnPick = true)
    OutputReason(lock, env)
    val isRumbling = env.shown.rumbleRemainingMs > 0L
    LaunchedEffect(isRumbling) { if (isRumbling) onTried() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutputTry(kind: TryKind, choices: List<TryChoice>, env: TryEnv, onTried: () -> Unit) {
    val lock = featureOf(kind)?.let { f -> env.locked.firstOrNull { it.feature == f } }
    val enabled = canSendOutputs(env.state) && lock == null
    val fire = { choice: TryChoice ->
        fireChoice(choice, env.outputs)
        onTried()
    }
    TryItButton(enabled) { fire(choices.first()) }
    when (kind) {
        TryKind.PLAYER_LEDS -> PlayerLedDots(env.shown.playerLeds, enabled) {
            env.outputs.setPlayerLeds(it)
            onTried()
        }

        TryKind.MUTE_LED -> Segmented(
            choices.map { it to it.labelRes },
            choices.firstOrNull {
                muteOf(it) ==
                    env.shown.muteLed
            },
            enabled,
            fire,
        )

        else -> Unit
    }
    val showChips = kind != TryKind.MUTE_LED && (choices.size > 1 || kind == TryKind.PLAYER_LEDS)
    if (showChips) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (choice in choices) {
                QuietButton({
                    fire(choice)
                }, enabled = enabled) { OneLine(choice.labelRes) }
            }
        }
    }
    OutputReason(lock, env)
}

@Composable
private fun micFailureText(why: MicFailure, code: Int): String = when (why) {
    MicFailure.PERMISSION_REFUSED -> stringResource(R.string.mic_failed_permission)
    MicFailure.NO_DEVICE -> stringResource(R.string.mic_failed_no_device)
    MicFailure.OPEN_FAILED -> stringResource(R.string.mic_failed_open)
    MicFailure.READ_ERROR -> stringResource(R.string.mic_failed_read, code)
}

@Composable
private fun MicStatus(state: MicState) {
    val meter = stringResource(R.string.mic_level_cd)
    when (state) {
        MicState.Idle -> Unit

        is MicState.Recording -> {
            val seconds = ((state.msLeft + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt()
            ReasonLine(stringResource(R.string.mic_recording, seconds))
            LinearProgressIndicator(
                { state.level },
                Modifier.fillMaxWidth().semantics {
                    contentDescription =
                        meter
                },
            )
        }

        is MicState.Done -> ReasonLine(
            stringResource(R.string.mic_done, (state.peak * PERCENT_MAX).roundToInt()),
        )

        is MicState.Failed -> ReasonLine(micFailureText(state.why, state.code))
    }
}

@Composable
private fun ToneStatus(state: ToneState) {
    val res = when (state) {
        ToneState.Idle -> return
        ToneState.Playing -> R.string.tone_playing
        ToneState.Played -> R.string.tone_played
        ToneState.NoDevice -> R.string.tone_no_device
        ToneState.Failed -> R.string.tone_failed
    }
    ReasonLine(stringResource(res))
}

@Composable
private fun AudioTry(kind: TryKind, env: TryEnv, onTried: () -> Unit) {
    if (env.state is RouteState.DirectUsb) {
        ReasonLine(AUDIO_STEP_REASON)
        return
    }
    if (env.state !is RouteState.AndroidMode) {
        ReasonLine(stringResource(R.string.tour_no_route))
        return
    }
    if (kind == TryKind.MIC) {
        TryItButton(true, env.onMicTap)
        MicStatus(env.mic.state)
        // the question is asked only once the recording really started, not when the permission was
        // refused
        LaunchedEffect(env.mic.state) { if (env.mic.state is MicState.Recording) onTried() }
        return
    }
    TryItButton(true) { env.tone.play(quiet = kind == TryKind.HEADPHONES) }
    ToneStatus(env.tone.state)
    LaunchedEffect(env.tone.state) { if (env.tone.state == ToneState.Playing) onTried() }
}

/**
 * The card's Try it: the big button and its variants, or the stated reason there is nothing to
 * fire.
 */
@Composable
fun TryPanel(card: EndpointCard, env: TryEnv, onTried: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val tryIt = card.tryIt) {
            is TryIt.Reason -> ReasonLine(tryIt.text)

            is TryIt.Action -> when (tryIt.kind) {
                TryKind.MIC, TryKind.SPEAKER, TryKind.HEADPHONES -> AudioTry(
                    tryIt.kind,
                    env,
                    onTried,
                )

                TryKind.RUMBLE -> RumbleTry(env, onTried, onMore)

                TryKind.ALL_OFF -> EverythingOffPanel(env, onTried)

                else -> OutputTry(tryIt.kind, tryIt.choices, env, onTried)
            }
        }
    }
}
