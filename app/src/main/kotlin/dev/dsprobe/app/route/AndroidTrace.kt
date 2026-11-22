package dev.dsprobe.app.route

import android.hardware.Sensor
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.dsprobe.app.debug.controlLabel
import dev.dsprobe.app.debug.requestControl
import dev.dsprobe.app.debug.requestText
import dev.dsprobe.app.debug.traceLine
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.app.output.AskKind
import dev.dsprobe.pad.Button
import java.util.Locale

// Pure descriptions of what the Android route touched: the hook, the constant's name and value, the
// arguments, and Android's answer. No Android object is called, so tests build the same rows from
// fakes.

const val HOOK_KEY = "Activity.dispatchKeyEvent"
const val HOOK_MOTION = "Activity.dispatchGenericMotionEvent"
const val HOOK_TOUCH = "Activity.dispatchTouchEvent"
const val HOOK_SENSOR = "SensorEventListener.onSensorChanged"

/** Every axis at rest: where the first reading is compared from. */
val AXES_AT_REST = AxisReading(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

private const val AXIS_MARGIN_UNITS = 0.0005f

private val SOURCE_NAMES = listOf(
    "KEYBOARD" to InputDevice.SOURCE_KEYBOARD,
    "GAMEPAD" to InputDevice.SOURCE_GAMEPAD,
    "JOYSTICK" to InputDevice.SOURCE_JOYSTICK,
    "MOUSE" to InputDevice.SOURCE_MOUSE,
    "TOUCHPAD" to InputDevice.SOURCE_TOUCHPAD,
    "SENSOR" to InputDevice.SOURCE_SENSOR,
)

private fun sourceText(source: Int): String {
    val names = SOURCE_NAMES.filter {
        source and it.second == it.second
    }.joinToString("|") { it.first }
    return String.format(Locale.ROOT, "0x%08x %s", source, names.ifEmpty { "none" })
}

fun buttonControl(button: Button): String = "in.button.${button.name.lowercase()}"

/** When and from which device and source an Android event came, and the pad state after it. */
data class EventOrigin(val timeNs: Long, val deviceId: Int, val source: Int, val state: PadState)

/**
 * One key event from the pad. Unmapped codes are named with their code and scan code, never
 * dropped.
 */

fun keyTraceEvent(
    origin: EventOrigin,
    down: Boolean,
    keyCode: Int,
    keyName: String,
    scanCode: Int,
): FeedEvent {
    val (timeNs, deviceId, source) = origin
    val state = origin.state
    val button = buttonForKeyCode(keyCode)
    val control = if (button != null) buttonControl(button) else "in.key.unmapped.$keyCode"
    val action = if (down) {
        "KeyEvent.ACTION_DOWN = ${KeyEvent.ACTION_DOWN}"
    } else {
        "KeyEvent.ACTION_UP = ${KeyEvent.ACTION_UP}"
    }
    val meaning = if (button != null) {
        "maps to Button.${button.name}"
    } else {
        "UNMAPPED key code, no pad control is moved"
    }
    val detail = "hook $HOOK_KEY  $action  KeyEvent.$keyName = $keyCode  scan $scanCode  " +
        "device $deviceId source ${sourceText(source)}  $meaning"
    val headline =
        (if (button != null) controlLabel(control) else "Key $keyCode") + "  " +
            (if (down) "pressed" else "released")
    val location = "dispatchKeyEvent · $keyName=$keyCode · scan $scanCode · device $deviceId"
    return FeedEvent(
        timeNs,
        Route.ANDROID,
        null,
        traceLine(control, Route.ANDROID, detail),
        control,
        state,
        headline,
        location,
    )
}

private class AxisSpec(
    val axis: Int,
    val name: String,
    val control: String,
    val read: (AxisReading) -> Float,
)

private val AXIS_SPECS = listOf(
    AxisSpec(MotionEvent.AXIS_X, "AXIS_X", "in.stick.left.x") { it.x },
    AxisSpec(MotionEvent.AXIS_Y, "AXIS_Y", "in.stick.left.y") { it.y },
    AxisSpec(MotionEvent.AXIS_Z, "AXIS_Z", "in.stick.right.x") { it.z },
    AxisSpec(MotionEvent.AXIS_RZ, "AXIS_RZ", "in.stick.right.y") { it.rz },
    AxisSpec(MotionEvent.AXIS_LTRIGGER, "AXIS_LTRIGGER", "in.trigger.l2") { it.lTrigger },
    AxisSpec(MotionEvent.AXIS_RTRIGGER, "AXIS_RTRIGGER", "in.trigger.r2") { it.rTrigger },
    AxisSpec(MotionEvent.AXIS_HAT_X, "AXIS_HAT_X", "in.dpad") { it.hatX },
    AxisSpec(MotionEvent.AXIS_HAT_Y, "AXIS_HAT_Y", "in.dpad") { it.hatY },
)

private const val SOURCE_JOYSTICK = "JOYSTICK"

private val KEY_NAMES = listOf(
    KeyEvent.KEYCODE_BUTTON_A to "KEYCODE_BUTTON_A",
    KeyEvent.KEYCODE_BUTTON_B to "KEYCODE_BUTTON_B",
    KeyEvent.KEYCODE_BUTTON_X to "KEYCODE_BUTTON_X",
    KeyEvent.KEYCODE_BUTTON_Y to "KEYCODE_BUTTON_Y",
    KeyEvent.KEYCODE_BUTTON_L1 to "KEYCODE_BUTTON_L1",
    KeyEvent.KEYCODE_BUTTON_R1 to "KEYCODE_BUTTON_R1",
    KeyEvent.KEYCODE_BUTTON_L2 to "KEYCODE_BUTTON_L2",
    KeyEvent.KEYCODE_BUTTON_R2 to "KEYCODE_BUTTON_R2",
    KeyEvent.KEYCODE_BUTTON_THUMBL to "KEYCODE_BUTTON_THUMBL",
    KeyEvent.KEYCODE_BUTTON_THUMBR to "KEYCODE_BUTTON_THUMBR",
    KeyEvent.KEYCODE_BUTTON_SELECT to "KEYCODE_BUTTON_SELECT",
    KeyEvent.KEYCODE_BUTTON_START to "KEYCODE_BUTTON_START",
    KeyEvent.KEYCODE_BUTTON_MODE to "KEYCODE_BUTTON_MODE",
)

private fun sourceOfButton(control: String): String? {
    if (control == "in.button.touchpad_click") {
        return "MotionEvent.BUTTON_PRIMARY = ${MotionEvent.BUTTON_PRIMARY}"
    }
    val key = KEY_NAMES.firstOrNull { (code, _) ->
        buttonForKeyCode(code)?.let(::buttonControl) == control
    } ?: return null
    return "KeyEvent.${key.second} = ${key.first}"
}

/**
 * The Android source of a pad control, from the same tables the trace rows use: the axis, key code,
 * pointer or sensor that carries it. Null when Android gives no event for it.
 */
fun androidSourceOf(control: String): String? = when {
    control.startsWith("in.button.") -> sourceOfButton(control)

    control.startsWith("in.gyro.") -> "SensorManager TYPE_GYROSCOPE"

    control.startsWith("in.accel.") -> "SensorManager TYPE_ACCELEROMETER"

    control.startsWith("in.touch.0.") && (".x." in control || ".y." in control) ->
        "MotionEvent.AXIS_X/AXIS_Y (mouse pointer)"

    else -> AXIS_SPECS.filter { it.control == control }.takeIf { it.isNotEmpty() }
        ?.let { specs ->
            "MotionEvent." + specs.joinToString(" / ") { it.name } + " · source $SOURCE_JOYSTICK"
        }
}

private fun signed(v: Float): String = String.format(Locale.ROOT, "%+.3f", v)

/** One row per joystick axis that moved between two readings. */
fun axisTraceEvents(
    origin: EventOrigin,
    hook: String,
    prev: AxisReading,
    cur: AxisReading,
): List<FeedEvent> {
    val (timeNs, deviceId, source) = origin
    val state = origin.state
    val out = ArrayList<FeedEvent>(AXIS_SPECS.size)
    for (spec in AXIS_SPECS) {
        val before = spec.read(prev)
        val now = spec.read(cur)
        if (kotlin.math.abs(now - before) <= AXIS_MARGIN_UNITS) continue
        val detail =
            "hook $hook  MotionEvent.${spec.name} = ${spec.axis}  raw ${signed(
                before,
            )} -> ${signed(now)}  " +
                "device $deviceId source ${sourceText(source)}"
        out.add(
            FeedEvent(
                timeNs,
                Route.ANDROID,
                null,
                traceLine(spec.control, Route.ANDROID, detail),
                spec.control,
                state,
            ),
        )
    }
    check(out.size <= AXIS_SPECS.size) { "more rows than axes" }
    return out
}

/**
 * The touchpad's mouse pointer and its click (MotionEvent.BUTTON_PRIMARY = 1): Android's only view
 * of it.
 */
fun pointerTraceEvents(
    origin: EventOrigin,
    hook: String,
    dot: TouchPoint,
    wasDown: Boolean,
    isDown: Boolean,
): List<FeedEvent> {
    val (timeNs, deviceId, source) = origin
    val state = origin.state
    val xFraction = dot.x
    val yFraction = dot.y
    val moveControl = "in.touch.0.pointer"
    val move =
        "hook $hook  MotionEvent.AXIS_X/AXIS_Y as window fractions x ${signed(
            xFraction,
        )} y ${signed(yFraction)}  " +
            "device $deviceId source ${sourceText(source)}"
    val out =
        arrayListOf(
            FeedEvent(
                timeNs,
                Route.ANDROID,
                null,
                traceLine(moveControl, Route.ANDROID, move),
                moveControl,
                state,
            ),
        )
    if (wasDown != isDown) {
        val control = "in.button.touchpad_click"
        val click =
            "hook $hook  MotionEvent.BUTTON_PRIMARY = ${MotionEvent.BUTTON_PRIMARY}  " +
                "${if (wasDown) 1 else 0} -> ${if (isDown) 1 else 0}  " +
                "device $deviceId source ${sourceText(source)}"
        out.add(
            FeedEvent(
                timeNs,
                Route.ANDROID,
                null,
                traceLine(control, Route.ANDROID, click),
                control,
                state,
            ),
        )
    }
    return out
}

private const val SENSOR_AXES = 3

/** A sensor sample: the sensor type's constant, its three values in SI units. */
fun sensorTraceEvent(
    timeNs: Long,
    sensorType: Int,
    values: FloatArray,
    state: PadState,
): FeedEvent? {
    require(values.size >= SENSOR_AXES) { "sensor gave ${values.size} values" }
    val (x, y, z) = values
    val (name, control) = when (sensorType) {
        Sensor.TYPE_GYROSCOPE -> "TYPE_GYROSCOPE" to "in.gyro"
        Sensor.TYPE_ACCELEROMETER -> "TYPE_ACCELEROMETER" to "in.accel"
        else -> return null
    }
    val detail = "hook $HOOK_SENSOR  Sensor.$name = $sensorType  values x ${signed(
        x,
    )} y ${signed(y)} z ${signed(z)}  (SI units, axis meaning unproven)"
    return FeedEvent(
        timeNs,
        Route.ANDROID,
        null,
        traceLine(control, Route.ANDROID, detail),
        control,
        state,
    )
}

private const val WAVEFORM_ENTRIES_SHOWN = 6

private fun ellipsis(size: Int, shown: Int): String = if (size > shown) {
    "...($size entries)"
} else {
    ""
}

/** One vibrator's waveform, summarised: the first entries and the length. */
private fun waveformArgs(lane: Waveform): String {
    val shown = minOf(lane.timingsMs.size, WAVEFORM_ENTRIES_SHOWN)
    return "timings ${lane.timingsMs.take(shown)}${ellipsis(lane.timingsMs.size, shown)} ms, " +
        "amplitudes ${lane.amplitudes.take(
            shown,
        )}${ellipsis(lane.amplitudes.size, shown)}, repeat -1"
}

fun oneShotCall(plan: List<Pair<Int, Int>>, durationMs: Int): String =
    "VibratorManager.vibrate(CombinedVibration.startParallel()" +
        plan.joinToString("") {
            ".addVibrator(id ${it.first}, VibrationEffect.createOneShot($durationMs ms, " +
                "amplitude ${it.second}))"
        } +
        ".combine())"

fun waveformCall(lanes: List<Waveform>): String =
    "VibratorManager.cancel(); VibratorManager.vibrate(CombinedVibration.startParallel()" +
        lanes.joinToString("") {
            ".addVibrator(id ${it.vibratorId}, VibrationEffect.createWaveform(${waveformArgs(it)}))"
        } +
        ".combine())"

/** One light in a lights request: its name, id and the colour as ARGB. */
data class LightEntry(val name: String, val id: Int, val argb: Int)

fun lightsCall(entries: List<LightEntry>): String =
    "LightsManager.LightsSession.requestLights(LightsRequest.Builder()" +
        entries.joinToString("") {
            (
                ".addLight(Light \"${it.name}\" id ${it.id}, " +
                    "LightState.Builder().setColor(0x%08x).build())"
                ).format(
                it.argb,
            )
        } +
        ".build())"

private fun answerText(answer: ApiAnswer): String = when (answer) {
    ApiAnswer.Ok ->
        "Android returned normally (the calls are void: whether the pad changed is the " +
            "did-it-change answer)"

    is ApiAnswer.Failed -> "Android answered: ${answer.reason}"
}

/**
 * One output request on Android mode: every call made with its arguments, then what Android
 * returned or threw.
 */
fun androidOutputEvent(
    timeNs: Long,
    req: OutputRequest,
    calls: List<String>,
    answer: ApiAnswer,
    state: PadState?,
): FeedEvent {
    val control = requestControl(req)
    val made = if (calls.isEmpty()) "no Android call made" else calls.joinToString("; ")
    val detail = "request ${requestText(req)}  call $made  -> ${answerText(answer)}"
    return FeedEvent(
        timeNs,
        Route.ANDROID,
        null,
        traceLine(control, Route.ANDROID, detail),
        control,
        state,
    )
}

fun askControl(kind: AskKind): String = when (kind) {
    AskKind.LIGHTBAR -> "out.lightbar"
    AskKind.PLAYER_LEDS -> "out.led.player"
    AskKind.MUTE_LED -> "out.led.mute"
    AskKind.RUMBLE -> "out.rumble"
}

/** The developer's "did it change?" answer, next to what Android said. */
fun changedAnswerEvent(
    timeNs: Long,
    kind: AskKind,
    sent: String,
    answer: ApiAnswer,
    changed: Boolean,
): FeedEvent {
    val control = askControl(kind)
    val detail = "answer to did-it-change for $sent  Android said ${answerText(
        answer,
    )}  developer said ${if (changed) "yes" else "no"}"
    return FeedEvent(
        timeNs,
        Route.ANDROID,
        null,
        traceLine(control, Route.ANDROID, detail),
        control,
        null,
    )
}
