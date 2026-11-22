package dev.dsprobe.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.Connection
import dev.dsprobe.app.debug.Expectation
import dev.dsprobe.app.debug.expectationOn
import dev.dsprobe.app.debug.otherConnections
import dev.dsprobe.app.ui.theme.NumberStyle

/** Which connection is live, at the top of the guided test. */
@Composable
fun ConnectionLine(connection: Connection) {
    Text(
        stringResource(R.string.connection_header, stringResource(connection.labelRes)),
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun ExpectationText(expectation: Expectation) {
    Text(expectation.text, style = MaterialTheme.typography.bodyLarge)
    if (expectation.rows.isNotEmpty()) {
        Text(
            stringResource(R.string.expected_facts, expectation.rows.joinToString(", ")),
            style = NumberStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The main area's one line: what to expect on the live connection, nothing else. */
@Composable
fun ExpectedLine(cardId: String, connection: Connection) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.expected_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (connection == Connection.NONE) {
                stringResource(R.string.expected_no_pad)
            } else {
                expectationOn(cardId, connection).text
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = EXPECTED_LINES_MAX,
        )
    }
}

private const val EXPECTED_LINES_MAX = 3

/**
 * What to expect on the live connection for this card, and a collapsed line with the other two.
 * Nothing here sends or reads anything.
 */
@Composable
fun ExpectedHere(cardId: String, connection: Connection) {
    var othersOpen by remember(cardId) { mutableStateOf(false) }
    val toggleDescription = stringResource(R.string.expected_other_cd)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.expected_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (connection == Connection.NONE) {
                Text(stringResource(R.string.expected_no_pad))
            } else {
                ExpectationText(expectationOn(cardId, connection))
            }
            Text(
                stringResource(R.string.expected_other),
                Modifier.fillMaxWidth().heightIn(min = TOUCH_TARGET_DP.dp)
                    .clickable { othersOpen = !othersOpen }
                    .semantics { contentDescription = toggleDescription }
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (othersOpen) {
                for (other in otherConnections(connection)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(other.labelRes),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        ExpectationText(expectationOn(cardId, other))
                    }
                }
            }
        }
    }
}
