package dev.dsprobe.app.route

import android.view.KeyEvent
import dev.dsprobe.app.live.Battery
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import dev.dsprobe.pad.Limits

// pure conversions from Android's event values to PadState; nothing here touches an Android object

private const val HAT_PRESSED_RATIO_MIN = 0.5f

/** Key code to pad button (P71). Mute has no key; unknown codes give null. */
fun buttonForKeyCode(keyCode: Int): Button? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> Button.CROSS
    KeyEvent.KEYCODE_BUTTON_B -> Button.CIRCLE
    KeyEvent.KEYCODE_BUTTON_X -> Button.SQUARE
    KeyEvent.KEYCODE_BUTTON_Y -> Button.TRIANGLE
    KeyEvent.KEYCODE_BUTTON_L1 -> Button.L1
    KeyEvent.KEYCODE_BUTTON_R1 -> Button.R1
    KeyEvent.KEYCODE_BUTTON_L2 -> Button.L2
    KeyEvent.KEYCODE_BUTTON_R2 -> Button.R2
    KeyEvent.KEYCODE_BUTTON_THUMBL -> Button.L3
    KeyEvent.KEYCODE_BUTTON_THUMBR -> Button.R3
    KeyEvent.KEYCODE_BUTTON_SELECT -> Button.CREATE
    KeyEvent.KEYCODE_BUTTON_START -> Button.OPTIONS
    KeyEvent.KEYCODE_BUTTON_MODE -> Button.PS
    else -> null
}

/** A float from outside: NaN becomes 0, the rest is clamped. */
fun clampAxis(v: Float, min: Float, max: Float): Float = if (v.isNaN()) 0f else v.coerceIn(min, max)

private fun hatStep(v: Float): Int = when {
    v <= -HAT_PRESSED_RATIO_MIN -> -1
    v >= HAT_PRESSED_RATIO_MIN -> 1
    else -> 0
}

/** Hat axes to D-pad: Y -1 is up (the SDK's AXIS_HAT_Y), X +1 is right. */
fun dpadFromHat(hatX: Float, hatY: Float): Dpad = when (hatStep(hatY)) {
    -1 -> when (hatStep(hatX)) { -1 -> Dpad.NW; 1 -> Dpad.NE; else -> Dpad.N }
    1 -> when (hatStep(hatX)) { -1 -> Dpad.SW; 1 -> Dpad.SE; else -> Dpad.S }
    else -> when (hatStep(hatX)) { -1 -> Dpad.W; 1 -> Dpad.E; else -> Dpad.NONE }
}

/** One joystick motion event's axes (P73). */
data class AxisReading(
    val x: Float, val y: Float, val z: Float, val rz: Float,
    val lTrigger: Float, val rTrigger: Float, val hatX: Float, val hatY: Float,
)

fun PadState.withKey(keyCode: Int, down: Boolean): PadState {
    val button = buttonForKeyCode(keyCode) ?: return this
    return copy(held = if (down) held + button else held - button)
}

fun PadState.withAxes(a: AxisReading): PadState = copy(
    leftX = clampAxis(a.x, -1f, 1f), leftY = clampAxis(a.y, -1f, 1f),
    rightX = clampAxis(a.z, -1f, 1f), rightY = clampAxis(a.rz, -1f, 1f),
    l2 = clampAxis(a.lTrigger, 0f, 1f), r2 = clampAxis(a.rTrigger, 0f, 1f),
    dpad = dpadFromHat(a.hatX, a.hatY),
)

/** The mouse pointer the touchpad drives, as a position 0..1 of the window; the click is the primary button. */
fun PadState.withPointer(x: Float, y: Float, primaryDown: Boolean): PadState = copy(
    touches = listOf(TouchPoint(0, clampAxis(x, 0f, 1f), clampAxis(y, 0f, 1f), isPointer = true)),
    held = if (primaryDown) held + Button.TOUCHPAD_CLICK else held - Button.TOUCHPAD_CLICK,
)

fun PadState.withGyro(x: Float, y: Float, z: Float): PadState = copy(gyro = Motion(x, y, z, MotionUnit.SI))

fun PadState.withAccel(x: Float, y: Float, z: Float): PadState = copy(accel = Motion(x, y, z, MotionUnit.SI))

/** Capacity 0..1 or NaN; anything else is shown as Android's raw answer, never as 0 percent and never hidden. */
fun batteryFrom(isPresent: Boolean, capacity: Float, status: Int): Battery {
    if (!isPresent || capacity.isNaN() || capacity < 0f) return Battery.AndroidRaw(capacity, status)
    return Battery.Percent((capacity.coerceAtMost(1f) * 100f).toInt())
}

/**
 * Which vibrator gets which strength, from the ids Android reports: the lower id is left, the next
 * is right. One id gets the stronger of the two; a motor at 0 is left out. Ids past two are unused.
 */
fun rumblePlan(ids: List<Int>, left: Int, right: Int): List<Pair<Int, Int>> {
    require(left in 0..Limits.MOTOR_STRENGTH_MAX && right in 0..Limits.MOTOR_STRENGTH_MAX) { "strength $left, $right" }
    val sorted = ids.sorted()
    val plan = when (sorted.size) {
        0 -> emptyList()
        1 -> listOf(sorted[0] to maxOf(left, right))
        else -> listOf(sorted[0] to left, sorted[1] to right)
    }
    return plan.filter { it.second > 0 }
}