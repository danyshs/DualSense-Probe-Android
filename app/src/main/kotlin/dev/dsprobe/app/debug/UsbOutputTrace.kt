package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
import dev.dsprobe.pad.u8

/**
 * One group of an output report: its valid-flag bit, the value bytes that bit enables, and the
 * FACTS row.
 */
data class FlagGroup(
    val flagByte: Int,
    val flagBit: Int,
    val label: String,
    val valueOffsets: List<Int>,
    val factsRow: String,
)

private const val FLAG0_BYTE = 1
private const val FLAG1_BYTE = 2
private const val FLAG2_BYTE = 39
private const val REPORT_ID_OUTPUT = 0x02
private const val RIGHT_TRIGGER_FIRST = 11
private const val LEFT_TRIGGER_FIRST = 22

private fun trigger(first: Int) = (first until first + Limits.TRIGGER_EFFECT_BYTES_MAX).toList()

/**
 * The valid-flag bits the app uses (P30-P43) and the bytes each one enables. Data, not code: the
 * trace and its test read the same table.
 */
val OUTPUT_FLAG_GROUPS: List<FlagGroup> = listOf(
    FlagGroup(FLAG0_BYTE, 0x01, "compatible vibration", emptyList(), "P31"),
    FlagGroup(FLAG0_BYTE, 0x02, "motors (3 right, 4 left)", listOf(3, 4), "P31"),
    FlagGroup(FLAG0_BYTE, 0x04, "right trigger effect", trigger(RIGHT_TRIGGER_FIRST), "P39"),
    FlagGroup(FLAG0_BYTE, 0x08, "left trigger effect", trigger(LEFT_TRIGGER_FIRST), "P39"),
    FlagGroup(FLAG0_BYTE, 0x10, "headphone volume", listOf(5), "P33"),
    FlagGroup(FLAG0_BYTE, 0x20, "speaker volume", listOf(6), "P34"),
    FlagGroup(FLAG0_BYTE, 0x40, "mic volume", listOf(7), "P35"),
    FlagGroup(FLAG0_BYTE, 0x80, "audio path", listOf(8), "P36"),
    FlagGroup(FLAG1_BYTE, 0x01, "mute LED", listOf(9), "P37"),
    FlagGroup(FLAG1_BYTE, 0x02, "power save (mic mute)", listOf(10), "P38"),
    FlagGroup(FLAG1_BYTE, 0x04, "lightbar (45 red, 46 green, 47 blue)", listOf(45, 46, 47), "P43"),
    FlagGroup(FLAG1_BYTE, 0x10, "player LEDs", listOf(44), "P42"),
    FlagGroup(FLAG1_BYTE, 0x80, "speaker preamp gain", listOf(38), "P40"),
    FlagGroup(FLAG2_BYTE, 0x04, "vibration v2", emptyList(), "P31"),
)

private val FLAG_BYTE_NAMES = mapOf(
    FLAG0_BYTE to "flag0",
    FLAG1_BYTE to "flag1",
    FLAG2_BYTE to "flag2",
)

/** [offset] went from [old] to [new]. */
data class ByteChange(val offset: Int, val old: Int, val new: Int)

private fun isSet(group: FlagGroup, report: ByteArray): Boolean =
    report.u8(group.flagByte) and group.flagBit != 0

private fun checkReport(report: ByteArray) {
    require(report.size == Limits.USB_OUTPUT_REPORT_BYTES) {
        "output report is ${report.size} bytes"
    }
    require(report.u8(0) == REPORT_ID_OUTPUT) { "output report id" }
}

/**
 * Valid-flag bits set in [report] that no group names: they would be a byte the table cannot
 * explain.
 */
fun unknownFlagBits(report: ByteArray): List<String> {
    checkReport(report)
    val out = ArrayList<String>()
    for ((byteIndex, name) in FLAG_BYTE_NAMES) {
        val groups = OUTPUT_FLAG_GROUPS.filter { it.flagByte == byteIndex }
        val known = groups.fold(0) { acc, g -> acc or g.flagBit }
        val unknown = report.u8(byteIndex) and known.inv()
        if (unknown != 0) out.add("$name byte $byteIndex bits 0x%02x".format(unknown))
    }
    return out
}

/** What the pad was last told, byte by byte, so each send can name exactly what it changed. */
class UsbOutputTrace {
    private val shadow = ByteArray(Limits.USB_OUTPUT_REPORT_BYTES)

    /** Every value byte of every enabled group that differs from what was last sent. */
    fun diff(report: ByteArray): List<ByteChange> {
        checkReport(report)
        val changes = ArrayList<ByteChange>()
        for (group in OUTPUT_FLAG_GROUPS) {
            if (!isSet(group, report)) continue
            for (offset in group.valueOffsets) {
                if (report.u8(offset) != shadow.u8(offset)) {
                    changes.add(ByteChange(offset, shadow.u8(offset), report.u8(offset)))
                }
            }
        }
        check(changes.size <= Limits.USB_OUTPUT_REPORT_BYTES) { "more changes than bytes" }
        return changes
    }

    /** The pad took [report]: its enabled bytes are now what the pad holds. */
    fun commit(report: ByteArray) {
        checkReport(report)
        for (group in OUTPUT_FLAG_GROUPS) {
            if (isSet(group, report)) {
                for (offset in group.valueOffsets) {
                    shadow[offset] =
                        report[offset]
                }
            }
        }
    }

    /**
     * The row for one send, or null for a refresh that changes nothing (a resend every half second
     * would bury the list). A report the pad did not take leaves the shadow alone.
     */
    fun row(send: UsbSend, timeMs: Long): TraceRow? {
        val changes = diff(send.report)
        if (send.ok) commit(send.report)
        if (send.isResend && changes.isEmpty()) return null
        val control = controlOfFields(send.fields, send.isFinal)
        val result = if (send.ok) "sent ok" else "NOT taken by bulkTransfer"
        val detail =
            "request ${send.request}  fields ${send.fields.joinToString(
                "+",
            )}  report 0x02 ${changesText(changes)}  " +
                "flags ${flagsText(send.report)}  ${send.report.size} bytes $result"
        val location = "report 0x02 · ${changesText(
            changes,
        )} · flags ${flagsText(send.report)} · ${send.report.size} bytes · $result"
        return TraceRow(
            timeMs,
            Route.DIRECT_USB,
            control,
            traceLine(control, Route.DIRECT_USB, detail),
            "$control  ${send.request}",
            location,
        )
    }
}

/** One output send as the writer saw it. */
class UsbSend(
    val report: ByteArray,
    val fields: Set<OutputField>,
    val request: String,
    val ok: Boolean,
    val isFinal: Boolean,
    val isResend: Boolean,
)

private fun changesText(changes: List<ByteChange>): String = if (changes.isEmpty()) {
    "no value byte changed"
} else {
    "bytes " +
        changes.joinToString(", ") { "%d: %02x -> %02x".format(it.offset, it.old, it.new) }
}

private fun flagsText(report: ByteArray): String {
    val parts = OUTPUT_FLAG_GROUPS.filter { isSet(it, report) }.map {
        "byte ${it.flagByte} (${FLAG_BYTE_NAMES.getValue(
            it.flagByte,
        )}) bit 0x%02x ${it.label} [${it.factsRow}]".format(it.flagBit)
    }
    val unknown = unknownFlagBits(report)
    return (parts + unknown.map { "UNKNOWN $it" }).joinToString("; ").ifEmpty { "none" }
}

/**
 * The control ID a send belongs to: the one field, "out.release" for the all-off report, else
 * "out.multi".
 */
fun controlOfFields(fields: Set<OutputField>, isFinal: Boolean): String {
    if (isFinal) return "out.release"
    if (fields.size != 1) return "out.multi"
    return when (fields.single()) {
        OutputField.RUMBLE, OutputField.RUMBLE_BOTH_MODES -> "out.rumble"
        OutputField.RIGHT_TRIGGER -> "out.trigger.r2"
        OutputField.LEFT_TRIGGER -> "out.trigger.l2"
        OutputField.LIGHTBAR -> "out.lightbar"
        OutputField.PLAYER_LEDS -> "out.led.player"
        OutputField.MUTE_LED -> "out.led.mute"
        OutputField.AUDIO_ROUTE -> "out.audio.route"
        OutputField.MIC_VOLUME -> "out.audio.mic"
    }
}
