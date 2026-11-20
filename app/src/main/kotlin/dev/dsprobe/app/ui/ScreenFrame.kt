package dev.dsprobe.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.output.maskToLeds
import dev.dsprobe.app.output.toggleLed
import dev.dsprobe.app.ui.theme.NumberStyle

/** The bar every screen but Live starts with: a Back button and the screen's name. */
@Composable
fun TopBar(@StringRes titleRes: Int, onUp: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        QuietButton(onUp) { OneLine(R.string.nav_back) }
        Text(stringResource(titleRes), Modifier.padding(start = 16.dp).weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
    }
}

/** The five player LEDs as dots in a row, lit or not, each a tap target of 48 dp that flips its LED. */
@Composable
fun PlayerLedDots(mask: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        maskToLeds(mask).forEachIndexed { index, lit ->
            val description = stringResource(if (lit) R.string.led_cd_on else R.string.led_cd_off, index + 1)
            Box(
                Modifier.size(TOUCH_TARGET_DP.dp).clip(CircleShape)
                    .background(if (lit) scheme.primary else scheme.surfaceContainerHigh)
                    .border(2.dp, if (enabled) scheme.outline else scheme.outlineVariant, CircleShape)
                    .toggleable(value = lit, enabled = enabled, role = Role.Switch, onValueChange = { onChange(toggleLed(mask, index)) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text((index + 1).toString(), style = NumberStyle, color = if (lit) scheme.onPrimary else scheme.onSurfaceVariant)
            }
        }
    }
}

/** "Did the pad react?" with three full-width answers; [picked] is the answer already given, if any. */
@Composable
fun ReactionRow(picked: Int?, onYes: () -> Unit, onNo: () -> Unit, onNotSure: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val each = Modifier.weight(1f)
        for ((labelRes, onPick) in listOf(R.string.reaction_yes to onYes, R.string.reaction_no to onNo, R.string.reaction_not_sure to onNotSure)) {
            if (picked == labelRes) TonalButton(onPick, each) { OneLine(labelRes) } else QuietButton(onPick, each) { OneLine(labelRes) }
        }
    }
}
