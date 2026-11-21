package dev.dsprobe.pad

enum class RejectReason { WRONG_ID, WRONG_LENGTH, BAD_DPAD, BAD_TOUCH }

sealed interface ParseResult {
    data class Ok(val input: UsbInput) : ParseResult
    data class Rejected(val reason: RejectReason, val detail: String) : ParseResult
}

private const val REPORT_ID_INPUT = 0x01
private const val DPAD_NONE_NIBBLE = 8
private const val TOUCH_0_OFFSET = 33
private const val TOUCH_1_OFFSET = 37
private const val DPAD_BYTE = 8
private const val GYRO_BYTE = 16
private const val ACCEL_BYTE = 22
private const val BATTERY_BYTE = 53
private const val STATUS_BYTE = 54
private const val HEADPHONES_BIT = 0x01
private const val MIC_BIT = 0x02
private const val AXIS_BYTES = 2

// one touch contact, P17: id and lifted flag, then 12-bit x and 12-bit y sharing the middle byte
private const val TOUCH_ID_MASK = 0x7f
private const val TOUCH_LIFTED_BIT = 0x80
private const val TOUCH_X_LOW_BYTE = 1
private const val TOUCH_SHARED_BYTE = 2
private const val TOUCH_Y_HIGH_BYTE = 3

/**
 * (byte offset, bit mask, button), read off INPUT_MAP so the parser and the map cannot drift apart.
 */
private val BUTTON_BITS: List<Triple<Int, Int, Button>> = INPUT_MAP
    .filter { it.id.startsWith("in.button.") }
    .map { src ->
        Triple(
            src.byteOffset,
            checkNotNull(src.bitMask),
            Button.valueOf(src.id.removePrefix("in.button.").uppercase()),
        )
    }

private fun ByteArray.s16le(i: Int): Short = u16le(i).toShort()

private fun ByteArray.vec3(i: Int) =
    Vec3Raw(s16le(i), s16le(i + AXIS_BYTES), s16le(i + 2 * AXIS_BYTES))

private fun heldButtons(r: ByteArray): Set<Button> {
    val held = HashSet<Button>()
    for ((offset, mask, button) in BUTTON_BITS) {
        if (r.u8(offset) and mask != 0) held.add(button)
    }
    return held
}

/** Null when the nibble is not a defined D-pad state (P13 defines 0-8). */
private fun dpadOf(nibble: Int): Dpad? = when {
    nibble < DPAD_NONE_NIBBLE -> Dpad.entries[nibble]
    nibble == DPAD_NONE_NIBBLE -> Dpad.NONE
    else -> null
}

private fun isLifted(r: ByteArray, base: Int): Boolean = r.u8(base) and TOUCH_LIFTED_BIT != 0

private fun touchXPx(r: ByteArray, base: Int): Int {
    val high = (r.u8(base + TOUCH_SHARED_BYTE) and NIBBLE_LOW_MASK) shl BITS_PER_BYTE
    return r.u8(base + TOUCH_X_LOW_BYTE) or high
}

private fun touchYPx(r: ByteArray, base: Int): Int {
    val high = r.u8(base + TOUCH_Y_HIGH_BYTE) shl NIBBLE_BITS
    return (r.u8(base + TOUCH_SHARED_BYTE) shr NIBBLE_BITS) or high
}

/** A touching contact whose coordinates fall outside the pad. */
private fun touchOutOfRange(r: ByteArray, base: Int): Boolean = !isLifted(r, base) &&
    (touchXPx(r, base) > Limits.TOUCH_X_PX_MAX || touchYPx(r, base) > Limits.TOUCH_Y_PX_MAX)

private fun touchAt(r: ByteArray, base: Int): Touch? {
    if (isLifted(r, base)) return null
    return Touch(r.u8(base) and TOUCH_ID_MASK, touchXPx(r, base), touchYPx(r, base))
}

private fun checkShape(report: ByteArray): ParseResult.Rejected? {
    if (report.size != Limits.USB_INPUT_REPORT_BYTES) {
        return ParseResult.Rejected(
            RejectReason.WRONG_LENGTH,
            "got ${report.size} bytes, want ${Limits.USB_INPUT_REPORT_BYTES}",
        )
    }
    if (report.u8(0) != REPORT_ID_INPUT) {
        return ParseResult.Rejected(
            RejectReason.WRONG_ID,
            "got id 0x%02x, want 0x01".format(report.u8(0)),
        )
    }
    return null
}

/** Parses report 0x01. Bad pad data returns Rejected; never throws on it. */
fun parseUsbInput(report: ByteArray): ParseResult =
    checkShape(report) ?: checkFields(report) ?: ParseResult.Ok(decode(report))

/** A report of the right shape whose d-pad or touch bytes can't be true. */
private fun checkFields(report: ByteArray): ParseResult.Rejected? {
    val nibble = report.u8(DPAD_BYTE) and NIBBLE_LOW_MASK
    // a lie is worse than a drop: one bad contact rejects the whole report
    val badTouch =
        touchOutOfRange(report, TOUCH_0_OFFSET) || touchOutOfRange(report, TOUCH_1_OFFSET)
    return when {
        dpadOf(nibble) == null ->
            ParseResult.Rejected(RejectReason.BAD_DPAD, "d-pad nibble $nibble")

        badTouch ->
            ParseResult.Rejected(RejectReason.BAD_TOUCH, "touch coordinates outside the pad")

        else -> null
    }
}

/** A report checkShape and checkFields both passed. */
private fun decode(report: ByteArray): UsbInput {
    val dpad = checkNotNull(dpadOf(report.u8(DPAD_BYTE) and NIBBLE_LOW_MASK)) { "d-pad checked" }
    val input = UsbInput(
        leftX = report[1].toUByte(), leftY = report[2].toUByte(),
        rightX = report[3].toUByte(), rightY = report[4].toUByte(),
        l2 = report[5].toUByte(), r2 = report[6].toUByte(),
        dpad = dpad,
        held = heldButtons(report),
        gyro = report.vec3(GYRO_BYTE),
        accel = report.vec3(ACCEL_BYTE),
        touches = listOfNotNull(touchAt(report, TOUCH_0_OFFSET), touchAt(report, TOUCH_1_OFFSET)),
        battery = BatteryRaw(
            report.u8(BATTERY_BYTE) and NIBBLE_LOW_MASK,
            report.u8(BATTERY_BYTE) shr NIBBLE_BITS,
        ),
        headphonesIn = report.u8(STATUS_BYTE) and HEADPHONES_BIT != 0,
        micIn = report.u8(STATUS_BYTE) and MIC_BIT != 0,
        sequence = report[7].toUByte(),
    )
    check(input.touches.size <= Limits.TOUCH_POINTS_MAX) { "touch count" }
    return input
}
