package dev.dsprobe.app.debug

/**
 * One fact only Android's API carries, shown on the physical card it belongs to. [api] names the
 * call it comes from; no two facts share one.
 */
private class AndroidFact(val cardId: String, val api: String, val text: String)

private const val USB_NO_CHANGE =
    "On USB the request returns normally and nothing changes (FACTS P79)."

// the order inside a card is the order shown
private val ANDROID_FACTS: List<AndroidFact> = listOf(
    AndroidFact(
        "in.buttons.face", "Activity.dispatchKeyEvent",
        "Android: each press arrives as a key event, down then up.",
    ),
    AndroidFact(
        "in.buttons.system", "KeyEvent for Mute",
        "Android: Mute sends no key event of any code; pressing it toggles the mute LED on the " +
            "pad itself.",
    ),
    AndroidFact(
        "in.gyro", "SensorManager TYPE_GYROSCOPE range",
        "Android: range 2048, reported in rad/s.",
    ),
    AndroidFact(
        "in.accel", "SensorManager TYPE_ACCELEROMETER range",
        "Android: range 4, reported in m/s squared.",
    ),
    AndroidFact(
        "in.battery", "InputDevice.getBatteryState()",
        "Android: getBatteryState() gives whether a battery is present, its capacity from 0 to " +
            "1 and its status. On USB it answers capacity -0.01 and status unknown (FACTS P79).",
    ),
    AndroidFact(
        "out.rumble", "VibratorManager vibrator ids",
        "Android: the left motor is vibrator id 0 and the right is id 1, amplitude 1 to 255. " +
            "It feels weaker than Direct USB.",
    ),
    AndroidFact(
        "out.lightbar", "LightsManager light 'indicator'",
        "Android: this is the light named 'indicator'. $USB_NO_CHANGE",
    ),
    AndroidFact(
        "out.player_leds", "LightsManager lights 'player-1' to 'player-5'",
        "Android: these are the lights named 'player-1' to 'player-5', which take brightness " +
            "only. $USB_NO_CHANGE",
    ),
    AndroidFact(
        "audio.mic", "AudioManager.getDevices(GET_DEVICES_INPUTS)",
        "Android: lists the pad's microphone as a USB headset input at 48 kHz, 2 channels.",
    ),
    AndroidFact(
        "audio.speaker", "AudioManager.getDevices(GET_DEVICES_OUTPUTS)",
        "Android: lists no output device for the pad (FACTS P80), for the speaker or the " +
            "headphones.",
    ),
)

/**
 * The cards with Android's own facts added to their Android-mode text; Direct USB text is not
 * touched. A fact for a card that does not exist, or an API named twice, fails here at start.
 */
fun withAndroidFacts(cards: List<EndpointCard>): List<EndpointCard> {
    val ids = cards.map { it.id }.toSet()
    check(ANDROID_FACTS.all { it.cardId in ids }) { "an Android fact names no card" }
    check(ANDROID_FACTS.map { it.api }.toSet().size == ANDROID_FACTS.size) { "an API repeats" }
    val byCard = ANDROID_FACTS.groupBy { it.cardId }
    return cards.map { card ->
        val facts = byCard[card.id] ?: return@map card
        card.copy(
            androidGuess = (listOf(card.androidGuess ?: card.guess) + facts.map { it.text })
                .joinToString("\n"),
            androidApis = facts.map { it.api },
        )
    }
}
