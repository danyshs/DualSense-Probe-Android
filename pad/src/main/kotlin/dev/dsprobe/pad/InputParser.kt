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

/** (byte offset, bit mask, button), read off INPUT_MAP so the parser and the map cannot drift apart. */
private val BUTTON_BITS: List<Triple<Int, Int, Button>> = INPUT_MAP
    .filter { it.id.startsWith("in.button.") }
    .map { src -> Triple(src.byteOffset, checkNotNull(src.bitMask), Button.valueOf(src.id.removePrefix("in.button.").uppercase())) }

private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xff

private fun ByteArray.s16le(i: Int): Short = (u8(i) or (u8(i + 1) shl 8)).toShort()

private fun ByteArray.vec3(i: Int) = Vec3Raw(s16le(i), s16le(i + 2), s16le(i + 4))

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

private fun isLifted(r: ByteArray, base: Int): Boolean = r.u8(base) and 0x80 != 0

private fun touchXPx(r: ByteArray, base: Int): Int = r.u8(base + 1) or ((r.u8(base + 2) and 0x0f) shl 8)

private fun touchYPx(r: ByteArray, base: Int): Int = (r.u8(base + 2) shr 4) or (r.u8(base + 3) shl 4)

/** A touching contact whose coordinates fall outside the pad. */
private fun touchOutOfRange(r: ByteArray, base: Int): Boolean =
    !isLifted(r, base) && (touchXPx(r, base) > Limits.TOUCH_X_PX_MAX || touchYPx(r, base) > Limits.TOUCH_Y_PX_MAX)

private fun touchAt(r: ByteArray, base: Int): Touch? =
    if (isLifted(r, base)) null else Touch(r.u8(base) and 0x7f, touchXPx(r, base), touchYPx(r, base))

private fun checkShape(report: ByteArray): ParseResult.Rejected? {
    if (report.size != Limits.USB_INPUT_REPORT_BYTES) {
        return ParseResult.Rejected(RejectReason.WRONG_LENGTH, "got ${report.size} bytes, want ${Limits.USB_INPUT_REPORT_BYTES}")
    }
    if (report.u8(0) != REPORT_ID_INPUT) {
        return ParseResult.Rejected(RejectReason.WRONG_ID, "got id 0x%02x, want 0x01".format(report.u8(0)))
    }
    return null
}

/** Parses report 0x01. Bad pad data returns Rejected; never throws on it. */
fun parseUsbInput(report: ByteArray): ParseResult {
    checkShape(report)?.let { return it }
    val dpad = dpadOf(report.u8(8) and 0x0f)
        ?: return ParseResult.Rejected(RejectReason.BAD_DPAD, "d-pad nibble ${report.u8(8) and 0x0f}")
    // a lie is worse than a drop: one bad contact rejects the whole report
    if (touchOutOfRange(report, TOUCH_0_OFFSET) || touchOutOfRange(report, TOUCH_1_OFFSET)) {
        return ParseResult.Rejected(RejectReason.BAD_TOUCH, "touch coordinates outside the pad")
    }
    val input = UsbInput(
        leftX = report[1].toUByte(), leftY = report[2].toUByte(),
        rightX = report[3].toUByte(), rightY = report[4].toUByte(),
        l2 = report[5].toUByte(), r2 = report[6].toUByte(),
        dpad = dpad,
        held = heldButtons(report),
        gyro = report.vec3(16),
        accel = report.vec3(22),
        touches = listOfNotNull(touchAt(report, TOUCH_0_OFFSET), touchAt(report, TOUCH_1_OFFSET)),
        battery = BatteryRaw(report.u8(53) and 0x0f, report.u8(53) shr 4),
        headphonesIn = report.u8(54) and 0x01 != 0,
        micIn = report.u8(54) and 0x02 != 0,
        sequence = report[7].toUByte(),
    )
    check(input.touches.size <= Limits.TOUCH_POINTS_MAX) { "touch count" }
    return ParseResult.Ok(input)
}
