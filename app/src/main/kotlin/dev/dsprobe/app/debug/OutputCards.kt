package dev.dsprobe.app.debug

import dev.dsprobe.pad.OutputField

private const val OUTPUT_REPORT = "output report 0x02"

/** One output field on a card: its [label] on the card's lines and its [place] in the report. */
private class OutField(val field: OutputField, val label: String, val place: String)

/** One output card: the fields it covers, in the order its lines list them. */
private class OutputSpec(
    val id: String,
    val title: String,
    val fields: List<OutField>,
    val what: String,
    val lines: List<String>,
    val move: String,
    val rows: List<String>,
    val listens: List<String>,
    val tryIt: TryIt,
)

private fun fieldId(field: OutputField): String = "out.${field.name.lowercase()}"

private val OUTPUT_SPECS: List<OutputSpec> = listOf(
    OutputSpec(
        "out.rumble", "Rumble motors",
        listOf(
            OutField(OutputField.RUMBLE, "Rumble motors", "bytes 3-4"),
        ),
        "The two vibration motors, left and right.",
        listOf("0 = still, 255 = full strength for each."),
        "Tap Try it and feel left, right, both or a pattern.",
        listOf("P31"), listOf("out.rumble"), TryIt.Action(TryKind.RUMBLE),
    ),
    OutputSpec(
        "out.rumble_both_modes", "Everything off",
        listOf(OutField(OutputField.RUMBLE_BOTH_MODES, "Everything off", "the whole report")),
        "The all-off report: rumble in both modes, both triggers, the lightbar, the LEDs and the " +
            "mute LED back to nothing.",
        listOf("Every value 0."), "Tap Try it, then check that everything on the pad went quiet.",
        listOf("P45"), listOf("out.release"), TryIt.Action(TryKind.ALL_OFF),
    ),
    OutputSpec(
        "out.triggers", "Trigger resistance",
        listOf(
            OutField(OutputField.RIGHT_TRIGGER, "R2", "bytes 11-21"),
            OutField(OutputField.LEFT_TRIGGER, "L2", "bytes 22-32"),
        ),
        "A push-back effect on each trigger: mode 1 resists from a start position with a force.",
        listOf(
            "Start position 0-255 and force 0-255; all zeros = off. The bytes 01 30 c0 gave " +
                "clear resistance.",
        ),
        "Tap Try it for R2 or L2, then squeeze that trigger.",
        listOf("P39"), listOf("out.trigger"), TryIt.Action(TryKind.TRIGGERS),
    ),
    OutputSpec(
        "out.lightbar", "Lightbar colour",
        listOf(
            OutField(OutputField.LIGHTBAR, "Lightbar colour", "bytes 45-47"),
        ),
        "The red, green and blue of the light strip beside the touchpad.",
        listOf("0 = dark, 255 = full for each of the three."),
        "Tap Try it and watch the strip change colour.",
        listOf("P43"), listOf("out.lightbar"), TryIt.Action(TryKind.LIGHTBAR),
    ),
    OutputSpec(
        "out.player_leds", "Player LEDs",
        listOf(
            OutField(OutputField.PLAYER_LEDS, "Player LEDs", "byte 44"),
        ),
        "The five small lights under the touchpad.",
        listOf("Five bits: LED 1 is bit 0, up to LED 5 as bit 4 (0x10)."),
        "Tap the dots to light each LED in turn.",
        listOf("P42"), listOf("out.led.player"), TryIt.Action(TryKind.PLAYER_LEDS),
    ),
    OutputSpec(
        "out.mute_led", "Mute LED",
        listOf(
            OutField(OutputField.MUTE_LED, "Mute LED", "byte 9"),
        ),
        "The light on the Mute button.", listOf("0 = off, 1 = on, 2 = pulsing."),
        "Tap Try it and pick off, on or pulse.",
        listOf("P37"), listOf("out.led.mute"), TryIt.Action(TryKind.MUTE_LED),
    ),
    OutputSpec(
        "out.audio", "Audio route and microphone volume",
        listOf(
            OutField(OutputField.AUDIO_ROUTE, "route", "byte 8, bits 4-5"),
            OutField(OutputField.MIC_VOLUME, "microphone volume", "byte 7"),
        ),
        "Which output the audio goes to (the headphones or the speaker), and how loud the pad's " +
            "microphone is.",
        listOf(
            "route: 0 = both channels to the headphones with the speaker muted; 3 = right " +
                "channel to the speaker with the headphones muted.",
            "microphone volume: 0 to 0x40.",
        ),
        "Set by the speaker, headphones and microphone cards.",
        listOf("P35", "P36"), listOf("out.audio"), TryIt.Reason(AUDIO_STEP_REASON),
    ),
)

private fun outputCard(spec: OutputSpec): EndpointCard {
    val where = if (spec.fields.size == 1) {
        listOf("$OUTPUT_REPORT · ${spec.fields.single().place}")
    } else {
        spec.fields.map { "${it.label}: $OUTPUT_REPORT · ${it.place}" }
    }
    return EndpointCard(
        id = spec.id,
        kind = CardKind.OUTPUT,
        title = spec.title,
        where = where,
        guess = cardText(spec.what, spec.lines, spec.move),
        factsRow = factsRowText(spec.rows),
        confidence = confidenceOfRows(spec.rows),
        tryIt = spec.tryIt,
        fields = spec.fields.map { CardField(fieldId(it.field), it.label) },
        listens = spec.listens,
    )
}

/**
 * One card per output you can set, merging fields that are one thing. Every OutputField is on
 * exactly one card; one that is on none, or two, fails here at start.
 */
fun outputCards(): List<EndpointCard> {
    val cards = OUTPUT_SPECS.map(::outputCard)
    val covered = cards.flatMap { card -> card.fields.map { it.id } }
    check(covered.sorted() == OutputField.entries.map(::fieldId).sorted()) {
        "an output field is on no card or two"
    }
    return cards
}

private class AudioText(val title: String, val what: String, val ends: String, val move: String)

private fun audioCard(
    id: String,
    where: String,
    text: AudioText,
    row: String,
    tryIt: TryIt,
): EndpointCard = EndpointCard(
    id = id,
    kind = CardKind.AUDIO,
    title = text.title,
    where = listOf(where),
    guess = cardText(text.what, listOf(text.ends), text.move),
    factsRow = row,
    confidence = confidenceOfRows(listOf(row)),
    tryIt = tryIt,
)

private const val NO_OUTPUT_DEVICE =
    "Android lists no output device for the pad (FACTS P80), and Direct USB audio needs the " +
        "audio step, not built yet."

fun audioCards(): List<EndpointCard> = listOf(
    audioCard(
        "audio.mic",
        "USB audio interface 2 · IN endpoint 0x82",
        AudioText(
            "Microphone",
            "The pad's built-in microphone, two channels at 48 kHz.",
            "Loud input clips at full scale.",
            "Tap Try it and talk or tap near the pad for 5 seconds; the level meter moves.",
        ),
        "P62",
        TryIt.Action(TryKind.MIC),
    ),
    audioCard(
        "audio.speaker",
        "USB audio interface 1 · OUT endpoint 0x01 · channels 1-2",
        AudioText(
            "Speaker tone",
            "The pad's small speaker, fed a 440 Hz tone.",
            "A one-second tone, loud.",
            "Tap Try it and listen to the pad.",
        ),
        "P61",
        TryIt.Action(TryKind.SPEAKER),
    ),
    audioCard(
        "audio.headphones",
        "USB audio interface 1 · OUT endpoint 0x01 · channels 1-2",
        AudioText(
            "Headphones tone",
            "The pad's headphone jack, fed a quiet 440 Hz tone.",
            "A one-second tone, kept quiet on purpose.",
            "Plug headphones into the pad, tap Try it and listen.",
        ),
        "P61",
        TryIt.Action(TryKind.HEADPHONES),
    ),
    audioCard(
        "audio.haptics",
        "USB audio interface 1 · OUT endpoint 0x01 · channels 3-4",
        AudioText(
            "HD haptics",
            "A buzz driven as sound on channels 3 and 4 (150 Hz was felt).",
            "It plays only while compatible rumble is off.",
            "Feel the pad while it plays.",
        ),
        "P44",
        TryIt.Reason(NO_OUTPUT_DEVICE),
    ),
)

val FIRMWARE_CARD = EndpointCard(
    id = "feature.0x20",
    kind = CardKind.FEATURE,
    title = "Firmware report",
    where = listOf("feature report 0x20 · 64 bytes"),
    guess = "The build date (bytes 1-19, text), hardware version (24-27), firmware version " +
        "(28-31) and update version (44-45). Read once when Direct USB claims the pad; the log " +
        "header shows it.",
    factsRow = "P51",
    confidence = confidenceOfRows(listOf("P51")),
    tryIt = TryIt.Reason("Read once when Direct USB claims the pad; there is nothing to fire."),
    listens = listOf("in.feature"),
)
