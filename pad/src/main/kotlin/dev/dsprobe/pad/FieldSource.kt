package dev.dsprobe.pad

private const val FIELD_BITS_MAX = 32

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
        require(byteOffset in 1 until Limits.USB_INPUT_REPORT_BYTES) { "offset $byteOffset" }
        require(widthBits in 1..FIELD_BITS_MAX) { "width $widthBits" }
        require(bitMask == null || bitMask in 1..BYTE_MASK) { "mask $bitMask" }
    }
}
