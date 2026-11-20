package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.Confidence
import dev.dsprobe.app.debug.EndpointCard
import dev.dsprobe.app.debug.EndpointTour
import dev.dsprobe.app.debug.RowLines
import dev.dsprobe.app.debug.Verdict
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle

private const val ROWS_SHOWN_MAX = 8
private val MONO_DIM = TextStyle(fontFamily = NumberStyle.fontFamily, fontSize = 11.sp, color = DsColors.TextDim)
private val MONO_BOLD = TextStyle(fontFamily = NumberStyle.fontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DsColors.Text)

private fun tagText(c: Confidence): Int = when (c) {
    Confidence.PROVEN -> R.string.tour_tag_proven
    Confidence.DOCUMENTED -> R.string.tour_tag_documented
    Confidence.UNKNOWN -> R.string.tour_tag_unknown
}

private fun tagColor(c: Confidence): Color = when (c) {
    Confidence.PROVEN -> DsColors.AxisY
    Confidence.DOCUMENTED -> DsColors.Warn
    Confidence.UNKNOWN -> DsColors.AxisX
}

@Composable
private fun SeenRows(seen: List<RowLines>) {
    if (seen.isEmpty()) {
        Text(stringResource(R.string.tour_nothing), color = DsColors.TextDim)
        return
    }
    Text(stringResource(R.string.tour_seen, seen.last().headline), color = DsColors.Accent, fontWeight = FontWeight.Bold)
    for (row in seen.takeLast(ROWS_SHOWN_MAX).asReversed()) {
        Column(Modifier.fillMaxWidth()) {
            Text(row.headline, style = MONO_BOLD)
            Text(row.location, style = MONO_DIM)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictRow(tour: EndpointTour, card: EndpointCard) {
    var note by remember(card.id) { mutableStateOf("") }
    val verdict = tour.verdictNow
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, labelRes) in listOf(Verdict.WORKS to R.string.tour_works, Verdict.DOESNT to R.string.tour_doesnt, Verdict.CHANGED to R.string.tour_changed)) {
            val pick = { tour.answer(v, note) }
            if (verdict == v) Button(pick) { Text(stringResource(labelRes), maxLines = 1, softWrap = false) }
            else OutlinedButton(pick) { Text(stringResource(labelRes), maxLines = 1, softWrap = false) }
        }
    }
    OutlinedTextField(note, { note = it; tour.answer(tour.verdictNow, it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tour_note)) })
}

@Composable
private fun CardBody(tour: EndpointTour, card: EndpointCard, liveRaw: State<String?>) {
    Text(stringResource(R.string.tour_progress, tour.number, tour.cards.size), style = NumberStyle, maxLines = 1, softWrap = false)
    Text(stringResource(tagText(card.confidence)), color = tagColor(card.confidence), fontWeight = FontWeight.Bold)
    Text(card.where, style = MONO_BOLD)
    Text(card.probably, fontSize = 16.sp)
    if (card.confidence == Confidence.UNKNOWN && card.rawByte >= 0) {
        val raw = liveRaw.value
        Text(if (raw == null) stringResource(R.string.tour_no_raw) else stringResource(R.string.tour_live_raw, raw), style = MONO_BOLD)
    }
    SeenRows(tour.seenNow)
    VerdictRow(tour, card)
}

/** The guided test: one endpoint card at a time. It never moves on by itself; Next and Back are the only way. */
@Composable
fun GuidedTest(tour: EndpointTour, liveRaw: State<String?>, onShare: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tour_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val card = tour.current
        if (card == null) {
            val visited = tour.answers.size
            Text(if (visited == 0) stringResource(R.string.tour_idle) else stringResource(R.string.tour_finished, visited), color = DsColors.TextDim)
        } else {
            CardBody(tour, card, liveRaw)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card == null) {
                Button(tour::start) { Text(stringResource(R.string.tour_start), maxLines = 1, softWrap = false) }
            } else {
                OutlinedButton(tour::back) { Text(stringResource(R.string.tour_back), maxLines = 1, softWrap = false) }
                Button(tour::next) { Text(stringResource(R.string.tour_next), maxLines = 1, softWrap = false) }
                OutlinedButton(tour::stop) { Text(stringResource(R.string.tour_stop), maxLines = 1, softWrap = false) }
            }
            OutlinedButton(onShare) { Text(stringResource(R.string.debug_share), maxLines = 1, softWrap = false) }
        }
    }
}
