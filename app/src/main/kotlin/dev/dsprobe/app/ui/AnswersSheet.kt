package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.route.AndroidAnswers
import dev.dsprobe.app.route.answerRows
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle
import kotlinx.coroutines.delay

/** "What Android reports": every raw answer, asked again once a second while it is open. One line per row so nothing reflows. */
@Composable
fun AnswersSheet(answers: AndroidAnswers?, onRefresh: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val refresh by rememberUpdatedState(onRefresh)
    OutlinedButton(onClick = { open = !open }) { Text(stringResource(R.string.answers_toggle)) }
    if (!open) return
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(AppLimits.ANSWERS_POLL_INTERVAL_MS)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (answers == null) Text(stringResource(R.string.answers_none), color = DsColors.TextDim)
        for (row in answers?.let(::answerRows).orEmpty()) {
            Text(
                stringResource(R.string.answer_row, stringResource(row.labelRes), row.value),
                style = NumberStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
