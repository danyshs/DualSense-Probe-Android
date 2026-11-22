package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import kotlin.math.abs
import kotlin.math.sqrt

private const val HALF_TURN_DEG = 180f
private const val FULL_TURN_DEG = 360f
private const val MS_PER_SECOND = 1_000f
private const val RAD_TO_DEG = 57.29578f
private const val BUBBLE_UNIT_SLACK = 1.001f // float rounding on a vector divided by its length

/** One needle angle per gyroscope axis, in degrees, each in (-180, 180]. */
data class Angles(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = Angles(0f, 0f, 0f)
    }
}

/** [angleDeg] folded into (-180, 180]. */
fun wrapDegrees(angleDeg: Float): Float {
    require(angleDeg.isFinite()) { "angle $angleDeg" }
    var wrapped = angleDeg % FULL_TURN_DEG
    if (wrapped <= -HALF_TURN_DEG) {
        wrapped += FULL_TURN_DEG
    } else if (wrapped > HALF_TURN_DEG) {
        wrapped -= FULL_TURN_DEG
    }
    check(wrapped > -HALF_TURN_DEG && wrapped <= HALF_TURN_DEG) { "wrapped $wrapped" }
    return wrapped
}

/**
 * [angleDeg] turned by [rateDegPerS] for [dtMs]. A rate past the limit and a frame gap past the
 * step limit are cut to it, so one stall cannot spin the needle; the result stays wrapped.
 */
fun integrateDegrees(angleDeg: Float, rateDegPerS: Float, dtMs: Long): Float {
    require(dtMs >= 0L) { "negative step $dtMs ms" }
    val stepMs = minOf(dtMs, AppLimits.GYRO_STEP_MS_MAX)
    val limit = AppLimits.GYRO_RATE_DEG_PER_S_MAX
    val rate = rateDegPerS.coerceIn(-limit, limit)
    return wrapDegrees(angleDeg + rate * stepMs / MS_PER_SECOND)
}

/** A rate as degrees a second: Android's are rad/s; raw counts go through the untested scale. */
fun degPerSecond(rate: Float, unit: MotionUnit): Float = when (unit) {
    MotionUnit.SI -> rate * RAD_TO_DEG
    MotionUnit.RAW_COUNTS -> rate * AppLimits.GYRO_RAW_DEG_PER_S_PER_COUNT
}

/** The needles since the card opened (or since [reset]); fed once per frame. Main thread only. */
class GyroIntegrator {
    var angles: Angles = Angles.ZERO
        private set
    private var lastMs: Long? = null

    fun step(rates: Motion, nowMs: Long): Angles {
        val last = lastMs
        lastMs = nowMs
        val dtMs = if (last == null) 0L else (nowMs - last).coerceAtLeast(0L)
        angles = Angles(
            integrateDegrees(angles.x, degPerSecond(rates.x, rates.unit), dtMs),
            integrateDegrees(angles.y, degPerSecond(rates.y, rates.unit), dtMs),
            integrateDegrees(angles.z, degPerSecond(rates.z, rates.unit), dtMs),
        )
        return angles
    }

    fun reset() {
        angles = Angles.ZERO
        lastMs = null
    }
}

/** True while all three rates are inside the still threshold of their unit. */
fun isStill(m: Motion): Boolean {
    val limit = when (m.unit) {
        MotionUnit.SI -> AppLimits.GYRO_STILL_RAD_PER_S
        MotionUnit.RAW_COUNTS -> AppLimits.GYRO_STILL_RAW_COUNTS
    }
    return abs(m.x) < limit && abs(m.y) < limit && abs(m.z) < limit
}

/**
 * The bubble level: [dx], [dy] in -1..1 are the reading's x and y as a share of its size (y up),
 * [total] is the size of the whole reading.
 */
data class Bubble(val dx: Float, val dy: Float, val total: Float)

fun bubbleOf(m: Motion): Bubble {
    val total = sqrt(m.x * m.x + m.y * m.y + m.z * m.z)
    if (total == 0f) return Bubble(0f, 0f, 0f)
    val bubble = Bubble(m.x / total, m.y / total, total)
    val radiusSquared = bubble.dx * bubble.dx + bubble.dy * bubble.dy
    check(radiusSquared <= BUBBLE_UNIT_SLACK) { "bubble outside the circle" }
    return bubble
}
