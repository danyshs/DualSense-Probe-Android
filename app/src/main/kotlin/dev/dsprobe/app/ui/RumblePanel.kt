package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.output.PatternId
import dev.dsprobe.app.ui.theme.NumberStyle
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.RumbleMode
import kotlin.math.roundToInt

private const val MS_PER_SECOND = 1000f
private const val DURATION_S_MIN = 1f
private const val DURATION_S_MAX = 5f
private const val DURATION_SLIDER_STEPS = 3 // 1, 2, 3, 4, 5 seconds

private fun patternLabelRes(id: PatternId): Int = when (id) {
    PatternId.CONSTANT -> R.string.pattern_constant
    PatternId.PULSE -> R.string.pattern_pulse
    PatternId.RAMP_UP -> R.string.pattern_ramp_up
    PatternId.RAMP_DOWN -> R.string.pattern_ramp_down
    PatternId.HEARTBEAT -> R.string.pattern_heartbeat
    PatternId.ALTERNATE -> R.string.pattern_alternate
    PatternId.IMPACT -> R.string.pattern_impact
    PatternId.ENGINE -> R.string.pattern_engine
}

/** A button that is filled while [selected]; its text is one line, words never broken. */
@Composable
private fun PickButton(labelRes: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = @Composable { Text(stringResource(labelRes), maxLines = 1, softWrap = false) }
    if (selected) {
        TonalButton(onClick, enabled = enabled) {
            label()
        }
    } else {
        QuietButton(onClick, enabled = enabled) { label() }
    }
}

@Composable
private fun ModePicker(enabled: Boolean, current: RumbleMode?, outputs: OutputController) {
    // only a route that has modes (Direct USB) reports one
    if (current == null) return
    Text(stringResource(R.string.rumble_mode_label))
    Segmented(
        listOf(
            RumbleMode.COMPATIBLE to R.string.rumble_mode_compatible,
            RumbleMode.V2 to R.string.rumble_mode_v2,
        ),
        current,
        enabled,
        outputs::setRumbleMode,
    )
}

/** What the user has set on the panel; lives only while the Outputs screen is open. */
private class RumbleInputs {
    var left by mutableFloatStateOf(AppLimits.RUMBLE_INTENSITY_DEFAULT.toFloat())
    var right by mutableFloatStateOf(AppLimits.RUMBLE_INTENSITY_DEFAULT.toFloat())
    var linked by mutableStateOf(true)
    var pattern by mutableStateOf(PatternId.CONSTANT)
    var rateHz by mutableFloatStateOf(AppLimits.RUMBLE_PULSE_RATE_HZ_DEFAULT.toFloat())
    var durationS by mutableFloatStateOf(DURATION_S_MAX)

    fun moveLeft(value: Float) {
        left = value
        if (linked) right = value
    }

    fun moveRight(value: Float) {
        right = value
        if (linked) left = value
    }
}

@Composable
private fun Sliders(enabled: Boolean, inputs: RumbleInputs, outputs: OutputController) {
    val live = { outputs.setRumble(inputs.left.roundToInt(), inputs.right.roundToInt()) }
    val range = 0f..Limits.MOTOR_STRENGTH_MAX.toFloat()
    // dragging rumbles at once; letting go stops it but keeps the value as the pattern ceiling
    val release = { outputs.setRumble(0, 0) }
    Text(stringResource(R.string.label_rumble_left))
    Slider(inputs.left, {
        inputs.moveLeft(it)
        live()
    }, enabled = enabled, valueRange = range, onValueChangeFinished = release)
    Text(stringResource(R.string.label_rumble_right))
    Slider(inputs.right, {
        inputs.moveRight(it)
        live()
    }, enabled = enabled, valueRange = range, onValueChangeFinished = release)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Switch(inputs.linked, {
            inputs.linked = it
            if (it) inputs.moveRight(inputs.left)
        }, enabled = enabled)
        Text(stringResource(R.string.rumble_link), maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Patterns(enabled: Boolean, inputs: RumbleInputs, outputs: OutputController) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (id in PatternId.entries) {
            PickButton(patternLabelRes(id), inputs.pattern == id, enabled) {
                inputs.pattern =
                    id
            }
        }
    }
    val rateHz = inputs.rateHz.roundToInt()
    Text(stringResource(R.string.rumble_pulse_rate, rateHz), maxLines = 1, softWrap = false)
    Slider(
        inputs.rateHz,
        { inputs.rateHz = it },
        enabled =
        enabled && inputs.pattern == PatternId.PULSE,
        valueRange =
        AppLimits.RUMBLE_PULSE_RATE_HZ_MIN.toFloat()..AppLimits.RUMBLE_PULSE_RATE_HZ_MAX.toFloat(),
        steps = AppLimits.RUMBLE_PULSE_RATE_HZ_MAX - AppLimits.RUMBLE_PULSE_RATE_HZ_MIN - 1,
    )
    Text(
        stringResource(R.string.rumble_duration, inputs.durationS.roundToInt()),
        maxLines = 1,
        softWrap = false,
    )
    Slider(
        inputs.durationS,
        { inputs.durationS = it },
        enabled = enabled,
        valueRange =
        DURATION_S_MIN..DURATION_S_MAX,
        steps = DURATION_SLIDER_STEPS,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val play = {
            outputs.playPattern(
                inputs.pattern,
                inputs.left.roundToInt(),
                inputs.right.roundToInt(),
                (
                    inputs.durationS *
                        MS_PER_SECOND
                    ).roundToInt(),
                rateHz,
            )
        }
        TonalButton(play, enabled = enabled) {
            Text(stringResource(R.string.rumble_play), maxLines = 1, softWrap = false)
        }
        QuietButton({
            outputs.setRumble(0, 0)
        }, enabled = enabled) {
            Text(stringResource(R.string.rumble_stop), maxLines = 1, softWrap = false)
        }
    }
}

/** Direct USB mode picker, per-motor intensity, the patterns, and a strip of what was sent. */
@Composable
fun RumbleSection(enabled: Boolean, shown: OutputsShown, outputs: OutputController) {
    val inputs = remember { RumbleInputs() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModePicker(enabled, shown.rumbleMode, outputs)
        Sliders(enabled, inputs, outputs)
        Patterns(enabled, inputs, outputs)
        Text(
            stringResource(
                R.string.rumble_status,
                shown.rumbleLeft,
                shown.rumbleRight,
                shown.rumbleRemainingMs / MS_PER_SECOND,
            ),
            style = NumberStyle,
            maxLines = 1,
            softWrap = false,
        )
        RumbleTraces(shown.rumbleTrace)
        Text(
            stringResource(R.string.rumble_hd_haptics),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
