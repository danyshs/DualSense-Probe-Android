package dev.dsprobe.app.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.Confirm
import dev.dsprobe.app.debug.DebugRunner
import dev.dsprobe.app.debug.Outcome
import dev.dsprobe.app.debug.Phase
import dev.dsprobe.app.debug.rawBytes
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.route.boundary
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle
import dev.dsprobe.pad.toHex

private const val TAG = "DebugScreen"
private const val CRASH_PREVIEW_CHARS = 600
private const val CRASH_PREVIEW_LINES = 8
private const val RESULTS_SHOWN_MAX = 60

@Composable
private fun CurrentCheck(runner: DebugRunner) {
    val test = runner.current
    when {
        test != null -> {
            Text(stringResource(R.string.debug_progress, runner.results.size + 1, runner.testCount, test.id), style = NumberStyle, maxLines = 1, softWrap = false)
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

/** The automatic checks (the catalogue run): they do advance by themselves, and their results go in the log. */
@Composable
private fun AutoChecks(runner: DebugRunner) {
    val running = runner.phase != Phase.IDLE && runner.phase != Phase.DONE
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.debug_auto_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Button(onClick = runner::stop) { Text(stringResource(R.string.debug_stop)) }
                OutlinedButton(onClick = runner::skip) { Text(stringResource(R.string.debug_skip)) }
            } else {
                Button(onClick = runner::start) { Text(stringResource(R.string.debug_run), maxLines = 1, softWrap = false) }
            }
        }
        runner.notice?.let { Text(it, color = DsColors.Warn) }
        CurrentCheck(runner)
        for (r in runner.results.asReversed().take(RESULTS_SHOWN_MAX)) {
            Text(stringResource(R.string.debug_result, r.outcome.name, r.id, r.detail), style = NumberStyle)
        }
    }
}

@Composable
private fun CrashRecord(record: String) {
    var shown by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { shown = !shown }) {
        Text(stringResource(if (shown) R.string.debug_crash_hide else R.string.debug_crash_show), maxLines = 1, softWrap = false)
    }
    if (shown) {
        Text(stringResource(R.string.debug_crash_record), fontWeight = FontWeight.Bold)
        Text(record.take(CRASH_PREVIEW_CHARS), style = NumberStyle, maxLines = CRASH_PREVIEW_LINES, color = DsColors.Warn)
    }
}

// one pass per frame: feed to trace, tour and runner, outputs timers, then the two values the screen watches
private suspend fun pumpFrames(session: PadSession, runner: DebugRunner, traceVersion: MutableLongState, liveRaw: MutableState<String?>) {
    val hub = session.debug
    while (true) {
        withFrameMillis {
            hub.pump(runner)
            session.outputs.tick()
            if (traceVersion.longValue != hub.trace.version) traceVersion.longValue = hub.trace.version
            val card = hub.tour.current
            val report = hub.trace.latestReport
            val raw = if (card != null && card.rawByte >= 0 && report != null) rawBytes(card, report).toHex() else null
            if (liveRaw.value != raw) liveRaw.value = raw
        }
    }
}

/** Debug Mode, one screen: checklist, outputs to try, the trace, the guided test, automatic checks. While it is open the feed has a subscriber. */
@Composable
fun DebugScreen(session: PadSession, onClose: () -> Unit) {
    val hub = session.debug
    val runner = remember(session) { session.newRunner() }
    val traceVersion = remember { mutableLongStateOf(0L) }
    val liveRaw = remember { mutableStateOf<String?>(null) }
    val routeState by session.controller.state.collectAsState()
    val shown by session.outputs.shown.collectAsState()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.debug_share)
    DisposableEffect(hub) {
        hub.open()
        onDispose { hub.close() }
    }
    LaunchedEffect(hub, runner) { pumpFrames(session, runner, traceVersion, liveRaw) }
    val guided: @Composable () -> Unit = {
        GuidedTest(hub.tour, liveRaw) {
            boundary("share log", { Log.w(TAG, it) }) { shareLog(context, session.exportLog(runner.results), shareTitle) }
        }
    }
    val busy = hub.tour.isRunning || (runner.phase != Phase.IDLE && runner.phase != Phase.DONE)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.debug_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.debug_close), maxLines = 1, softWrap = false) }
        }
        // a running test sits at the top so it is never scrolled away
        if (busy) guided()
        TryOutputPanel(canSendOutputs(routeState), shown, session.outputs)
        TraceSection(hub, traceVersion)
        if (!busy) guided()
        AutoChecks(runner)
        session.crashRecord()?.let { CrashRecord(it) }
    }
}
