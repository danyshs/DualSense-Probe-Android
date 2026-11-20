package dev.dsprobe.app.route

import androidx.annotation.StringRes
import dev.dsprobe.app.R
import dev.dsprobe.app.pad.Transport

/** Every row of the Android mode table, split where the screen greys one part and not the other. */
enum class Feature {
    BUTTONS, MUTE_BUTTON, STICKS, TRIGGERS_ANALOG, TOUCHPAD_POINTER, TOUCHPAD_TWO_FINGERS, MOTION_SENSORS,
    RUMBLE, LIGHTBAR, PLAYER_LEDS, BATTERY, MUTE_LED, ADAPTIVE_TRIGGERS, MICROPHONE, SPEAKER, HEADPHONES, HD_HAPTICS,
}

/** A feature the route can't reach, with the one-line reason as a string resource. */
data class Reduction(val feature: Feature, @param:StringRes val reasonRes: Int)

// the Android mode table as data (measured on one phone, see FACTS P70-P80)
private val ANDROID_ALWAYS = listOf(
    Reduction(Feature.MUTE_BUTTON, R.string.reason_mute_button),
    Reduction(Feature.TOUCHPAD_TWO_FINGERS, R.string.reason_two_fingers),
    Reduction(Feature.MUTE_LED, R.string.reason_no_android_api),
    Reduction(Feature.ADAPTIVE_TRIGGERS, R.string.reason_no_android_api),
    Reduction(Feature.SPEAKER, R.string.reason_no_audio_output),
    Reduction(Feature.HEADPHONES, R.string.reason_no_audio_output),
    Reduction(Feature.HD_HAPTICS, R.string.reason_no_audio_output),
)

private val ANDROID_USB_ONLY = listOf(
    Reduction(Feature.LIGHTBAR, R.string.reason_lights_usb),
    Reduction(Feature.PLAYER_LEDS, R.string.reason_lights_usb),
    Reduction(Feature.BATTERY, R.string.reason_battery_usb),
)

private val ANDROID_BLUETOOTH_ONLY = listOf(
    Reduction(Feature.MICROPHONE, R.string.reason_mic_bluetooth),
)

/** What the route can't reach on this transport, in [Feature] order. Direct USB over Bluetooth never exists. */
fun reductions(route: Route, transport: Transport): List<Reduction> {
    require(!(route == Route.DIRECT_USB && transport == Transport.BLUETOOTH)) { "Direct USB needs a cable" }
    if (route == Route.DIRECT_USB) return emptyList()
    val perTransport = if (transport == Transport.USB) ANDROID_USB_ONLY else ANDROID_BLUETOOTH_ONLY
    val result = (ANDROID_ALWAYS + perTransport).sortedBy { it.feature.ordinal }
    check(result.map { it.feature }.toSet().size == result.size) { "a feature listed twice" }
    return result
}
