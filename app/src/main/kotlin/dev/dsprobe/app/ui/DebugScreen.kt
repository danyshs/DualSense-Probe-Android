package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import dev.dsprobe.app.R
import dev.dsprobe.app.route.boundary
import dev.dsprobe.app.debug.Confirm
import dev.dsprobe.app.debug.DebugRunner
import dev.dsprobe.app.debug.Outcome
import dev.dsprobe.app.debug.Phase
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle

@Composable
private fun RunControls(runner: DebugRunner, onShare: () -> Unit) {
    val running = runner.phase != Phase.IDLE && runner.phase != Phase.DONE
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running) {
            Button(onClick = runner::stop) { Text(stringResource(R.string.debug_stop)) }
            OutlinedButton(onClick = runner::skip) { Text(stringResource(R.string.debug_skip)) }
        } else {
            Button(onClick = runner::start) { Text(stringResource(R.string.debug_run)) }
        }
    }
    if (runner.results.isNotEmpty() && !running) OutlinedButton(onClick = onShare) { Text(stringResource(R.string.debug_share)) }
}

@Composable
private fun CurrentTest(runner: DebugRunner) {
    val test = runner.current
    when {
        test != null -> {
            val number = runner.results.size + 1
            Text(stringResource(R.string.debug_progress, number, runner.testCount, test.id), style = NumberStyle, maxLines = 1, softWrap = false)
            Text(stringResource(test.promptRes, test.promptArg), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (runner.phase == Phase.WAITING_CONFIRM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { runner.confirm(Confirm.YES) }) { Text(stringResource(R.string.yes)) }
                    OutlinedButton(onClick = { runner.confirm(Confirm.NO) }) { Text(stringResource(R.string.no)) }
                    OutlinedButton(onClick = { runner.confirm(Confirm.CANT_TELL) }) { Text(stringResource(R.string.debug_cant_tell), maxLines = 1, softWrap = false) }
                }
            }
        }
        runner.phase == Phase.DONE -> {
            val passed = runner.results.count { it.outcome == Outcome.PASSED }
            Text(stringResource(R.string.debug_done, runner.results.size))
            Text(stringResource(R.string.debug_outcome_totals, passed, runner.results.size), color = DsColors.TextDim)
        }
        else -> Text(stringResource(R.string.debug_idle), color = DsColors.TextDim)
    }
}

@Composable
private fun ResultList(runner: DebugRunner) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(runner.results.asReversed()) { r ->
            val line = stringResource(R.string.debug_result, r.outcome.name, r.id, r.detail)
            Text(line, style = NumberStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TestsTab(session: PadSession, runner: DebugRunner, revision: Long) {
    val context = LocalContext.current
    val title = stringResource(R.string.debug_share)
    // the runner is plain state: a new revision is what makes this redraw
    key(revision) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RunControls(runner) {
                boundary("share log", { Log.w(TAG, it) }) { shareLog(context, session.exportLog(runner.results), title) }
            }
            CurrentTest(runner)
            session.crashRecord()?.let {
                Text(stringResource(R.string.debug_crash_record), fontWeight = FontWeight.Bold)
                Text(it.take(CRASH_PREVIEW_CHARS), style = NumberStyle, maxLines = CRASH_PREVIEW_LINES, color = DsColors.Warn)
            }
            ResultList(runner)
        }
    }
}

private const val TAG = "DebugScreen"

private const val CRASH_PREVIEW_CHARS = 600
private const val CRASH_PREVIEW_LINES = 8

/** Debug Mode: the test run with its log, and the Route trace. While it is open the feed has a subscriber; closed, nothing is published. */
@Composable
fun DebugScreen(session: PadSession, onClose: () -> Unit) {
    val hub = session.debug
    val runner = remember(session) { session.newRunner() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stamp by remember { mutableLongStateOf(0L) }
    DisposableEffect(hub) {
        hub.open()
        onDispose { hub.close() }
    }
    LaunchedEffect(hub, runner) {
        while (true) {
            withFrameMillis {
                hub.pump(runner)
                // recompose only when the runner or the trace changed
                val next = runner.revision * STAMP_RUNNER_FACTOR + hub.trace.version
                if (next != stamp) stamp = next
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.debug_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.debug_close), maxLines = 1, softWrap = false) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tabs = listOf(R.string.debug_tab_tests, R.string.debug_tab_trace)
            for ((i, label) in tabs.withIndex()) {
                if (tab == i) Button(onClick = { tab = i }) { Text(stringResource(label), maxLines = 1, softWrap = false) }
                else OutlinedButton(onClick = { tab = i }) { Text(stringResource(label), maxLines = 1, softWrap = false) }
            }
        }
        if (tab == 0) TestsTab(session, runner, stamp) else RouteTraceTab(hub, stamp)
    }
}

private const val STAMP_RUNNER_FACTOR = 1_000_003L
