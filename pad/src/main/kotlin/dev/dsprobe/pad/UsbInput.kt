package dev.dsprobe.pad

/** Raw int16 triple as the pad sends it; per-axis meaning, sign and scale are not facts yet (P16). */
data class Vec3Raw(val x: Short, val y: Short, val z: Short)

/** One finger: contact id, position in touchpad pixels (0-1919, 0-1079). */
data class Touch(val id: Int, val xPx: Int, val yPx: Int)

/** Nibbles as sent; their mapping to percent and charging states is untested (P18). */
data class BatteryRaw(val levelNibble: Int, val chargingNibble: Int)

/** Everything report 0x01 carries, raw as the pad sends it. */
data class UsbInput(
    val leftX: UByte,
    val leftY: UByte,
    val rightX: UByte,
    val rightY: UByte,
    val l2: UByte,
    val r2: UByte,
    val dpad: Dpad,
    val held: Set<Button>,
    val gyro: Vec3Raw,
    val accel: Vec3Raw,
    val touches: List<Touch>,
    val battery: BatteryRaw?,
    val headphonesIn: Boolean?,
    val micIn: Boolean?,
    val sequence: UByte?,
) {
    init {
        require(touches.size <= Limits.TOUCH_POINTS_MAX) { "touches ${touches.size}" }
    }
}
