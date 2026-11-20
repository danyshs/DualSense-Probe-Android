package dev.dsprobe.app.route

import android.content.Context
import android.hardware.BatteryState
import android.hardware.Sensor
import android.hardware.input.InputManager
import android.hardware.lights.Light
import android.hardware.lights.LightsManager
import android.media.AudioManager
import android.os.Build
import android.os.VibratorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.dsprobe.app.AppLimits

private val PAD_KEY_CODES = intArrayOf(
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
    KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_MODE,
)

/** Every key code Android defines, 0 up to its own bound; the bound moves each release, so it is read at run time. */
@Suppress("DEPRECATION") // the SDK's own last key code is deprecated because it moves; asking it is the point of the probe
private fun lastKeyCode(): Int = KeyEvent.getMaxKeyCode()

private fun keyAnswers(device: InputDevice): List<KeyAnswer> {
    val last = minOf(lastKeyCode(), AppLimits.KEY_CODES_PROBED_MAX - 1)
    val codes = IntArray(last + 1) { it }
    val has = device.hasKeys(*codes)
    check(has.size == codes.size) { "hasKeys answered ${has.size} for ${codes.size} codes" }
    // the codes the pad has, plus the mapped ones whatever Android says, so an absent mapped key still shows
    return codes.indices.filter { has[it] || codes[it] in PAD_KEY_CODES }
        .map { KeyAnswer(codes[it], KeyEvent.keyCodeToString(codes[it]), buttonForKeyCode(codes[it]), has[it]) }
}

private fun audioAnswers(context: Context, flag: Int): List<AudioAnswer> {
    val audio = checkNotNull(context.getSystemService(AudioManager::class.java)) { "no AudioManager" }
    return audio.getDevices(flag).map { AudioAnswer(it.type, it.channelCounts.toList(), it.channelIndexMasks.toList()) }
}

private val PROBED_CLASSES: List<Class<*>> = listOf(
    InputDevice::class.java, InputManager::class.java, VibratorManager::class.java,
    LightsManager::class.java, Light::class.java, BatteryState::class.java,
)

/** The public method names of Android's controller classes. Only names are read; no method is ever invoked. */
private fun probeApi(): ApiProbe = ApiProbe(
    Build.VERSION.SDK_INT,
    PROBED_CLASSES.map { c -> ClassMethods(c.simpleName, c.methods.map { it.name }.distinct().sorted().take(AppLimits.REFLECTED_METHODS_PER_CLASS_MAX)) },
)

/**
 * Asks Android what it offers for this pad right now. Every question runs inside [askOrNull]: one
 * that throws is logged and recorded in `failed`, and the rest still get asked. Main thread.
 */
fun readAnswers(context: Context, device: InputDevice, touchPointersSeen: Int, log: (String) -> Unit): AndroidAnswers {
    val failed = ArrayList<String>()
    fun <T : Any> ask(label: String, block: () -> T): T? = askOrNull(label, log, block).also { if (it == null) failed.add(label) }
    return AndroidAnswers(
        keys = ask("hasKeys") { keyAnswers(device) },
        axes = ask("motionRanges") {
            device.motionRanges.map { AxisAnswer(it.axis, MotionEvent.axisToString(it.axis), it.min, it.max, it.flat) }
        },
        sources = device.sources,
        sensors = ask("sensors") {
            device.sensorManager.getSensorList(Sensor.TYPE_ALL).map { SensorAnswer(it.type, it.stringType, it.maximumRange) }
        },
        vibratorIds = ask("vibratorIds") { device.vibratorManager.vibratorIds.toList() },
        lights = ask("lights") { device.lightsManager.lights.map { LightAnswer(it.name, it.type) } },
        // the battery's name holds the pad's Bluetooth address: only the three values are read
        battery = ask("batteryState") { device.batteryState.let { BatteryAnswer(it.isPresent, it.capacity, it.status) } },
        audioInputs = ask("audio inputs") { audioAnswers(context, AudioManager.GET_DEVICES_INPUTS) },
        audioOutputs = ask("audio outputs") { audioAnswers(context, AudioManager.GET_DEVICES_OUTPUTS) },
        touchPointersSeen = touchPointersSeen,
        failed = failed,
        deviceName = device.name,
        probe = ask("api probe") { probeApi() },
    )
}
