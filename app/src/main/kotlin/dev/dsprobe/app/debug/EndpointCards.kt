package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.output.ledMaskText
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.androidSourceOf
import dev.dsprobe.pad.FieldSource
import dev.dsprobe.pad.INPUT_MAP
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField

/** How sure FACTS.md is about a card's meaning. */
enum class Confidence { PROVEN, DOCUMENTED, UNKNOWN }

/** Which family of endpoints a card belongs to. */
enum class CardKind { INPUT_FIELD, INPUT_RANGE, OUTPUT, FEATURE, ANDROID, AUDIO }

/**
 * One stop of the guided test. [title] is the plain name, [where] the exact location, [guess] the
 * per-field
 * paragraph, [factsRow] the FACTS.md row it comes from (null: no row, so [confidence] is UNKNOWN).
 * [rawByte], [rawByteCount] and [rawMask] say which live bytes of report 0x01 a card shows; -1
 * means none.
 * [tryIt] is what the big button fires, or the reason there is nothing to fire.
 */
data class EndpointCard(
    val id: String,
    val kind: CardKind,
    val title: String,
    val where: String,
    val guess: String,
    val factsRow: String?,
    val confidence: Confidence,
    val tryIt: TryIt,
    val rawByte: Int = -1,
    val rawByteCount: Int = 0,
    val rawMask: Int = BYTE_ALL,
    /** Control IDs (or prefixes) whose trace rows this card listens to while it is open. */
    val listens: List<String> = emptyList(),
    /** The guess in Android mode's units, when they differ from Direct USB's. */
    val androidGuess: String? = null,
) {
    /** A button the user presses by hand: no Try it, and no reaction question. */
    val isButton: Boolean get() = id.startsWith("in.button.") || id == "android.keys" ||
        id == "android.key.mute"
}

private const val BYTE_ALL = 0xff
private const val BITS_PER_BYTE = 8

const val HAND_REASON =
    "Nothing to fire: this one is yours to move. Use the control and watch the value."
const val AUDIO_STEP_REASON = "Direct USB audio needs the audio step, not built yet."

/**
 * FACTS "Confirmed" text to a tag: only a plain yes is Proven; a row with a "not yet", a "no" or a
 * mix is Documented.
 */
fun confidenceFromConfirmed(confirmed: String): Confidence {
    val text = confirmed.trim().lowercase()
    val plainYes = text.startsWith("yes") && "not yet" !in text && "no (" !in text
    return if (plainYes) Confidence.PROVEN else Confidence.DOCUMENTED
}

/**
 * The FACTS rows the guided test cites, with the tag their Confirmed column gives. The test reads
 * FACTS.md and compares.
 */
val ROW_CONFIDENCE: Map<String, Confidence> = mapOf(
    "P10" to Confidence.PROVEN, "P11" to Confidence.PROVEN, "P12" to Confidence.PROVEN,
    "P13" to Confidence.PROVEN,
    "P14" to Confidence.PROVEN, "P15" to Confidence.PROVEN, "P16" to Confidence.DOCUMENTED,
    "P17" to Confidence.PROVEN,
    "P18" to Confidence.DOCUMENTED, "P19" to Confidence.DOCUMENTED,
    "P31" to Confidence.PROVEN, "P35" to Confidence.PROVEN, "P36" to Confidence.PROVEN,
    "P37" to Confidence.PROVEN,
    "P39" to Confidence.PROVEN, "P42" to Confidence.PROVEN, "P43" to Confidence.PROVEN,
    "P44" to Confidence.PROVEN,
    "P45" to Confidence.PROVEN, "P51" to Confidence.PROVEN, "P61" to Confidence.PROVEN,
    "P62" to Confidence.PROVEN,
    "P71" to Confidence.PROVEN, "P72" to Confidence.DOCUMENTED, "P73" to Confidence.DOCUMENTED,
    "P74" to Confidence.DOCUMENTED,
    "P75" to Confidence.DOCUMENTED, "P76" to Confidence.PROVEN, "P77" to Confidence.DOCUMENTED,
    "P78" to Confidence.DOCUMENTED,
    "P80" to Confidence.PROVEN,
)

private fun confidenceOf(row: String): Confidence = checkNotNull(ROW_CONFIDENCE[row]) {
    "no confidence for $row"
}

private fun inputCard(src: FieldSource): EndpointCard {
    val guess = checkNotNull(INPUT_GUESSES[src.id]) { "no guess for ${src.id}" }
    val place = "report 0x%02x · %s".format(src.reportId, placeOfField(src))
    val tryIt = TryIt.Reason(HAND_REASON)
    return EndpointCard(
        src.id, CardKind.INPUT_FIELD, guess.title, place, guess.text, src.factsRow,
        confidenceOf(
            src.factsRow,
        ),
        tryIt, listens = listOf(src.id), androidGuess = guess.androidText,
    )
}

/** The card's guess in the live route's units. */
fun guessText(card: EndpointCard, route: Route): String =
    if (route == Route.ANDROID) card.androidGuess ?: card.guess else card.guess

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
    val count = range.last - range.first + 1
    val tryIt = TryIt.Reason(HAND_REASON)
    val where = "report 0x01 · $bytes$bits"
    if (row == null) {
        val title = "Unknown, $bytes$bits"
        return EndpointCard(
            id,
            CardKind.INPUT_RANGE,
            title,
            where,
            "Nobody has decoded $bytes$bits yet. $UNKNOWN_TRY",
            null,
            Confidence.UNKNOWN,
            tryIt,
            range.first,
            count,
            range.mask,
        )
    }
    return EndpointCard(
        id, CardKind.INPUT_RANGE, "Byte 54, headphone flags", where, MIC_MUTED_GUESS, row,
        confidenceOf(
            row,
        ),
        tryIt, range.first, count, range.mask,
    )
}

private class OutputText(
    val title: String,
    val place: String,
    val what: String,
    val ends: String,
    val move: String,
    val row: String,
    val control: String,
    val tryIt: TryIt,
)

private val OUTPUT_TEXT: Map<OutputField, OutputText> = mapOf(
    OutputField.RUMBLE to OutputText(
        "Rumble motors",
        "bytes 3-4",
        "The two vibration motors, left and right.",
        "0 = still, 255 = full strength for each.",
        "Tap Try it and feel left, right, both or a pattern.",
        "P31",
        "out.rumble",
        TryIt.Action(TryKind.RUMBLE),
    ),
    OutputField.RUMBLE_BOTH_MODES to OutputText(
        "Everything off",
        "the whole report",
        "The all-off report: rumble in both modes, both triggers, the lightbar, the LEDs and the " +
            "mute LED back to nothing.",
        "Every value 0.",
        "Tap Try it, then check that everything on the pad went quiet.",
        "P45",
        "out.release",
        TryIt.Action(TryKind.ALL_OFF),
    ),
    OutputField.RIGHT_TRIGGER to OutputText(
        "R2 trigger resistance",
        "bytes 11-21",
        "A push-back effect on the right trigger: mode 1 resists from a start position with a " +
            "force.",
        "Start position 0-255 and force 0-255; all zeros = off. The bytes 01 30 c0 gave clear " +
            "resistance.",
        "Tap Try it, then squeeze R2.",
        "P39",
        "out.trigger.r2",
        TryIt.Action(TryKind.TRIGGERS, listOf(TryChoice.TRIGGER_R2, TryChoice.TRIGGER_OFF)),
    ),
    OutputField.LEFT_TRIGGER to OutputText(
        "L2 trigger resistance",
        "bytes 22-32",
        "A push-back effect on the left trigger: mode 1 resists from a start position with a " +
            "force.",
        "Start position 0-255 and force 0-255; all zeros = off. The bytes 01 30 c0 gave clear " +
            "resistance.",
        "Tap Try it, then squeeze L2.",
        "P39",
        "out.trigger.l2",
        TryIt.Action(TryKind.TRIGGERS, listOf(TryChoice.TRIGGER_L2, TryChoice.TRIGGER_OFF)),
    ),
    OutputField.LIGHTBAR to OutputText(
        "Lightbar colour",
        "bytes 45-47",
        "The red, green and blue of the light strip beside the touchpad.",
        "0 = dark, 255 = full for each of the three.",
        "Tap Try it and watch the strip change colour.",
        "P43",
        "out.lightbar",
        TryIt.Action(TryKind.LIGHTBAR),
    ),
    OutputField.PLAYER_LEDS to OutputText(
        "Player LEDs",
        "byte 44",
        "The five small lights under the touchpad.",
        "Five bits: LED 1 is bit 0, up to LED 5 as bit 4 (0x10).",
        "Tap the dots to light each LED in turn.",
        "P42",
        "out.led.player",
        TryIt.Action(TryKind.PLAYER_LEDS),
    ),
    OutputField.MUTE_LED to OutputText(
        "Mute LED",
        "byte 9",
        "The light on the Mute button.",
        "0 = off, 1 = on, 2 = pulsing.",
        "Tap Try it and pick off, on or pulse.",
        "P37",
        "out.led.mute",
        TryIt.Action(TryKind.MUTE_LED),
    ),
    OutputField.AUDIO_ROUTE to OutputText(
        "Audio output path",
        "byte 8, bits 4-5",
        "Which output the audio goes to: the headphones or the speaker.",
        "0 = both channels to the headphones with the speaker muted; 3 = right channel to the " +
            "speaker with the headphones muted.",
        "Set by the speaker and headphones cards.",
        "P36",
        "out.audio.route",
        TryIt.Reason(AUDIO_STEP_REASON),
    ),
    OutputField.MIC_VOLUME to OutputText(
        "Microphone volume",
        "byte 7",
        "How loud the pad's microphone is.",
        "0 to 0x40.",
        "Set when the microphone card powers the mic on.",
        "P35",
        "out.audio.mic",
        TryIt.Reason(AUDIO_STEP_REASON),
    ),
)

private fun outputCard(field: OutputField): EndpointCard {
    val t = checkNotNull(OUTPUT_TEXT[field]) { "no output text for $field" }
    val where = "output report 0x02 · ${t.place}"
    return EndpointCard(
        "out.${field.name.lowercase()}", CardKind.OUTPUT, t.title, where,
        "${t.what} ${t.ends} ${t.move}", t.row,
        confidenceOf(
            t.row,
        ),
        t.tryIt, listens = listOf(t.control),
    )
}

private fun audioCard(
    id: String,
    where: String,
    guess: FieldGuess,
    row: String,
    tryIt: TryIt,
): EndpointCard =
    EndpointCard(id, CardKind.AUDIO, guess.title, where, guess.text, row, confidenceOf(row), tryIt)

private const val NO_OUTPUT_DEVICE =
    "Android lists no output device for the pad (FACTS P80), and Direct USB audio needs the " +
        "audio step, not built yet."

private fun audioCards(): List<EndpointCard> = listOf(
    audioCard(
        "audio.mic",
        "USB audio interface 2 · IN endpoint 0x82",
        FieldGuess(
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
        FieldGuess(
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
        FieldGuess(
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
        FieldGuess(
            "HD haptics",
            "A buzz driven as sound on channels 3 and 4 (150 Hz was felt).",
            "It plays only while compatible rumble is off.",
            "Feel the pad while it plays.",
        ),
        "P44",
        TryIt.Reason(NO_OUTPUT_DEVICE),
    ),
)

private class AndroidSpec(
    val id: String,
    val title: String,
    val where: String,
    val row: String,
    val what: String,
    val ends: String,
    val move: String,
    val listens: List<String>,
    val tryIt: TryIt,
)

private val SEE_MIC = TryIt.Reason(
    "Android's list of input devices. The Microphone card records from the device it names.",
)
private val SEE_TONES = TryIt.Reason(
    "Android's list of output devices. The speaker and headphone cards play to the device it " +
        "names, if any.",
)

private val ANDROID_CARDS: List<AndroidSpec> = listOf(
    AndroidSpec(
        "android.keys", "Android key events", "Activity.dispatchKeyEvent · KeyEvent key codes",
        "P71",
        "The pad's buttons arriving as Android key codes (Cross is BUTTON_A, Circle BUTTON_B, " +
            "PS BUTTON_MODE).",
        "Down then up for each press.", "Press any button.",
        listOf("in.button", "in.key"), TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.key.mute", "Mute key (none)", "Activity.dispatchKeyEvent · no key code for Mute",
        "P72",
        "Mute sends no key event of any code; pressing it toggles the mute LED on the pad itself.",
        "Nothing arrives at the app.", "Press Mute and watch whether any row appears.",
        listOf("in.key"), TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.axes", "Android joystick axes",
        "Activity.dispatchGenericMotionEvent · MotionEvent axes", "P73",
        "The sticks (AXIS_X, Y, Z, RZ), the triggers (LTRIGGER, RTRIGGER) and the D-pad " +
            "(HAT_X, HAT_Y) as joystick axes.",
        "Sticks -1 to 1, triggers 0 to 1, D-pad -1, 0 or 1.",
        "Move each stick, squeeze each trigger, press the D-pad.",
        listOf(
            "in.stick",
            "in.trigger",
            "in.dpad",
        ),
        TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.touchpad", "Android touchpad pointer",
        "Activity.dispatchGenericMotionEvent · touchpad pointer", "P74",
        "The touchpad as a mouse pointer with a click; two fingers become Android's scroll " +
            "gesture.",
        "Pointer in screen coordinates; the click is mouse button 1.",
        "Drag one finger, click the pad, then try two fingers.",
        listOf(
            "in.touch",
            "in.button.touchpad_click",
        ),
        TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.sensors", "Android motion sensors",
        "InputDevice.getSensorManager() · gyroscope, accelerometer", "P75",
        "The gyroscope (range 2048) and accelerometer (range 4) in SI units.",
        "Gyroscope in rad/s, accelerometer in m/s squared.", "Tilt and turn the pad.",
        listOf("in.gyro", "in.accel"), TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.vibrator.0", "Left motor, Android vibrator 0", "VibratorManager vibrator id 0",
        "P76",
        "The left rumble motor as Android's vibrator id 0.", "Amplitude 1 to 255.",
        "Tap Try it and feel the left side; it feels weaker than Direct USB.",
        listOf(
            "out.rumble",
        ),
        TryIt.Action(TryKind.RUMBLE, listOf(TryChoice.RUMBLE_LEFT, TryChoice.RUMBLE_STOP)),
    ),
    AndroidSpec(
        "android.vibrator.1", "Right motor, Android vibrator 1", "VibratorManager vibrator id 1",
        "P76",
        "The right rumble motor as Android's vibrator id 1.", "Amplitude 1 to 255.",
        "Tap Try it and feel the right side; it feels weaker than Direct USB.",
        listOf(
            "out.rumble",
        ),
        TryIt.Action(TryKind.RUMBLE, listOf(TryChoice.RUMBLE_RIGHT, TryChoice.RUMBLE_STOP)),
    ),
    AndroidSpec(
        "android.light.indicator", "Lightbar, Android light 'indicator'",
        "LightsManager light 'indicator'", "P77",
        "The RGB lightbar as Android's light named indicator.",
        "On USB the request returns normally and nothing changes (FACTS P79).",
        "Tap Try it and watch the strip.",
        listOf("out.lightbar"), TryIt.Action(TryKind.LIGHTBAR),
    ),
    AndroidSpec(
        "android.light.player", "Player LEDs, Android lights 'player-1' to 'player-5'",
        "LightsManager lights 'player-1' to 'player-5'", "P77",
        "The five player LEDs as five lights that take brightness only.",
        "On USB the request returns normally and nothing changes (FACTS P79).",
        "Tap the dots and watch the pad.",
        listOf("out.led.player"), TryIt.Action(TryKind.PLAYER_LEDS),
    ),
    AndroidSpec(
        "android.battery", "Battery, Android battery state", "InputDevice.getBatteryState()", "P78",
        "Whether a battery is present, its capacity from 0 to 1 and its status.",
        "On USB it answers capacity -0.01 and status unknown (FACTS P79).",
        "Nothing to press: read the value in the trace.",
        emptyList(), TryIt.Reason("Read-only: Android reports it, there is nothing to fire."),
    ),
    AndroidSpec(
        "android.audio.in", "Audio inputs, Android list",
        "AudioManager.getDevices(GET_DEVICES_INPUTS) · USB headset", "P80",
        "The pad's microphone listed as a USB headset input at 48 kHz, 2 channels.",
        "No output device is listed for the pad.", "Nothing to press: it is a list.",
        emptyList(), SEE_MIC,
    ),
    AndroidSpec(
        "android.audio.out", "Audio outputs, Android list",
        "AudioManager.getDevices(GET_DEVICES_OUTPUTS) · the pad", "P80",
        "Whether Android lists any output device for the pad.",
        "On the Pixel it lists none (FACTS P80).", "Nothing to press: it is a list.",
        emptyList(), SEE_TONES,
    ),
)

private fun androidCard(spec: AndroidSpec): EndpointCard = EndpointCard(
    spec.id, CardKind.ANDROID, spec.title, spec.where, "${spec.what} ${spec.ends} ${spec.move}",
    spec.row,
    confidenceOf(
        spec.row,
    ),
    spec.tryIt, listens = spec.listens,
)

private val FIRMWARE_CARD = EndpointCard(
    "feature.0x20", CardKind.FEATURE, "Firmware report", "feature report 0x20 · 64 bytes",
    "The build date (bytes 1-19, text), hardware version (24-27), firmware version (28-31) and " +
        "update version (44-45). Read once when Direct USB claims the pad; the log header shows " +
        "it.",
    "P51",
    confidenceOf(
        "P51",
    ),
    TryIt.Reason(
        "Read once when Direct USB claims the pad; there is nothing to fire.",
    ),
    listens = listOf("in.feature"),
)

/** The cards whose raw byte is the player LED mask: their location line shows its live value. */
private val PLAYER_LED_CARDS = setOf("out.player_leds", "android.light.player")

/**
 * The location line, with the live player LED byte (binary, hex, which LED) added on the cards that
 * drive it. On the Android route an input card names its Android source instead of the report
 * byte; [noAndroidEvent] is the text for a field Android has no event for.
 */
fun locationLine(
    card: EndpointCard,
    playerLeds: Int,
    route: Route,
    noAndroidEvent: String,
): String {
    if (route == Route.ANDROID && card.kind in INPUT_KINDS) {
        return androidSourceOf(card.id) ?: noAndroidEvent
    }
    return if (card.id in PLAYER_LED_CARDS) {
        card.where + " · now " + ledMaskText(playerLeds)
    } else {
        card.where
    }
}

private val INPUT_KINDS = setOf(CardKind.INPUT_FIELD, CardKind.INPUT_RANGE)

/**
 * Every endpoint the guided test visits, in order: input fields, free report bytes, outputs, audio,
 * firmware, then Android's APIs. Bounded by [AppLimits.TOUR_CARDS_MAX].
 */
fun buildEndpointCards(): List<EndpointCard> {
    val cards = ArrayList<EndpointCard>()
    for (src in INPUT_MAP) cards.add(inputCard(src))
    for (range in unclaimedRanges()) cards.add(rangeCard(range))
    for (field in OutputField.entries) cards.add(outputCard(field))
    cards.addAll(audioCards())
    cards.add(FIRMWARE_CARD)
    for (spec in ANDROID_CARDS) cards.add(androidCard(spec))
    check(cards.size <= AppLimits.TOUR_CARDS_MAX) { "more cards than the limit: ${cards.size}" }
    check(cards.map { it.id }.toSet().size == cards.size) { "a card id repeats" }
    return cards
}
