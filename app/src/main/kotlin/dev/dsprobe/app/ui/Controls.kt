package dev.dsprobe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The smallest a tappable control may be, on each side. */
const val TOUCH_TARGET_DP = 48

private val MIN_BUTTON = Modifier.heightIn(min = TOUCH_TARGET_DP.dp)

/** The one filled button of a screen. */
@Composable
fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.then(MIN_BUTTON), enabled = enabled, content = content)
}

/** A quieter filled button for the other actions. */
@Composable
fun TonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    FilledTonalButton(onClick, modifier.then(MIN_BUTTON), enabled = enabled, content = content)
}

/** An outlined button for choices and secondary actions. */
@Composable
fun QuietButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.then(MIN_BUTTON), enabled = enabled, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), content = content)
}

/** A button label: one line, words never broken. */
@Composable
fun OneLine(textRes: Int) {
    Text(stringResource(textRes), maxLines = 1, softWrap = false)
}

/** A group of related content on a card, with a title. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** A short exclusive choice drawn as a segmented row; [selected] null means none is picked yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segmented(options: List<Pair<T, Int>>, selected: T?, enabled: Boolean, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, labelRes) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                enabled = enabled,
                modifier = MIN_BUTTON,
            ) { OneLine(labelRes) }
        }
    }
}
