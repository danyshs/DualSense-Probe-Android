package dev.dsprobe.app.route

import dev.dsprobe.app.live.Battery
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.Touch
import dev.dsprobe.pad.UsbInput
import dev.dsprobe.pad.Vec3Raw

private const val BYTE_MAX = 255f

// 0 -> -1, 255 -> +1; right and down positive as in PadState (the sign of Y is a phone check, see
// FACTS gaps)
private fun stick(b: UByte): Float = b.toInt() / BYTE_MAX * 2f - 1f

private fun trigger(b: UByte): Float = b.toInt() / BYTE_MAX

// raw int16 counts, shown as they are: scale and axis meaning are not facts yet (P16)
private fun motion(v: Vec3Raw) =
    Motion(v.x.toFloat(), v.y.toFloat(), v.z.toFloat(), MotionUnit.RAW_COUNTS)

private fun touchPoint(t: Touch): TouchPoint {
    require(t.xPx in 0..Limits.TOUCH_X_PX_MAX && t.yPx in 0..Limits.TOUCH_Y_PX_MAX) {
        "touch outside the pad: ${t.xPx}, ${t.yPx}"
    }
    return TouchPoint(
        t.id,
        t.xPx.toFloat() / Limits.TOUCH_X_PX_MAX,
        t.yPx.toFloat() / Limits.TOUCH_Y_PX_MAX,
        isPointer = false,
    )
}

/** One parsed report as the route-neutral state the screens draw. */
fun toPadState(u: UsbInput): PadState = PadState(
    leftX = stick(
        u.leftX,
    ),
    leftY = stick(u.leftY), rightX = stick(u.rightX), rightY = stick(u.rightY),
    l2 = trigger(u.l2), r2 = trigger(u.r2),
    dpad = u.dpad,
    held = u.held,
    gyro = motion(u.gyro),
    accel = motion(u.accel),
    touches = u.touches.map(::touchPoint),
    battery = u.battery?.let { Battery.RawNibble(it.levelNibble, it.chargingNibble) },
    headphonesIn = u.headphonesIn,
    sequence = u.sequence?.toInt(),
)
