package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.Dpad
import dev.dsprobe.pad.FieldSource
import dev.dsprobe.pad.INPUT_MAP
import dev.dsprobe.pad.Limits

private const val SEQUENCE_ID = "in.sequence"
private const val BYTE_MASK_ALL = 0xff
private const val WORD_BITS = 16

/** Which function in InputParser.kt reads a field, by ID prefix: first match wins. */
private val PARSER_READERS: List<Pair<String, String>> = listOf(
    "in.stick." to "parseUsbInput leftX..rightY",
    "in.trigger." to "parseUsbInput l2, r2",
    "in.sequence" to "parseUsbInput sequence",
    "in.dpad" to "dpadOf",
    "in.button." to "heldButtons",
    "in.gyro." to "vec3 / s16le (gyro)",
    "in.accel." to "vec3 / s16le (accel)",
    "in.touch." to "touchAt, touchXPx, touchYPx",
    "in.battery." to "parseUsbInput battery",
    "in.headphones_in" to "parseUsbInput headphonesIn",
    "in.mic_in" to "parseUsbInput micIn",
)

/** The parser function for [id]; fails on an ID the table does not cover, so a new INPUT_MAP entry cannot go unnamed. */
fun parserReaderOf(id: String): String {
    val hit = PARSER_READERS.firstOrNull { id.startsWith(it.first) }
    return "InputParser.kt " + checkNotNull(hit) { "no parser function named for $id" }.second
}

private fun u8(r: ByteArray, i: Int): Int = r[i].toInt() and BYTE_MASK_ALL

/** The raw bits of a field: the masked byte, or the whole little-endian word. */
fun rawOfField(src: FieldSource, r: ByteArray): Int {
    require(r.size == Limits.USB_INPUT_REPORT_BYTES) { "input report is ${r.size} bytes" }
    if (src.widthBits == WORD_BITS) return u8(r, src.byteOffset) or (u8(r, src.byteOffset + 1) shl 8)
    return u8(r, src.byteOffset) and (src.bitMask ?: BYTE_MASK_ALL)
}

/** The value the parser makes of it: shifted down to bit 0, or a signed word. */
fun decodedOfField(src: FieldSource, r: ByteArray): Int {
    val raw = rawOfField(src, r)
    if (src.widthBits == WORD_BITS) return raw.toShort().toInt()
    return raw ushr Integer.numberOfTrailingZeros(src.bitMask ?: BYTE_MASK_ALL)
}

/** Where the field is: "byte 8 mask 0x20" or "bytes 16-17". */
fun placeOfField(src: FieldSource): String = when {
    src.widthBits == WORD_BITS -> "bytes ${src.byteOffset}-${src.byteOffset + 1}"
    src.bitMask != null -> "byte ${src.byteOffset} mask 0x%02x".format(src.bitMask)
    else -> "byte ${src.byteOffset}"
}

private fun hexOfRaw(src: FieldSource, raw: Int): String = if (src.widthBits == WORD_BITS) "0x%04x".format(raw) else "0x%02x".format(raw)

private fun dpadName(v: Int): String = when {
    v in 0 until Dpad.NONE.ordinal -> Dpad.entries[v].name
    v == Dpad.NONE.ordinal -> Dpad.NONE.name
    else -> "undefined"
}

private fun decodedText(src: FieldSource, v: Int): String = when {
    src.id == "in.dpad" -> "$v (${dpadName(v)})"
    src.id.endsWith(".active") -> "$v (1 = not touching)"
    else -> v.toString()
}

private fun headlineFor(src: FieldSource, before: Int, after: Int): String {
    if (src.id.startsWith("in.button.")) return controlLabel(src.id) + "  " + (if (after != 0) "pressed" else "released")
    return src.id + "  " + decodedText(src, before) + " → " + decodedText(src, after)
}

private fun locationFor(src: FieldSource, prev: ByteArray, cur: ByteArray): String =
    "report 0x%02x".format(src.reportId) + " · " + placeOfField(src) +
        " · raw " + hexOfRaw(src, rawOfField(src, prev)) + " → " + hexOfRaw(src, rawOfField(src, cur)) +
        " · FACTS " + src.factsRow + " · " + parserReaderOf(src.id)

private fun rowFor(src: FieldSource, prev: ByteArray, cur: ByteArray, timeMs: Long): TraceRow {
    val detail = "report 0x%02x ${placeOfField(src)}  raw ${hexOfRaw(src, rawOfField(src, prev))} -> ${hexOfRaw(src, rawOfField(src, cur))}".format(src.reportId) +
        "  decoded ${decodedText(src, decodedOfField(src, prev))} -> ${decodedText(src, decodedOfField(src, cur))}" +
        "  FACTS ${src.factsRow}  read by ${parserReaderOf(src.id)}"
    val headline = headlineFor(src, decodedOfField(src, prev), decodedOfField(src, cur))
    return TraceRow(timeMs, Route.DIRECT_USB, src.id, traceLine(src.id, Route.DIRECT_USB, detail), headline, locationFor(src, prev, cur))
}

/** One row per field that differs between two reports, read straight off INPUT_MAP. The sequence counter changes every report and is not a row. */
fun usbInputRows(prev: ByteArray, cur: ByteArray, timeMs: Long): List<TraceRow> {
    require(prev.size == Limits.USB_INPUT_REPORT_BYTES && cur.size == Limits.USB_INPUT_REPORT_BYTES) { "input reports are 64 bytes" }
    val rows = ArrayList<TraceRow>()
    for (src in INPUT_MAP) {
        if (src.id == SEQUENCE_ID) continue
        if (rawOfField(src, prev) != rawOfField(src, cur)) rows.add(rowFor(src, prev, cur, timeMs))
    }
    check(rows.size <= INPUT_MAP.size) { "more rows than fields" }
    return rows
}
