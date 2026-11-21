package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.Confirm
import dev.dsprobe.app.debug.DebugRunner
import dev.dsprobe.app.debug.Outcome
import dev.dsprobe.app.debug.Phase
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.ui.theme.NumberStyle

private const val CRASH_PREVIEW_CHARS = 600
private const val CRASH_PREVIEW_LINES = 8
private const val RESULTS_SHOWN_MAX = 60

@Composable
private fun CurrentCheck(runner: DebugRunner) {
    val test = runner.current
    when {
        test != null -> {
            Text(
                stringResource(
                    R.string.debug_progress,
                    runner.results.size + 1,
                    runner.testCount,
                    test.id,
                ),
                style = NumberStyle,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                stringResource(test.promptRes, test.promptArg),
                style = MaterialTheme.typography.titleMedium,
            )
            if (runner.phase == Phase.WAITING_CONFIRM) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val each = Modifier.weight(1f)
                    TonalButton({ runner.confirm(Confirm.YES) }, each) { OneLine(R.string.yes) }
                    QuietButton({ runner.confirm(Confirm.NO) }, each) { OneLine(R.string.no) }
                    QuietButton({
                        runner.confirm(Confirm.CANT_TELL)
                    }, each) { OneLine(R.string.debug_cant_tell) }
                }
            }
        }

        runner.phase == Phase.DONE -> {
            val passed = runner.results.count { it.outcome == Outcome.PASSED }
            Text(stringResource(R.string.debug_done, runner.results.size))
            Text(
                stringResource(R.string.debug_outcome_totals, passed, runner.results.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Text(
            stringResource(R.string.debug_idle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The automatic checks (the catalogue run): they do advance by themselves, and their results go in
 * the log.
 */
@Composable
private fun AutoChecks(runner: DebugRunner) {
    val running = runner.phase != Phase.IDLE && runner.phase != Phase.DONE
    SectionCard(stringResource(R.string.debug_auto_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                TonalButton(runner::stop) { OneLine(R.string.debug_stop) }
                QuietButton(runner::skip) { OneLine(R.string.debug_skip) }
            } else {
                TonalButton(runner::start) { OneLine(R.string.debug_run) }
            }
        }
        runner.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        CurrentCheck(runner)
        for (r in runner.results.asReversed().take(RESULTS_SHOWN_MAX)) {
            Text(
                stringResource(R.string.debug_result, r.outcome.name, r.id, r.detail),
                style = NumberStyle,
            )
        }
    }
}

@Composable
private fun CrashRecord(record: String) {
    var shown by remember { mutableStateOf(false) }
    QuietButton({
        shown = !shown
    }) { OneLine(if (shown) R.string.debug_crash_hide else R.string.debug_crash_show) }
    if (shown) {
        Text(stringResource(R.string.debug_crash_record), fontWeight = FontWeight.Bold)
        Text(
            record.take(CRASH_PREVIEW_CHARS),
            style = NumberStyle,
            maxLines = CRASH_PREVIEW_LINES,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * Debug Mode: outputs to try, the trace, the automatic checks. The guided test and the log are one
 * tap away.
 */
@Composable
fun DebugScreen(session: PadSession, runner: DebugRunner, pump: FeedPump, actions: ScreenActions) {
    val routeState by session.controller.state.collectAsState()
    val shown by session.outputs.shown.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(R.string.debug_title, actions.onUp)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton({
                    actions.onOpen(Screen.GUIDED)
                }, Modifier.weight(1f)) { OneLine(R.string.debug_open_guided) }
                QuietButton({
                    actions.onOpen(Screen.LOG)
                }, Modifier.weight(1f)) { OneLine(R.string.debug_open_log) }
            }
            SectionCard(stringResource(R.string.try_title)) {
                TryOutputPanel(canSendOutputs(routeState), shown, session.outputs)
            }
            SectionCard(stringResource(R.string.debug_trace_title)) {
                TraceSection(session.debug, pump.traceVersion)
            }
            AutoChecks(runner)
            session.crashRecord()?.let { CrashRecord(it) }
        }
    }
}
