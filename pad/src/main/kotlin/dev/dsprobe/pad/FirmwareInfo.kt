package dev.dsprobe.pad

/**
 * Feature report 0x20 (P51). [buildDate] is the pad's ASCII text as sent, date and time run
 * together.
 */
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
private const val HARDWARE_BYTE = 24
private const val FIRMWARE_BYTE = 28
private const val UPDATE_VERSION_BYTE = 44

/** Null when the reply is not a 64-byte report 0x20; never throws on pad data. */
fun parseFirmwareInfo(report: ByteArray): FirmwareInfo? {
    if (report.size != Limits.USB_FEATURE_FIRMWARE_REPORT_BYTES) return null
    if (report.u8(0) != REPORT_ID_FIRMWARE) return null
    val text =
        String(report, BUILD_DATE_FIRST, BUILD_DATE_END - BUILD_DATE_FIRST, Charsets.US_ASCII)
    return FirmwareInfo(
        hardware = report.u32le(HARDWARE_BYTE),
        firmware = report.u32le(FIRMWARE_BYTE),
        updateVersion = report.u16le(UPDATE_VERSION_BYTE).toUShort(),
        buildDate = text.trimEnd { it == ' ' || it.code == 0 },
    )
}

/** Vibration v2 when the update version is 0x0215 or later (P32). */
fun FirmwareInfo.supportsRumbleV2(): Boolean = updateVersion.toInt() >= UPDATE_VERSION_RUMBLE_V2_MIN
