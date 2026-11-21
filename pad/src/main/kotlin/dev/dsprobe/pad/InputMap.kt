package dev.dsprobe.pad

private const val REPORT_ID_INPUT = 0x01
private const val WORD_BITS = 2 * BITS_PER_BYTE

private fun button(name: Button, offset: Int, mask: Int, row: String) =
    FieldSource("in.button.${name.name.lowercase()}", REPORT_ID_INPUT, offset, mask, 1, row)

private fun byteField(id: String, offset: Int, row: String) =
    FieldSource(id, REPORT_ID_INPUT, offset, null, BITS_PER_BYTE, row)

private fun wordField(id: String, offset: Int, row: String) =
    FieldSource(id, REPORT_ID_INPUT, offset, null, WORD_BITS, row)

/** One field of a touch contact: its name, byte after the contact's first, mask and width. */
private class TouchPart(val name: String, val byteDelta: Int, val mask: Int?, val widthBits: Int)

private val TOUCH_PARTS = listOf(
    TouchPart("id", 0, 0x7f, 7),
    TouchPart("active", 0, 0x80, 1),
    TouchPart("x.low8", 1, null, 8),
    TouchPart("x.high4", 2, NIBBLE_LOW_MASK, NIBBLE_BITS),
    TouchPart("y.low4", 2, NIBBLE_HIGH_MASK, NIBBLE_BITS),
    TouchPart("y.high8", 3, null, 8),
)

private fun touchFields(point: Int, base: Int): List<FieldSource> = TOUCH_PARTS.map {
    FieldSource(
        "in.touch.$point.${it.name}",
        REPORT_ID_INPUT,
        base + it.byteDelta,
        it.mask,
        it.widthBits,
        "P17",
    )
}

/**
 * One entry per input, as data; the Route trace reads it and InputMapTest checks it against the
 * parser.
 */
val INPUT_MAP: List<FieldSource> = listOf(
    byteField("in.stick.left.x", 1, "P10"),
    byteField("in.stick.left.y", 2, "P10"),
    byteField("in.stick.right.x", 3, "P10"),
    byteField("in.stick.right.y", 4, "P10"),
    byteField("in.trigger.l2", 5, "P11"),
    byteField("in.trigger.r2", 6, "P11"),
    byteField("in.sequence", 7, "P12"),
    FieldSource("in.dpad", 0x01, 8, 0x0f, 4, "P13"),
    button(Button.SQUARE, 8, 0x10, "P13"),
    button(Button.CROSS, 8, 0x20, "P13"),
    button(Button.CIRCLE, 8, 0x40, "P13"),
    button(Button.TRIANGLE, 8, 0x80, "P13"),
    button(Button.L1, 9, 0x01, "P14"),
    button(Button.R1, 9, 0x02, "P14"),
    button(Button.L2, 9, 0x04, "P14"),
    button(Button.R2, 9, 0x08, "P14"),
    button(Button.CREATE, 9, 0x10, "P14"),
    button(Button.OPTIONS, 9, 0x20, "P14"),
    button(Button.L3, 9, 0x40, "P14"),
    button(Button.R3, 9, 0x80, "P14"),
    button(Button.PS, 10, 0x01, "P15"),
    button(Button.TOUCHPAD_CLICK, 10, 0x02, "P15"),
    button(Button.MUTE, 10, 0x04, "P15"),
    wordField("in.gyro.x", 16, "P16"),
    wordField("in.gyro.y", 18, "P16"),
    wordField("in.gyro.z", 20, "P16"),
    wordField("in.accel.x", 22, "P16"),
    wordField("in.accel.y", 24, "P16"),
    wordField("in.accel.z", 26, "P16"),
) + touchFields(0, 33) + touchFields(1, 37) + listOf(
    FieldSource("in.battery.level", 0x01, 53, 0x0f, 4, "P18"),
    FieldSource("in.battery.charging", 0x01, 53, 0xf0, 4, "P18"),
    FieldSource("in.headphones_in", 0x01, 54, 0x01, 1, "P19"),
    FieldSource("in.mic_in", 0x01, 54, 0x02, 1, "P19"),
)
