package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.OffRow
import dev.dsprobe.app.debug.OutputKind
import dev.dsprobe.app.debug.TryChoice
import dev.dsprobe.app.debug.drivableOutputs
import dev.dsprobe.app.debug.fireChoice
import dev.dsprobe.app.debug.isFailed
import dev.dsprobe.app.debug.offRows
import dev.dsprobe.app.debug.turnEverythingOn
import dev.dsprobe.app.ui.theme.NumberStyle

private fun nameRes(kind: OutputKind): Int = when (kind) {
    OutputKind.LIGHTBAR -> R.string.try_lightbar
    OutputKind.PLAYER_LEDS -> R.string.try_player_leds
    OutputKind.MUTE_LED -> R.string.try_mute_led
    OutputKind.RUMBLE -> R.string.try_rumble
    OutputKind.TRIGGER_R2 -> R.string.output_r2
    OutputKind.TRIGGER_L2 -> R.string.output_l2
}

private fun stateRes(row: OffRow, isFailed: Boolean): Int = when {
    !row.isDrivable -> R.string.off_state_unavailable
    isFailed -> R.string.off_state_failed
    row.isOn -> R.string.off_state_on
    else -> R.string.off_state_off
}

@Composable
private fun OffTableRow(row: OffRow, isFailed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val colour = when {
        isFailed -> scheme.error
        row.isDrivable -> scheme.onSurface
        else -> scheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(nameRes(row.kind)),
            Modifier.weight(1f),
            color = colour,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (row.isDrivable) {
            Text(row.sent, Modifier.weight(1f), color = colour, style = NumberStyle)
        }
        Text(
            stringResource(stateRes(row, isFailed)),
            Modifier.weight(1f),
            color = colour,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/**
 * Turn everything on, Everything off, and one row per output: what was last sent and whether that
 * leaves it on. After Everything off a row that is still on shows as failed.
 */
@Composable
fun EverythingOffPanel(env: TryEnv, onTried: () -> Unit) {
    val canSend = canSendOutputs(env.state)
    val drivable = drivableOutputs(canSend, env.locked)
    var offPressed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            {
                offPressed = false
                turnEverythingOn(env.outputs, drivable)
                onTried()
            },
            Modifier.fillMaxWidth(),
            enabled = drivable.isNotEmpty(),
        ) { OneLine(R.string.all_on) }
        TonalButton(
            {
                fireChoice(TryChoice.ALL_OFF, env.outputs)
                offPressed = true
                onTried()
            },
            Modifier.fillMaxWidth(),
            enabled = canSend,
        ) { OneLine(R.string.try_all_off) }
        if (!canSend) ReasonLine(stringResource(R.string.tour_no_route))
        for (row in offRows(env.shown, drivable)) OffTableRow(row, isFailed(row, offPressed))
    }
}
