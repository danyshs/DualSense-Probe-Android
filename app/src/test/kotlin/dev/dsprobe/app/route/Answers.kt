package dev.dsprobe.app.route

import android.hardware.Sensor
import android.media.AudioDeviceInfo
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.dsprobe.pad.Button

// fake Android answers for the tests; nothing here touches an Android object

private const val LIGHT_TYPE_INPUT = 10001
private const val BATTERY_STATUS_UNKNOWN = 1

/** Android offers nothing at all, no question failed. */
fun fakeAnswers(
    keys: List<KeyAnswer>? = emptyList(),
    axes: List<AxisAnswer>? = emptyList(),
    sources: Int = 0,
    sensors: List<SensorAnswer>? = emptyList(),
    vibratorIds: List<Int>? = emptyList(),
    lights: List<LightAnswer>? = emptyList(),
    battery: BatteryAnswer? = null,
    audioInputs: List<AudioAnswer>? = emptyList(),
    audioOutputs: List<AudioAnswer>? = emptyList(),
    touchPointersSeen: Int = 0,
    failed: List<String> = emptyList(),
) = AndroidAnswers(keys, axes, sources, sensors, vibratorIds, lights, battery, audioInputs, audioOutputs, touchPointersSeen, failed)

private val PAD_KEYS = listOf(
    KeyEvent.KEYCODE_BUTTON_A to Button.CROSS, KeyEvent.KEYCODE_BUTTON_B to Button.CIRCLE,
    KeyEvent.KEYCODE_BUTTON_X to Button.SQUARE, KeyEvent.KEYCODE_BUTTON_Y to Button.TRIANGLE,
    KeyEvent.KEYCODE_BUTTON_L1 to Button.L1, KeyEvent.KEYCODE_BUTTON_R1 to Button.R1,
    KeyEvent.KEYCODE_BUTTON_L2 to Button.L2, KeyEvent.KEYCODE_BUTTON_R2 to Button.R2,
    KeyEvent.KEYCODE_BUTTON_THUMBL to Button.L3, KeyEvent.KEYCODE_BUTTON_THUMBR to Button.R3,
    KeyEvent.KEYCODE_BUTTON_SELECT to Button.CREATE, KeyEvent.KEYCODE_BUTTON_START to Button.OPTIONS,
    KeyEvent.KEYCODE_BUTTON_MODE to Button.PS,
).map { (code, button) -> KeyAnswer(code, "KEYCODE_$code", button, present = true) }

private val PAD_AXES = listOf(
    MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
    MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
).map { AxisAnswer(it, "AXIS_$it", -1f, 1f, 0f) }

val PLAYER_LIGHTS = (1..5).map { LightAnswer("pad:player-$it", LIGHT_TYPE_INPUT) }

/** What the Pixel gave over the USB cable, measured: everything but the mute pieces and audio output. */
fun usbPixelAnswers() = fakeAnswers(
    keys = PAD_KEYS,
    axes = PAD_AXES,
    sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_MOUSE or
        InputDevice.SOURCE_TOUCHPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_SENSOR,
    sensors = listOf(SensorAnswer(Sensor.TYPE_GYROSCOPE, "gyro", 2048f), SensorAnswer(Sensor.TYPE_ACCELEROMETER, "accel", 4f)),
    vibratorIds = listOf(0, 1),
    lights = listOf(LightAnswer(LIGHTBAR_NAME, LIGHT_TYPE_INPUT)) + PLAYER_LIGHTS,
    battery = BatteryAnswer(isPresent = true, capacity = -0.01f, status = BATTERY_STATUS_UNKNOWN),
    audioInputs = listOf(AudioAnswer(AudioDeviceInfo.TYPE_USB_HEADSET)),
    touchPointersSeen = 1,
)
