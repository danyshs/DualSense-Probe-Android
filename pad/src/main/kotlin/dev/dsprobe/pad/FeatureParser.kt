package dev.dsprobe.pad

/** Feature report 0x20 (P51). [buildDate] is the pad's ASCII text as sent, date and time run together. */
data class FirmwareInfo(
    val hardware: UInt,
    val firmware: UInt,
    val updateVersion: UShort,
    val buildDate: String,
)

private const val REPORT_ID_FIRMWARE = 0x20
private const val BUILD_DATE_FIRST = 1
private const val BUILD_DATE_END = 20 // bytes 1-19
private const val UPDATE_VERSION_RUMBLE_V2_MIN = 0x0215

private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xff

private fun ByteArray.u32le(i: Int): UInt =
    (u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16) or (u8(i + 3) shl 24)).toUInt()

/** Null when the reply is not a 64-byte report 0x20; never throws on pad data. */
fun parseFirmwareInfo(report: ByteArray): FirmwareInfo? {
    if (report.size != Limits.USB_FEATURE_FIRMWARE_REPORT_BYTES) return null
    if (report.u8(0) != REPORT_ID_FIRMWARE) return null
    val text = String(report, BUILD_DATE_FIRST, BUILD_DATE_END - BUILD_DATE_FIRST, Charsets.US_ASCII)
    return FirmwareInfo(
        hardware = report.u32le(24),
        firmware = report.u32le(28),
        updateVersion = (report.u8(44) or (report.u8(45) shl 8)).toUShort(),
        buildDate = text.trimEnd { it == ' ' || it.code == 0 },
    )
}

/** Vibration v2 when the update version is 0x0215 or later (P32). */
fun FirmwareInfo.supportsRumbleV2(): Boolean = updateVersion.toInt() >= UPDATE_VERSION_RUMBLE_V2_MIN
