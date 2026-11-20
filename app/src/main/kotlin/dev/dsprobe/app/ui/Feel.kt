package dev.dsprobe.app.ui

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.Frame
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// the time-based parts of the screen, as plain data advanced once per frame so tests can drive them

data class TrailPoint(val id: Int, val x: Float, val y: Float, val atMs: Long)

data class Peak(val value: Float, val atMs: Long)

data class Ripple(val x: Float, val y: Float, val atMs: Long)

data class FeelFrame(
    val nowMs: Long,
    val buttonActiveMs: Map<Button, Long>,
    val armActiveMs: Map<Dpad, Long>,
    val leftTrail: List<TrailPoint>,
    val rightTrail: List<TrailPoint>,
    val touchTrail: List<TrailPoint>,
    val l2Peak: Peak,
    val r2Peak: Peak,
    val ripples: List<Ripple>,
    val gyroHistory: List<Motion>,
    val accelHistory: List<Motion>,
) {
    companion object {
        val EMPTY = FeelFrame(
            0L, emptyMap(), emptyMap(), emptyList(), emptyList(), emptyList(),
            Peak(0f, 0L), Peak(0f, 0L), emptyList(), emptyList(), emptyList(),
        )
    }
}

/** The four D-pad arms a direction lights: a diagonal lights two. */
fun armsOf(d: Dpad): Set<Dpad> = when (d) {
    Dpad.N -> setOf(Dpad.N)
    Dpad.NE -> setOf(Dpad.N, Dpad.E)
    Dpad.E -> setOf(Dpad.E)
    Dpad.SE -> setOf(Dpad.S, Dpad.E)
    Dpad.S -> setOf(Dpad.S)
    Dpad.SW -> setOf(Dpad.S, Dpad.W)
    Dpad.W -> setOf(Dpad.W)
    Dpad.NW -> setOf(Dpad.N, Dpad.W)
    Dpad.NONE -> emptySet()
}

/** Full while held, then a linear fade over PRESS_GLOW_FADE_MS from the last moment it was active. */
fun glowAlpha(held: Boolean, activeMs: Long?, nowMs: Long): Float {
    if (held) return 1f
    if (activeMs == null) return 0f
    return (1f - (nowMs - activeMs).toFloat() / AppLimits.PRESS_GLOW_FADE_MS).coerceIn(0f, 1f)
}

/** A peak holds until it has been above the value for TRIGGER_PEAK_HOLD_MS, then drops to the value. */
fun nextPeak(prev: Peak, value: Float, nowMs: Long): Peak = when {
    value >= prev.value -> Peak(value, nowMs)
    nowMs - prev.atMs > AppLimits.TRIGGER_PEAK_HOLD_MS -> Peak(value, nowMs)
    else -> prev
}

private fun trimTrail(points: List<TrailPoint>, nowMs: Long, max: Int): List<TrailPoint> {
    val fresh = points.filter { nowMs - it.atMs <= AppLimits.STICK_TRAIL_MS }
    return if (fresh.size > max) fresh.drop(fresh.size - max) else fresh
}

private fun stickTrail(prev: List<TrailPoint>, x: Float, y: Float, nowMs: Long): List<TrailPoint> {
    val moved = abs(x) > TRAIL_DEADZONE || abs(y) > TRAIL_DEADZONE
    val next = if (moved) prev + TrailPoint(0, x, y, nowMs) else prev
    return trimTrail(next, nowMs, AppLimits.STICK_TRAIL_POINTS_MAX)
}

private const val TRAIL_DEADZONE = 0.04f
private const val TOUCH_TRAIL_POINTS_MAX = AppLimits.STICK_TRAIL_POINTS_MAX * 2

private fun touchTrail(prev: List<TrailPoint>, touches: List<TouchPoint>, nowMs: Long): List<TrailPoint> =
    trimTrail(prev + touches.map { TrailPoint(it.id, it.x, it.y, nowMs) }, nowMs, TOUCH_TRAIL_POINTS_MAX)

private fun <T> appendBounded(list: List<T>, item: T?, max: Int): List<T> {
    if (item == null) return list
    val next = list + item
    return if (next.size > max) next.drop(next.size - max) else next
}

private fun nextRipples(prev: List<Ripple>, frame: Frame, nowMs: Long): List<Ripple> {
    val fresh = prev.filter { nowMs - it.atMs <= AppLimits.CLICK_RIPPLE_MS }
    if (Button.TOUCHPAD_CLICK !in frame.pressedSinceLast) return fresh
    val finger = frame.state.touches.firstOrNull()
    val ripple = Ripple(finger?.x ?: 0.5f, finger?.y ?: 0.5f, nowMs)
    return appendBounded(fresh, ripple, AppLimits.CLICK_RIPPLES_MAX)
}

/** One frame forward. Held or just-latched buttons and arms count as active now; the rest keep their last moment. */
fun advance(prev: FeelFrame, frame: Frame, nowMs: Long): FeelFrame {
    val s = frame.state
    val active = HashMap(prev.buttonActiveMs)
    for (b in s.held) active[b] = nowMs
    for (b in frame.pressedSinceLast) active[b] = nowMs
    val arms = HashMap(prev.armActiveMs)
    for (d in armsOf(s.dpad)) arms[d] = nowMs
    for (dir in frame.dpadSinceLast) for (d in armsOf(dir)) arms[d] = nowMs
    return FeelFrame(
        nowMs = nowMs,
        buttonActiveMs = active,
        armActiveMs = arms,
        leftTrail = stickTrail(prev.leftTrail, s.leftX, s.leftY, nowMs),
        rightTrail = stickTrail(prev.rightTrail, s.rightX, s.rightY, nowMs),
        touchTrail = touchTrail(prev.touchTrail, s.touches, nowMs),
        l2Peak = nextPeak(prev.l2Peak, s.l2, nowMs),
        r2Peak = nextPeak(prev.r2Peak, s.r2, nowMs),
        ripples = nextRipples(prev.ripples, frame, nowMs),
        gyroHistory = appendBounded(prev.gyroHistory, s.gyro, AppLimits.MOTION_TRACE_SAMPLES_MAX),
        accelHistory = appendBounded(prev.accelHistory, s.accel, AppLimits.MOTION_TRACE_SAMPLES_MAX),
    )
}

/** Degrees to rotate and to lean the drawing; [rollDegrees] from sideways gravity, [pitchDegrees] from forward. */
data class Tilt(val rollDegrees: Float, val pitchDegrees: Float)

/**
 * The gravity vector normalised, so the scale of the unit doesn't matter. Only SI readings: raw
 * counts have no proven axes, so the tilt stays off for them.
 */
fun tiltOf(accel: Motion?): Tilt? {
    if (accel == null || accel.unit != MotionUnit.SI) return null
    val length = sqrt(accel.x * accel.x + accel.y * accel.y + accel.z * accel.z)
    if (length < GRAVITY_LENGTH_MIN) return null
    return Tilt(
        rollDegrees = (accel.x / length) * AppLimits.ACCEL_TILT_DEGREES_MAX,
        pitchDegrees = (accel.y / length) * AppLimits.ACCEL_TILT_DEGREES_MAX,
    )
}

private const val GRAVITY_LENGTH_MIN = 0.5f // m/s2; below this the reading is noise

/** The largest absolute value in the history, never below [floor], for a plot that auto-scales. */
fun plotScale(history: List<Motion>, floor: Float): Float {
    var peak = floor
    for (m in history) peak = max(peak, max(abs(m.x), max(abs(m.y), abs(m.z))))
    return peak
}
