package dev.dsprobe.app.route

import android.hardware.Sensor
import android.view.InputDevice
import android.view.MotionEvent
import android.media.AudioDeviceInfo
import androidx.annotation.StringRes
import dev.dsprobe.app.R
import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.Button

/** Every row of the Android mode table, split where the screen greys one part and not the other. */
enum class Feature {
    BUTTONS, MUTE_BUTTON, STICKS, TRIGGERS_ANALOG, TOUCHPAD_POINTER, TOUCHPAD_TWO_FINGERS, MOTION_SENSORS,
    RUMBLE, LIGHTBAR, PLAYER_LEDS, BATTERY, MUTE_LED, ADAPTIVE_TRIGGERS, MICROPHONE, SPEAKER, HEADPHONES, HD_HAPTICS,
}

/** A feature Android offers nothing for right now: the one-line reason as a string resource, with its integer arguments. */
data class Reduction(val feature: Feature, @param:StringRes val reasonRes: Int, val reasonArgs: List<Int> = emptyList())

/** A feature with no known API but a method whose name matches: unlocked for inspection, never called blindly. */
data class MayOffer(val feature: Feature, val method: String)

/** The probe word each feature without a known API is searched for. */
private val PROBE_FEATURES: Map<Feature, String> = mapOf(
    Feature.ADAPTIVE_TRIGGERS to "trigger",
    Feature.MUTE_BUTTON to "mute",
    Feature.MUTE_LED to "mute",
    Feature.HD_HAPTICS to "haptic",
)

/** The features Android has a method name for and no documented use of: the first matching method each. */
fun mayOffer(answers: AndroidAnswers): List<MayOffer> {
    val probe = answers.probe ?: return emptyList()
    return PROBE_FEATURES.mapNotNull { (feature, keyword) -> probe.matchesFor(keyword).firstOrNull()?.let { MayOffer(feature, it.method) } }
        .sortedBy { it.feature.ordinal }
}

/**
 * A key event that arrives while the Mute prompt is up, and maps to no pad button, is the Mute key:
 * Android's answers then offer it. Any other key, or any key outside the prompt, changes nothing.
 */
fun learnMuteKey(answers: AndroidAnswers, muteWatched: Boolean, keyCode: Int): AndroidAnswers {
    if (!muteWatched || answers.muteKeyCode != null || buttonForKeyCode(keyCode) != null) return answers
    return answers.copy(muteKeyCode = keyCode)
}

/** The light Android calls the lightbar, the part of the five player LED names, and the guess for a mute light. */
const val LIGHTBAR_NAME = "indicator"
const val PLAYER_LIGHT_NAME_PART = "player-"
const val MUTE_LIGHT_NAME_PART = "mute"

private val GYRO_OR_ACCEL = setOf(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER)
private val STICK_AXES = setOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
private val TRIGGER_AXES = setOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER)
private const val TOUCHPAD_FINGERS_MIN = 2

private fun hasSource(sources: Int, source: Int) = sources and source == source

private fun playerLightCount(lights: List<LightAnswer>): Int =
    (1..AppLimits.PLAYER_LED_COUNT).count { n -> lights.any { it.name.endsWith(PLAYER_LIGHT_NAME_PART + n) } }

/**
 * True for each feature Android's answers offer something for. A feature is never decided by the
 * transport: it is offered when Android lists the key, axis, sensor, vibrator, light or battery for it.
 */
private fun offered(a: AndroidAnswers): Map<Feature, Boolean> {
    val keys = a.keys.orEmpty()
    val lights = a.lights.orEmpty()
    return mapOf(
        Feature.BUTTONS to keys.any { it.present },
        // no Android key code is known for Mute: it unlocks only when a key mapped to it is offered
        Feature.MUTE_BUTTON to (keys.any { it.present && it.button == Button.MUTE } || a.muteKeyCode != null),
        Feature.STICKS to a.axes.orEmpty().any { it.axis in STICK_AXES },
        Feature.TRIGGERS_ANALOG to a.axes.orEmpty().any { it.axis in TRIGGER_AXES },
        Feature.TOUCHPAD_POINTER to (hasSource(a.sources, InputDevice.SOURCE_MOUSE) || hasSource(a.sources, InputDevice.SOURCE_TOUCHPAD)),
        // Android has no query for multi-touch: it is offered once a second finger has been seen
        Feature.TOUCHPAD_TWO_FINGERS to (a.touchPointersSeen >= TOUCHPAD_FINGERS_MIN),
        Feature.MOTION_SENSORS to a.sensors.orEmpty().any { it.type in GYRO_OR_ACCEL },
        Feature.RUMBLE to a.vibratorIds.orEmpty().isNotEmpty(),
        Feature.LIGHTBAR to lights.any { it.name == LIGHTBAR_NAME },
        Feature.PLAYER_LEDS to (playerLightCount(lights) == AppLimits.PLAYER_LED_COUNT),
        // Android's raw battery answer is shown even when it reads as unknown: only a failed question locks it
        Feature.BATTERY to (a.battery != null),
        Feature.MUTE_LED to lights.any { it.name.contains(MUTE_LIGHT_NAME_PART, ignoreCase = true) },
        // no documented call for trigger effects: only a matching method name (see mayOffer) can unlock them
        Feature.ADAPTIVE_TRIGGERS to false,
        Feature.MICROPHONE to a.audioInputs.orEmpty().any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET },
        Feature.SPEAKER to a.audioOutputs.orEmpty().any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET },
        Feature.HEADPHONES to a.audioOutputs.orEmpty().any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET },
        Feature.HD_HAPTICS to a.audioOutputs.orEmpty().any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET },
    )
}

private fun reductionFor(feature: Feature, a: AndroidAnswers): Reduction {
    val probe = a.probe
    if (probe == null || feature !in PROBE_FEATURES) return Reduction(feature, R.string.reason_android_offers_nothing)
    return Reduction(feature, R.string.reason_android_offers_nothing_probed, listOf(probe.methodCount, probe.apiLevel))
}

/** What Android offers nothing for, in [Feature] order. Direct USB has no such list: nothing is asked of Android there. */
fun reductions(answers: AndroidAnswers): List<Reduction> {
    val offered = offered(answers)
    check(offered.keys.size == Feature.entries.size) { "a feature has no rule" }
    val unlocked = mayOffer(answers).map { it.feature }.toSet()
    val result = Feature.entries.filter { offered.getValue(it).not() && it !in unlocked }.map { reductionFor(it, answers) }
    check(result.size <= Feature.entries.size) { "more locks than features" }
    return result
}
