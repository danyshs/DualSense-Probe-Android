package dev.dsprobe.app.debug

import dev.dsprobe.pad.FieldSource
import dev.dsprobe.pad.INPUT_MAP
import dev.dsprobe.pad.Limits

private const val BITS_PER_BYTE = 8
private const val REPORT_NAME = "report 0x01"

/** One physical control: the fields it owns are the INPUT_MAP entries under its [prefixes]. */
private class InputGroup(
    val id: String,
    val title: String,
    val what: String,
    val move: String,
    val prefixes: List<String>,
)

private fun stick(side: String, click: String): InputGroup = InputGroup(
    "in.stick.$side",
    "${side.replaceFirstChar { it.uppercase() }} stick",
    "The $side stick: how far it is pushed each way, and the click when it is pressed straight " +
        "down.",
    "Push the $side stick in every direction, then press it down.",
    listOf("in.stick.$side", click),
)

private fun trigger(side: String): InputGroup = InputGroup(
    "in.trigger.$side",
    "${side.uppercase()} trigger",
    "The ${if (side == "l2") "left" else "right"} trigger: how far it is squeezed, and a " +
        "separate yes or no click flag.",
    "Squeeze ${side.uppercase()} slowly from rest to the end and watch where the flag flips.",
    listOf("in.trigger.$side", "in.button.$side"),
)

private fun finger(point: Int): InputGroup = InputGroup(
    "in.touch.$point",
    "Touchpad, finger ${point + 1}",
    "The ${if (point == 0) "first" else "second"} finger on the touchpad: a contact id, whether " +
        "it is touching, and where it is.",
    if (point == 0) {
        "Touch with one finger, slide it across the pad, lift it, touch again."
    } else {
        "Keep a first finger down, touch with a second, slide it, lift it."
    },
    listOf("in.touch.$point"),
)

private const val MOTION_UNTESTED =
    "Which way each axis points, its sign and its scale are untested: your answer settles them."

// the order is the tour's order
private val INPUT_GROUPS: List<InputGroup> = listOf(
    stick("left", "in.button.l3"),
    stick("right", "in.button.r3"),
    trigger("l2"),
    trigger("r2"),
    InputGroup(
        "in.dpad",
        "D-pad",
        "The D-pad as one number.",
        "Press each D-pad direction, then each corner.",
        listOf("in.dpad"),
    ),
    InputGroup(
        "in.buttons.face",
        "Face buttons",
        "The four face buttons: Square on the left, Cross at the bottom, Circle on the right, " +
            "Triangle at the top.",
        "Press each face button in turn.",
        listOf(
            "in.button.square",
            "in.button.cross",
            "in.button.circle",
            "in.button.triangle",
        ),
    ),
    InputGroup(
        "in.buttons.shoulder",
        "Shoulder buttons",
        "The two shoulder buttons above the triggers, L1 and R1.",
        "Press L1, then R1.",
        listOf("in.button.l1", "in.button.r1"),
    ),
    InputGroup(
        "in.buttons.system",
        "System buttons",
        "The small buttons: Create left of the touchpad, Options right of it, the PS logo in " +
            "the middle, the whole touchpad pressed down, and the microphone Mute.",
        "Press each of them in turn; press the touchpad down until it clicks.",
        listOf(
            "in.button.create",
            "in.button.options",
            "in.button.ps",
            "in.button.touchpad_click",
            "in.button.mute",
        ),
    ),
    finger(0),
    finger(1),
    InputGroup(
        "in.gyro",
        "Gyroscope",
        "How fast the pad turns about each of three axes; 0 = not turning. $MOTION_UNTESTED",
        "Turn the pad slowly in the air, one direction at a time, and see which bar moves.",
        listOf("in.gyro"),
    ),
    InputGroup(
        "in.accel",
        "Accelerometer",
        "The push or gravity along each of three axes; a pad lying still shows gravity on one " +
            "axis. $MOTION_UNTESTED",
        "Tip the pad so each side points down in turn and see which bar moves.",
        listOf("in.accel"),
    ),
    InputGroup(
        "in.battery",
        "Battery",
        "The battery level and the charging state, each a 4-bit number.",
        "Nothing to press: it drifts as the battery drains or charges, so just watch it. A USB " +
            "cable makes a charging change hard to cause.",
        listOf("in.battery"),
    ),
    InputGroup(
        "in.sequence",
        "Report counter",
        "A counter that goes up by one with every report the pad sends.",
        "Nothing to press: it counts by itself while Direct USB reports arrive.",
        listOf("in.sequence"),
    ),
    InputGroup(
        "in.headset",
        "Headphone and headset flags",
        "Two flags for the pad's headphone jack: whether something is plugged in, and whether " +
            "it has a microphone.",
        "Plug headphones into the pad, then a headset that has a microphone, then pull them out.",
        listOf("in.headphones_in", "in.mic_in"),
    ),
)

private fun sourcesOf(group: InputGroup): List<FieldSource> =
    INPUT_MAP.filter { src -> group.prefixes.any { controlUnder(src.id, it) } }

private fun placeLine(src: FieldSource, label: String): String =
    "$label: $REPORT_NAME · ${placeOfField(src)}"

private fun groupCard(group: InputGroup): EndpointCard {
    val sources = sourcesOf(group)
    check(sources.isNotEmpty()) { "group ${group.id} owns no field" }
    val guesses = sources.map { checkNotNull(INPUT_GUESSES[it.id]) { "no guess for ${it.id}" } }
    val rows = sources.map { it.factsRow }.distinct()
    val lines = guesses.map { "${it.label}: ${it.line}" }
    val hasAndroid = guesses.any { it.androidLine != null }
    val androidLines = guesses.map { "${it.label}: ${it.androidLine ?: it.line}" }
    return EndpointCard(
        id = group.id,
        kind = CardKind.INPUT_FIELD,
        title = group.title,
        where = sources.zip(guesses) { src, g -> placeLine(src, g.label) },
        guess = cardText(group.what, lines, group.move),
        factsRow = factsRowText(rows),
        confidence = confidenceOfRows(rows),
        tryIt = TryIt.Reason(HAND_REASON),
        fields = sources.zip(guesses) { src, g -> CardField(src.id, g.label) },
        listens = group.prefixes,
        androidGuess = if (hasAndroid) cardText(group.what, androidLines, group.move) else null,
    )
}

/**
 * One card per physical control. Every INPUT_MAP entry is on exactly one of them; a field no group
 * owns, or two groups own, fails here at start.
 */
fun inputGroupCards(): List<EndpointCard> {
    val cards = INPUT_GROUPS.map(::groupCard)
    val owned = cards.flatMap { card -> card.fields.map { it.id } }
    check(owned.sorted() == INPUT_MAP.map { it.id }.sorted()) { "a field is in no group or two" }
    return cards
}

/**
 * A stretch of report 0x01 no INPUT_MAP entry claims: whole bytes [first]..[last], or part of one
 * byte ([mask] set).
 */
data class UnclaimedRange(val first: Int, val last: Int, val mask: Int)

/**
 * Every byte or bit of report 0x01 (byte 0 is the report ID) that INPUT_MAP leaves out, whole free
 * bytes merged into ranges.
 */
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

private const val MIC_MUTED_GUESS =
    "Bit 2 is the microphone-muted flag; the other bits here are not decoded. 1 = muted, 0 = " +
        "not. Press Mute and let go, plug a headset in, and watch which bits move."
private const val UNKNOWN_TRY =
    "Nobody has decoded this yet. Try: tilt, press every button, touch the pad, " +
        "plug headphones. Watch the value."

private fun rangeCard(range: UnclaimedRange): EndpointCard {
    val bytes = if (range.first == range.last) {
        "byte ${range.first}"
    } else {
        "bytes ${range.first}-${range.last}"
    }
    val bits = if (range.mask == BYTE_ALL) "" else " · mask 0x%02x".format(range.mask)
    val id = "in.unclaimed.${range.first}-${range.last}"
    val row = UNCLAIMED_FACTS_ROW[range.first]
    val where = listOf("$REPORT_NAME · $bytes$bits")
    val count = range.last - range.first + 1
    val tryIt = TryIt.Reason(HAND_REASON)
    return if (row == null) {
        EndpointCard(
            id = id, kind = CardKind.INPUT_RANGE, title = "Unknown, $bytes$bits", where = where,
            guess = "Nobody has decoded $bytes$bits yet. $UNKNOWN_TRY", factsRow = null,
            confidence = Confidence.UNKNOWN, tryIt = tryIt, rawByte = range.first,
            rawByteCount = count, rawMask = range.mask,
        )
    } else {
        EndpointCard(
            id = id, kind = CardKind.INPUT_RANGE, title = "Byte 54, mic-muted bit", where = where,
            guess = MIC_MUTED_GUESS, factsRow = row,
            confidence = confidenceOfRows(listOf(row)), tryIt = tryIt, rawByte = range.first,
            rawByteCount = count, rawMask = range.mask,
        )
    }
}

fun rangeCards(): List<EndpointCard> = unclaimedRanges().map(::rangeCard)
