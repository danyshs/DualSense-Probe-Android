package dev.dsprobe.pad

/**
 * Where one input lives in an input report: bytes from [byteOffset] (little-endian when wider than
 * a byte), masked by [bitMask] when set, [widthBits] wide. [factsRow] is the FACTS.md row.
 */
data class FieldSource(
    val id: String,
    val reportId: Int,
    val byteOffset: Int,
    val bitMask: Int?,
    val widthBits: Int,
    val factsRow: String,
) {
    init {
        require(byteOffset in 1 until Limits.USB_INPUT_REPORT_BYTES) { "offset ${byteOffset}" }
        require(widthBits in 1..32) { "width ${widthBits}" }
        require(bitMask == null || bitMask in 1..0xff) { "mask ${bitMask}" }
    }
}

private fun button(name: Button, offset: Int, mask: Int, row: String) =
    FieldSource("in.button.${name.name.lowercase()}", 0x01, offset, mask, 1, row)

private fun byteField(id: String, offset: Int, row: String) = FieldSource(id, 0x01, offset, null, 8, row)

private fun wordField(id: String, offset: Int, row: String) = FieldSource(id, 0x01, offset, null, 16, row)

private fun touchFields(point: Int, base: Int): List<FieldSource> = listOf(
    FieldSource("in.touch.${point}.id", 0x01, base, 0x7f, 7, "P17"),
    FieldSource("in.touch.${point}.active", 0x01, base, 0x80, 1, "P17"),
    FieldSource("in.touch.${point}.x.low8", 0x01, base + 1, null, 8, "P17"),
    FieldSource("in.touch.${point}.x.high4", 0x01, base + 2, 0x0f, 4, "P17"),
    FieldSource("in.touch.${point}.y.low4", 0x01, base + 2, 0xf0, 4, "P17"),
    FieldSource("in.touch.${point}.y.high8", 0x01, base + 3, null, 8, "P17"),
)

/** One entry per input, as data; the Route trace reads it and InputMapTest checks it against the parser. */
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
