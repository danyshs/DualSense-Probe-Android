package dev.dsprobe.app.route

import android.content.Context
import android.hardware.Sensor
import android.media.AudioManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

private val PAD_KEY_CODES = intArrayOf(
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
    KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_MODE,
)

private fun keyAnswers(device: InputDevice): List<KeyAnswer> {
    val has = device.hasKeys(*PAD_KEY_CODES)
    check(has.size == PAD_KEY_CODES.size) { "hasKeys answered ${has.size} for ${PAD_KEY_CODES.size} codes" }
    return PAD_KEY_CODES.mapIndexed { i, code -> KeyAnswer(code, KeyEvent.keyCodeToString(code), buttonForKeyCode(code), has[i]) }
}

private fun audioTypes(context: Context, flag: Int): List<AudioAnswer> {
    val audio = checkNotNull(context.getSystemService(AudioManager::class.java)) { "no AudioManager" }
    return audio.getDevices(flag).map { AudioAnswer(it.type) }
}

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
        audioInputs = ask("audio inputs") { audioTypes(context, AudioManager.GET_DEVICES_INPUTS) },
        audioOutputs = ask("audio outputs") { audioTypes(context, AudioManager.GET_DEVICES_OUTPUTS) },
        touchPointersSeen = touchPointersSeen,
        failed = failed,
    )
}
