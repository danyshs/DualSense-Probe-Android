package dev.dsprobe.app.debug

import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import kotlin.math.atan2
import kotlin.math.hypot

/** The angles the controller picture turns by. */
enum class TiltAxis { HOME_ROLL, PITCH, ROLL, YAW }

/**
 * Which way each angle turns the picture: +1 or -1, one entry per angle, so a wrong sign found on
 * the phone is a one-line fix. HOME_ROLL is the flat rotation on the home screen (positive turns
 * clockwise: a pad tilted left reads +x and must turn the picture left, so it is -1). PITCH, ROLL
 * and YAW are the three graphicsLayer rotations on the motion cards; the signs are untested.
 */
val TILT_SIGN: Map<TiltAxis, Float> = mapOf(
    TiltAxis.HOME_ROLL to -1f,
    TiltAxis.PITCH to 1f,
    TiltAxis.ROLL to 1f,
    TiltAxis.YAW to -1f,
)

private const val RAD_TO_DEG_TILT = 57.29578f
private const val GRAVITY_LENGTH_MIN = 0.5f // m/s2; below this the reading is noise

/** [TILT_SIGN] for [axis]; a missing entry fails here. */
fun tiltSign(axis: TiltAxis): Float = checkNotNull(TILT_SIGN[axis]) { "no sign for $axis" }

/** Degrees to show: [pitchDeg] about x, [rollDeg] about y, [yawDeg] about z, signs applied. */
data class Attitude(val pitchDeg: Float, val rollDeg: Float, val yawDeg: Float)

/**
 * Pitch and roll from the direction of gravity (no drift); yaw is [yawDeg], the integrated gyro.
 * Raw counts and a reading too short to be gravity give a level pad, since their axes are
 * unproven.
 */
fun attitudeOf(gravity: Motion?, yawDeg: Float): Attitude {
    val yaw = tiltSign(TiltAxis.YAW) * yawDeg
    if (gravity == null || gravity.unit != MotionUnit.SI) return Attitude(0f, 0f, yaw)
    if (hypot(hypot(gravity.x, gravity.y), gravity.z) < GRAVITY_LENGTH_MIN) {
        return Attitude(0f, 0f, yaw)
    }
    val pitch = atan2(gravity.y, hypot(gravity.x, gravity.z)) * RAD_TO_DEG_TILT
    val roll = atan2(gravity.x, gravity.z) * RAD_TO_DEG_TILT
    return Attitude(tiltSign(TiltAxis.PITCH) * pitch, tiltSign(TiltAxis.ROLL) * roll, yaw)
}
