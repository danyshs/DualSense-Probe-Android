package dev.dsprobe.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.AndroidAnswers
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.RouteState

/**
 * The newest frame and its effects, advanced once per display frame for as long as the screen is
 * composed.
 */
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

/** One of the three doors on the home screen: a whole card is the tap target. */
@Composable
private fun EntryCard(titleRes: Int, bodyRes: Int, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().heightIn(
            min = TOUCH_TARGET_DP.dp,
        ).clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LiveScreen(view: RouteView, live: LiveState, actions: ScreenActions) {
    val state = view.state
    val locked = view.locked
    val shown = view.shown
    val ui = rememberLiveUi(live)
    val s = ui.frame.state
    val reasons = locked.associate { it.feature to it.reasonRes }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeaderStrip(view, s.battery, s.headphonesIn, actions)
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EntryCard(R.string.open_outputs, R.string.live_outputs_body) {
                actions.onOpen(Screen.OUTPUTS)
            }
            EntryCard(R.string.live_guided_title, R.string.live_guided_body) {
                actions.onOpen(Screen.GUIDED)
            }
            EntryCard(R.string.live_debug_title, R.string.live_debug_body) {
                actions.onOpen(Screen.DEBUG)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TriggerBar(stringResource(R.string.label_l2), s.l2, ui.feel.l2Peak)
                ControllerDrawing(ui, reasons.keys, shown, Modifier.weight(1f))
                TriggerBar(stringResource(R.string.label_r2), s.r2, ui.feel.r2Peak)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StickView(
                    stringResource(R.string.label_left_stick),
                    s.leftX,
                    s.leftY,
                    ui.feel.leftTrail,
                    ui.feel.nowMs,
                    Modifier.weight(1f),
                )
                StickView(
                    stringResource(R.string.label_right_stick),
                    s.rightX,
                    s.rightY,
                    ui.feel.rightTrail,
                    ui.feel.nowMs,
                    Modifier.weight(1f),
                )
            }
            TouchpadView(
                ui,
                reasons[Feature.TOUCHPAD_TWO_FINGERS],
                androidMode = state is RouteState.AndroidMode,
            )
            MotionTraces(ui)
        }
    }
}
