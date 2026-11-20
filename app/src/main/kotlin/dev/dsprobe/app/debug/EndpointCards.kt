package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.FieldSource
import dev.dsprobe.pad.INPUT_MAP
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField

/** How sure FACTS.md is about a card's meaning. */
enum class Confidence { PROVEN, DOCUMENTED, UNKNOWN }

/** Which family of endpoints a card belongs to. */
enum class CardKind { INPUT_FIELD, INPUT_RANGE, OUTPUT, FEATURE, ANDROID }

/**
 * One stop of the endpoint tour. [where] is the exact location, [probably] the plain-language guess,
 * [factsRow] the FACTS.md row both come from (null: no row, so [confidence] is UNKNOWN).
 * [rawByte] and [rawMask] say which live byte of report 0x01 an UNKNOWN input card shows; -1 means none.
 */
data class EndpointCard(
    val id: String,
    val kind: CardKind,
    val where: String,
    val probably: String,
    val factsRow: String?,
    val confidence: Confidence,
    val rawByte: Int = -1,
    val rawByteCount: Int = 0,
    val rawMask: Int = BYTE_ALL,
    /** Control IDs (or prefixes) whose trace rows this card listens to while it is open. */
    val listens: List<String> = emptyList(),
)

private const val BYTE_ALL = 0xff
const val UNKNOWN_PROBABLY = "Unknown. Watch whether it changes when you press, move or tilt something."

/** FACTS "Confirmed" text to a tag: only a plain yes is Proven; a row with a "not yet", a "no" or a mix is Documented. */
fun confidenceFromConfirmed(confirmed: String): Confidence {
    val text = confirmed.trim().lowercase()
    val plainYes = text.startsWith("yes") && "not yet" !in text && "no (" !in text
    return if (plainYes) Confidence.PROVEN else Confidence.DOCUMENTED
}

/** The FACTS rows the tour cites, with the tag their Confirmed column gives. The test reads FACTS.md and compares. */
val ROW_CONFIDENCE: Map<String, Confidence> = mapOf(
    "P10" to Confidence.PROVEN, "P11" to Confidence.PROVEN, "P12" to Confidence.PROVEN, "P13" to Confidence.PROVEN,
    "P14" to Confidence.PROVEN, "P15" to Confidence.PROVEN, "P16" to Confidence.DOCUMENTED, "P17" to Confidence.PROVEN,
    "P18" to Confidence.DOCUMENTED, "P19" to Confidence.DOCUMENTED,
    "P31" to Confidence.PROVEN, "P35" to Confidence.PROVEN, "P36" to Confidence.PROVEN, "P37" to Confidence.PROVEN,
    "P39" to Confidence.PROVEN, "P42" to Confidence.PROVEN, "P43" to Confidence.PROVEN, "P45" to Confidence.PROVEN,
    "P51" to Confidence.PROVEN,
    "P71" to Confidence.PROVEN, "P72" to Confidence.DOCUMENTED, "P73" to Confidence.DOCUMENTED, "P74" to Confidence.DOCUMENTED,
    "P75" to Confidence.DOCUMENTED, "P76" to Confidence.PROVEN, "P77" to Confidence.DOCUMENTED, "P78" to Confidence.DOCUMENTED,
    "P80" to Confidence.PROVEN,
)

// what each cited row says the bytes probably are, in plain words
private val MEANING: Map<String, String> = mapOf(
    "P10" to "a stick axis, 0-255, centre about 128",
    "P11" to "an analog trigger, 0-255",
    "P12" to "a report counter that goes up by one per report",
    "P13" to "the D-pad (low nibble) or a face button (Square, Cross, Circle, Triangle)",
    "P14" to "a shoulder, trigger click, Create, Options or stick-click button",
    "P15" to "the PS, touchpad click or Mute button",
    "P16" to "a gyroscope or accelerometer axis, signed 16-bit; which axis is which is untested",
    "P17" to "a touchpad finger field: contact id, not-touching flag or a position",
    "P18" to "battery level (low nibble) or charging state (high nibble); the mapping is untested",
    "P19" to "the headphones-in, mic-in or mic-muted flags",
    "P31" to "the two rumble motors",
    "P35" to "the microphone volume",
    "P36" to "where audio is sent: headphones or speaker",
    "P37" to "the mute LED: off, on or pulsing",
    "P39" to "a trigger effect; mode 01 is resistance",
    "P42" to "the five player LEDs",
    "P43" to "the lightbar colour",
    "P45" to "the all-off report: rumble, triggers, lightbar and LEDs back to nothing",
    "P51" to "the firmware report: build date, hardware, firmware and update versions",
    "P71" to "the pad's buttons arriving as key codes",
    "P72" to "the Mute button: no key event arrives, the LED toggles on the pad itself",
    "P73" to "the sticks, triggers and D-pad as joystick axes",
    "P74" to "the touchpad as a mouse pointer and click",
    "P75" to "the gyroscope and accelerometer, in SI units",
    "P76" to "a rumble motor, amplitude 1-255",
    "P77" to "a light: the RGB lightbar or a player LED; changes fail silently on USB",
    "P78" to "battery state; fails on USB",
    "P80" to "the pad's microphone as a USB headset input; no output device exists",
)

private fun known(id: String, kind: CardKind, where: String, row: String, rawByte: Int = -1, listens: List<String> = emptyList()): EndpointCard {
    val confidence = checkNotNull(ROW_CONFIDENCE[row]) { "no confidence for $row" }
    val meaning = checkNotNull(MEANING[row]) { "no meaning for $row" }
    return EndpointCard(id, kind, where, "Probably: $meaning (FACTS $row).", row, confidence, rawByte, listens = listens)
}

private fun inputCard(src: FieldSource): EndpointCard {
    val place = "report 0x%02x · %s".format(src.reportId, placeOfField(src))
    return known(src.id, CardKind.INPUT_FIELD, place, src.factsRow, listens = listOf(src.id))
}

/** A stretch of report 0x01 no INPUT_MAP entry claims: whole bytes [first]..[last], or part of one byte ([mask] set). */
data class UnclaimedRange(val first: Int, val last: Int, val mask: Int)

/** Every byte or bit of report 0x01 (byte 0 is the report ID) that INPUT_MAP leaves out, whole free bytes merged into ranges. */
fun unclaimedRanges(map: List<FieldSource> = INPUT_MAP): List<UnclaimedRange> {
    val claimed = IntArray(Limits.USB_INPUT_REPORT_BYTES)
    for (src in map) {
        if (src.widthBits >= BITS_PER_BYTE * 2) {
            claimed[src.byteOffset] = BYTE_ALL
            claimed[src.byteOffset + 1] = BYTE_ALL
        } else {
            claimed[src.byteOffset] = claimed[src.byteOffset] or (src.bitMask ?: BYTE_ALL)
        }
    }
    val out = ArrayList<UnclaimedRange>()
    var byte = 1
    while (byte < Limits.USB_INPUT_REPORT_BYTES) {
        val free = BYTE_ALL and claimed[byte].inv()
        if (free == 0) {
            byte += 1
        } else if (free != BYTE_ALL) {
            out.add(UnclaimedRange(byte, byte, free))
            byte += 1
        } else {
            var last = byte
            while (last + 1 < Limits.USB_INPUT_REPORT_BYTES && claimed[last + 1] == 0) last += 1
            out.add(UnclaimedRange(byte, last, BYTE_ALL))
            byte = last + 1
        }
    }
    check(out.size <= Limits.USB_INPUT_REPORT_BYTES) { "more ranges than bytes" }
    return out
}

// a free range FACTS names anyway: byte 54 bit 2 is "mic muted" (P19)
private val UNCLAIMED_FACTS_ROW: Map<Int, String> = mapOf(54 to "P19")

private fun rangeCard(range: UnclaimedRange): EndpointCard {
    val bytes = if (range.first == range.last) "byte ${range.first}" else "bytes ${range.first}-${range.last}"
    val bits = if (range.mask == BYTE_ALL) "" else " · mask 0x%02x".format(range.mask)
    val id = "in.unclaimed.${range.first}-${range.last}"
    val row = UNCLAIMED_FACTS_ROW[range.first]
    val count = range.last - range.first + 1
    if (row == null) return EndpointCard(id, CardKind.INPUT_RANGE, "report 0x01 · $bytes$bits", UNKNOWN_PROBABLY, null, Confidence.UNKNOWN, range.first, count, range.mask)
    return known(id, CardKind.INPUT_RANGE, "report 0x01 · $bytes$bits", row, range.first).copy(rawByteCount = count, rawMask = range.mask)
}

private fun outputRow(field: OutputField): String = when (field) {
    OutputField.RUMBLE -> "P31"
    OutputField.RUMBLE_BOTH_MODES -> "P45"
    OutputField.RIGHT_TRIGGER, OutputField.LEFT_TRIGGER -> "P39"
    OutputField.LIGHTBAR -> "P43"
    OutputField.PLAYER_LEDS -> "P42"
    OutputField.MUTE_LED -> "P37"
    OutputField.AUDIO_ROUTE -> "P36"
    OutputField.MIC_VOLUME -> "P35"
}

// the trace control a send of this field shows under (see controlOfFields)
private fun outputControl(field: OutputField): String = when (field) {
    OutputField.RUMBLE -> "out.rumble"
    OutputField.RUMBLE_BOTH_MODES -> "out.release"
    OutputField.RIGHT_TRIGGER -> "out.trigger.r2"
    OutputField.LEFT_TRIGGER -> "out.trigger.l2"
    OutputField.LIGHTBAR -> "out.lightbar"
    OutputField.PLAYER_LEDS -> "out.led.player"
    OutputField.MUTE_LED -> "out.led.mute"
    OutputField.AUDIO_ROUTE -> "out.audio.route"
    OutputField.MIC_VOLUME -> "out.audio.mic"
}

private fun outputCard(field: OutputField): EndpointCard =
    known("out.${field.name.lowercase()}", CardKind.OUTPUT, "output report 0x02 · field ${field.name}", outputRow(field), listens = listOf(outputControl(field)))

private class AndroidSpec(val id: String, val where: String, val row: String, val listens: List<String>)

private val ANDROID_CARDS: List<AndroidSpec> = listOf(
    AndroidSpec("android.keys", "Activity.dispatchKeyEvent · KeyEvent key codes", "P71", listOf("in.button", "in.key")),
    AndroidSpec("android.key.mute", "Activity.dispatchKeyEvent · no key code for Mute", "P72", listOf("in.key")),
    AndroidSpec("android.axes", "Activity.dispatchGenericMotionEvent · MotionEvent axes", "P73", listOf("in.stick", "in.trigger", "in.dpad")),
    AndroidSpec("android.touchpad", "Activity.dispatchGenericMotionEvent · touchpad pointer", "P74", listOf("in.touch", "in.button.touchpad_click")),
    AndroidSpec("android.sensors", "InputDevice.getSensorManager() · gyroscope, accelerometer", "P75", listOf("in.gyro", "in.accel")),
    AndroidSpec("android.vibrator.0", "VibratorManager vibrator id 0", "P76", listOf("out.rumble")),
    AndroidSpec("android.vibrator.1", "VibratorManager vibrator id 1", "P76", listOf("out.rumble")),
    AndroidSpec("android.light.indicator", "LightsManager light 'indicator'", "P77", listOf("out.lightbar")),
    AndroidSpec("android.light.player", "LightsManager lights 'player-1' to 'player-5'", "P77", listOf("out.led.player")),
    AndroidSpec("android.battery", "InputDevice.getBatteryState()", "P78", emptyList()),
    AndroidSpec("android.audio.in", "AudioManager.getDevices(GET_DEVICES_INPUTS) · USB headset", "P80", emptyList()),
    AndroidSpec("android.audio.out", "AudioManager.getDevices(GET_DEVICES_OUTPUTS) · the pad", "P80", emptyList()),
)

/** Every endpoint the tour visits, in order: input fields, free report bytes, outputs, firmware, then Android's APIs. Bounded by [AppLimits.TOUR_CARDS_MAX]. */
fun buildEndpointCards(): List<EndpointCard> {
    val cards = ArrayList<EndpointCard>()
    for (src in INPUT_MAP) cards.add(inputCard(src))
    for (range in unclaimedRanges()) cards.add(rangeCard(range))
    for (field in OutputField.entries) cards.add(outputCard(field))
    cards.add(known("feature.0x20", CardKind.FEATURE, "feature report 0x20 · 64 bytes", "P51", listens = listOf("in.feature")))
    for (spec in ANDROID_CARDS) cards.add(known(spec.id, CardKind.ANDROID, spec.where, spec.row, listens = spec.listens))
    check(cards.size <= AppLimits.TOUR_CARDS_MAX) { "more cards than the limit: ${cards.size}" }
    check(cards.map { it.id }.toSet().size == cards.size) { "a card id repeats" }
    return cards
}

private const val BITS_PER_BYTE = 8
