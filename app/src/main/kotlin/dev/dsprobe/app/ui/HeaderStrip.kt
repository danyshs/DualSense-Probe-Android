package dev.dsprobe.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.R
import dev.dsprobe.app.live.Battery
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.pad.PRODUCT_DUALSENSE_EDGE
import dev.dsprobe.app.pad.Transport
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.NoticeKind
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle

@StringRes
fun featureNameRes(feature: Feature): Int = when (feature) {
    Feature.BUTTONS -> R.string.feature_buttons
    Feature.MUTE_BUTTON -> R.string.feature_mute_button
    Feature.STICKS -> R.string.feature_sticks
    Feature.TRIGGERS_ANALOG -> R.string.feature_triggers_analog
    Feature.TOUCHPAD_POINTER -> R.string.feature_touchpad_pointer
    Feature.TOUCHPAD_TWO_FINGERS -> R.string.feature_touchpad_two_fingers
    Feature.MOTION_SENSORS -> R.string.feature_motion_sensors
    Feature.RUMBLE -> R.string.feature_rumble
    Feature.LIGHTBAR -> R.string.feature_lightbar
    Feature.PLAYER_LEDS -> R.string.feature_player_leds
    Feature.BATTERY -> R.string.feature_battery
    Feature.MUTE_LED -> R.string.feature_mute_led
    Feature.ADAPTIVE_TRIGGERS -> R.string.feature_adaptive_triggers
    Feature.MICROPHONE -> R.string.feature_microphone
    Feature.SPEAKER -> R.string.feature_speaker
    Feature.HEADPHONES -> R.string.feature_headphones
    Feature.HD_HAPTICS -> R.string.feature_hd_haptics
}

@StringRes
private fun noticeRes(kind: NoticeKind): Int = when (kind) {
    NoticeKind.PERMISSION_DENIED -> R.string.notice_permission_denied
    NoticeKind.PERMISSION_TIMED_OUT -> R.string.notice_permission_timed_out
    NoticeKind.NO_PERMISSION -> R.string.notice_no_permission
    NoticeKind.WRONG_LAYOUT -> R.string.notice_wrong_layout
    NoticeKind.CLAIM_FAILED -> R.string.notice_claim_failed
}

@StringRes
private fun stateMessageRes(state: RouteState): Int? = when (state) {
    is RouteState.NoPad -> R.string.state_no_pad
    is RouteState.Stranded -> R.string.state_stranded
    is RouteState.AskingUsbPermission -> R.string.state_asking
    is RouteState.Releasing -> R.string.state_releasing
    is RouteState.WaitingForAndroid -> R.string.state_waiting
    is RouteState.AndroidMode, is RouteState.DirectUsb, is RouteState.TooMany -> null
}

@Composable
private fun padName(pad: FoundPad?): String = when {
    pad == null -> stringResource(R.string.pad_none)
    pad.productId == PRODUCT_DUALSENSE_EDGE -> stringResource(R.string.pad_edge)
    else -> stringResource(R.string.pad_dualsense)
}

private const val BATTERY_WIDTH_DP = 128 // fits "Battery raw 15" in 14 sp monospace

@Composable
private fun batteryText(battery: Battery?): String = when (battery) {
    is Battery.Percent -> stringResource(R.string.battery_percent, battery.percent)
    is Battery.RawNibble -> stringResource(R.string.battery_raw, battery.nibble)
    null -> stringResource(R.string.battery_unknown)
}

@Composable
private fun ReducedList(reduced: List<Reduction>) {
    Column(Modifier.fillMaxWidth().background(DsColors.SurfaceHigh).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in reduced) {
            Column {
                Text(stringResource(featureNameRes(r.feature)), fontWeight = FontWeight.Bold)
                Text(stringResource(r.reasonRes), color = DsColors.TextDim)
            }
        }
    }
}

/** Route name, transport, battery, the "Functionality reduced" banner and the switch button. */
@Composable
fun HeaderStrip(
    state: RouteState,
    battery: Battery?,
    directUsbAvailable: Boolean,
    onSwitch: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = padOf(state)
    val reduced = reducedOf(state)
    var showReduced by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val routeName = if (routeOf(state) == Route.ANDROID) R.string.route_android else R.string.route_direct_usb
            Text(stringResource(routeName), Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (pad != null) Text(stringResource(R.string.battery_label, batteryText(battery)), Modifier.padding(start = 8.dp).width(BATTERY_WIDTH_DP.dp), style = NumberStyle, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        }
        Text(padLine(pad), Modifier.fillMaxWidth(), color = DsColors.TextDim)
        if (state is RouteState.AndroidMode) AndroidBanner(state, reduced, showReduced, directUsbAvailable, onSwitch) { showReduced = !showReduced }
        if (state is RouteState.DirectUsb) {
            Button(onClick = onBack) { Text(stringResource(R.string.back_to_android)) }
            if (state.secondPadIgnored) Text(stringResource(R.string.second_pad_ignored), color = DsColors.Warn)
        }
        stateMessageRes(state)?.let { Text(stringResource(it), Modifier.fillMaxWidth(), color = DsColors.Warn) }
    }
}

@Composable
private fun padLine(pad: FoundPad?): String {
    if (pad == null) return padName(null)
    val transport = stringResource(if (pad.transport == Transport.USB) R.string.transport_usb else R.string.transport_bluetooth)
    val line = stringResource(R.string.pad_line, padName(pad), transport)
    return if (pad.alsoPairedBluetooth) line + "  " + stringResource(R.string.also_paired) else line
}

@Composable
private fun AndroidBanner(
    state: RouteState.AndroidMode,
    reduced: List<Reduction>,
    expanded: Boolean,
    directUsbAvailable: Boolean,
    onSwitch: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.pad.transport == Transport.BLUETOOTH) {
            Text(stringResource(R.string.banner_bluetooth), Modifier.fillMaxWidth(), color = DsColors.Warn, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.banner_bluetooth_detail), Modifier.fillMaxWidth(), color = DsColors.TextDim)
        }
        if (reduced.isNotEmpty()) {
            Text(
                stringResource(R.string.banner_reduced, reduced.size),
                color = DsColors.Warn,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp),
            )
            if (expanded) ReducedList(reduced)
        }
        if (state.pad.transport == Transport.USB) {
            Button(onClick = onSwitch, enabled = directUsbAvailable) { Text(stringResource(R.string.switch_to_direct_usb)) }
            if (!directUsbAvailable) Text(stringResource(R.string.direct_usb_unbuilt), Modifier.fillMaxWidth(), color = DsColors.TextDim)
        }
        state.notice?.let { Text(stringResource(noticeRes(it.kind), it.detail), Modifier.fillMaxWidth(), color = DsColors.Warn) }
    }
}
