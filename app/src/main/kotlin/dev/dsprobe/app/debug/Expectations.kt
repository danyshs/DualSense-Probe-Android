package dev.dsprobe.app.debug

/** What to expect on one connection and the FACTS rows it rests on; no rows means none exist. */
data class Expectation(val text: String, val rows: List<String>) {
    init {
        require(text.isNotBlank()) { "an expectation with no text" }
        require((rows.isEmpty()) == (text == NOT_MEASURED_TEXT)) {
            "rows and the not-measured text must go together: $text"
        }
    }
}

const val NOT_MEASURED_TEXT =
    "Not measured on this connection yet: what you see now is the first record"

private val NOT_MEASURED = Expectation(NOT_MEASURED_TEXT, emptyList())

private fun exp(text: String, vararg rows: String) = Expectation(text, rows.toList())

/** One card's expectation for each of the three connections. */
class ConnectionExpectations(
    val directUsb: Expectation,
    val androidUsb: Expectation,
    val androidBluetooth: Expectation,
) {
    fun on(connection: Connection): Expectation = when (connection) {
        Connection.DIRECT_USB -> directUsb
        Connection.ANDROID_USB -> androidUsb
        Connection.ANDROID_BLUETOOTH -> androidBluetooth
        Connection.NONE -> error("nothing is expected with no pad")
    }
}

private fun row(
    direct: Expectation,
    androidUsb: Expectation = NOT_MEASURED,
    bluetooth: Expectation = NOT_MEASURED,
) = ConnectionExpectations(direct, androidUsb, bluetooth)

private const val LIGHTS_USB = "The calls return normally and nothing changes."

private fun stickRow(axes: String, click: String) = row(
    exp(
        "x and y each run 0 to 255, centre about 128. The stick click is a button bit.",
        "P10",
        "P14",
    ),
    exp("$axes run -1.0 to 1.0. The click ($click) arrives as a key event.", "P73", "P71"),
)

private fun triggerRow(axis: String) = row(
    exp(
        "The analog value runs 0 to 255 and reaches 255; the digital bit comes on too.",
        "P11",
        "P14",
    ),
    exp("$axis runs 0.0 to 1.0; the digital press arrives as a key event.", "P73", "P71"),
)

private fun motionRow(range: String) = row(
    exp("The three values change with motion; meaning, sign and scale are untested.", "P16"),
    exp("Android lists the sensor with range $range in SI units; values were not logged.", "P75"),
)

private val INPUT_EXPECTATIONS: Map<String, ConnectionExpectations> = mapOf(
    "in.stick.left" to stickRow("AXIS_X and AXIS_Y", "BUTTON_THUMBL"),
    "in.stick.right" to stickRow("AXIS_Z and AXIS_RZ", "BUTTON_THUMBR"),
    "in.trigger.l2" to triggerRow("AXIS_LTRIGGER"),
    "in.trigger.r2" to triggerRow("AXIS_RTRIGGER"),
    "in.touch.0" to row(
        exp("Touching clears the not-touching bit; x runs 0 to 1919 and y 0 to 1079.", "P17"),
        exp(
            "Android drives its mouse pointer in screen coordinates; " +
                "the click is mouse button 0x1.",
            "P74",
        ),
    ),
    "in.touch.1" to row(
        exp("A second finger has its own contact id and the same ranges.", "P17"),
        exp("Two fingers become Android's own scroll gesture, not a second touch point.", "P74"),
    ),
    "in.dpad" to row(
        exp("0 = up, clockwise to 7 = up-left, 8 = nothing pressed.", "P13"),
        exp("AXIS_HAT_X and AXIS_HAT_Y each read -1, 0 or 1.", "P73"),
    ),
    "in.buttons.face" to row(
        exp("Square, Cross, Circle and Triangle are bits 4 to 7 of byte 8.", "P13"),
        exp(
            "Cross is BUTTON_A, Circle BUTTON_B, Square BUTTON_X, " +
                "Triangle BUTTON_Y, each a key event.",
            "P71",
        ),
    ),
    "in.buttons.shoulder" to row(
        exp("L1 is bit 0 and R1 bit 1 of byte 9.", "P14"),
        exp("BUTTON_L1 and BUTTON_R1 arrive as key events.", "P71"),
    ),
    "in.buttons.system" to row(
        exp(
            "Create and Options are bits 4 and 5 of byte 9; " +
                "PS, touchpad click and Mute are bits 0 to 2 of byte 10.",
            "P14",
            "P15",
        ),
        exp(
            "Create is BUTTON_SELECT, Options BUTTON_START, PS BUTTON_MODE. " +
                "Mute sends no key event; it toggles its LED on the pad.",
            "P71",
            "P72",
        ),
    ),
    "in.gyro" to motionRow("2048"),
    "in.accel" to motionRow("4"),
    "in.battery" to row(
        exp("Raw level reads 8 to 9 and charging 2; the mapping to percent is untested.", "P18"),
        exp("Capacity -0.01 and status unknown.", "P79"),
        exp("A real percentage and a charging status.", "P78"),
    ),
    "in.sequence" to
        row(exp("A counter that goes up by 1 per report and wraps from 255 to 0.", "P12")),
    "in.headset" to row(
        exp("Byte 54 bit 0 is 1 with headphones in the jack; the mic bits are untested.", "P19"),
    ),
)

private val OUTPUT_EXPECTATIONS: Map<String, ConnectionExpectations> = mapOf(
    "out.rumble" to row(
        exp("The motors spin at the strength set, in either rumble mode.", "P31"),
        exp("Vibrators 0 (left) and 1 (right) run, weaker than Direct USB.", "P76"),
    ),
    "out.rumble_both_modes" to row(
        exp("Every output goes off and the pad returns to Android normally.", "P45"),
    ),
    "out.triggers" to row(exp("The trigger you squeeze pushes back.", "P39")),
    "out.lightbar" to row(
        exp("The bar changes colour.", "P43"),
        exp(LIGHTS_USB, "P79"),
        exp("The bar changes colour.", "P77"),
    ),
    "out.player_leds" to row(
        exp("The chosen LEDs light, singly and in combinations.", "P42"),
        exp(LIGHTS_USB, "P79"),
        exp("The five player lights change; they take brightness only.", "P77"),
    ),
    "out.mute_led" to row(
        exp("The Mute light goes off, on or pulses as set.", "P37"),
        exp("Android has no light for the mute LED, so there is nothing to send.", "P77"),
        exp("Android has no light for the mute LED, so there is nothing to send.", "P77"),
    ),
    "out.audio" to row(
        exp(
            "Route 3 sends sound to the speaker, 0 to the headphones; mic volume runs 0 to 0x40.",
            "P36",
            "P35",
        ),
    ),
)

private val AUDIO_EXPECTATIONS: Map<String, ConnectionExpectations> = mapOf(
    "audio.mic" to row(
        exp("About 3,000 packets in 3 s with no errors; loud input reaches full scale.", "P62"),
        exp("The pad is listed as a USB headset input; recording from it works.", "P80"),
    ),
    "audio.speaker" to row(
        exp("A 440 Hz tone is heard from the pad's speaker.", "P61"),
        exp("Android lists no output device for the pad, so there is no tone.", "P80"),
    ),
    "audio.headphones" to row(
        exp("A quiet 440 Hz tone is heard in the headphones.", "P61"),
        exp("Android lists no output device for the pad, so there is no tone.", "P80"),
    ),
    "audio.haptics" to row(
        exp("A 150 Hz buzz is felt, only while compatible rumble is off.", "P44", "P61"),
    ),
    "feature.0x20" to row(
        exp("Build date text in bytes 1-19; update version 0x0630 on the pad measured.", "P51"),
    ),
)

/** Every card with its own entry; the free byte ranges share one, see [expectationsFor]. */
val EXPECTATIONS: Map<String, ConnectionExpectations> =
    INPUT_EXPECTATIONS + OUTPUT_EXPECTATIONS + AUDIO_EXPECTATIONS

private const val UNCLAIMED_PREFIX = "in.unclaimed."

private val UNCLAIMED = row(NOT_MEASURED)

/** The three expectations of card [cardId]; a card with no entry fails here. */
fun expectationsFor(cardId: String): ConnectionExpectations =
    if (cardId.startsWith(UNCLAIMED_PREFIX)) {
        UNCLAIMED
    } else {
        checkNotNull(EXPECTATIONS[cardId]) { "no expectation entry for card $cardId" }
    }

/** What to expect on [connection] for card [cardId]. */
fun expectationOn(cardId: String, connection: Connection): Expectation =
    expectationsFor(cardId).on(connection)

/** The two measurable connections other than [live], in order. */
fun otherConnections(live: Connection): List<Connection> = Connection.MEASURABLE - live
