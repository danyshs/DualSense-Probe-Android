package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.AndroidAnswers
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.ui.theme.DsColors
import androidx.compose.runtime.withFrameMillis

/** The newest frame and its effects, advanced once per display frame for as long as the screen is composed. */
@Composable
fun rememberLiveUi(live: LiveState): LiveUi {
    val ui by produceState(LiveUi.EMPTY, live) {
        var feel = FeelFrame.EMPTY
        while (true) {
            withFrameMillis { nowMs ->
                val frame = live.takeFrame()
                feel = advance(feel, frame, nowMs)
                value = LiveUi(frame, feel)
            }
        }
    }
    return ui
}

@Composable
fun LiveScreen(
    state: RouteState,
    live: LiveState,
    locked: List<Reduction>,
    answers: AndroidAnswers?,
    shown: OutputsShown,
    actions: ScreenActions,
) {
    val ui = rememberLiveUi(live)
    val s = ui.frame.state
    val reasons = locked.associate { it.feature to it.reasonRes }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderStrip(state, s.battery, s.headphonesIn, locked, answers, actions.onRefreshAnswers, actions.onSwitch, actions.onBack, actions.onCrashNow)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onOpenOutputs) { Text(stringResource(R.string.open_outputs)) }
                Button(onClick = actions.onOpenDebug) { Text(stringResource(R.string.open_debug)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TriggerBar(stringResource(R.string.label_l2), s.l2, ui.feel.l2Peak)
                ControllerDrawing(ui, reasons.keys, shown, Modifier.weight(1f))
                TriggerBar(stringResource(R.string.label_r2), s.r2, ui.feel.r2Peak)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StickView(stringResource(R.string.label_left_stick), s.leftX, s.leftY, ui.feel.leftTrail, ui.feel.nowMs, Modifier.weight(1f))
                StickView(stringResource(R.string.label_right_stick), s.rightX, s.rightY, ui.feel.rightTrail, ui.feel.nowMs, Modifier.weight(1f))
            }
            TouchpadView(ui, reasons[Feature.TOUCHPAD_TWO_FINGERS], androidMode = state is RouteState.AndroidMode)
            MotionTraces(ui)
        }
    }
}
