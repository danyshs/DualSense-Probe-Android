package dev.dsprobe.app.debug

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

private val SEE_AUDIO = TryIt.Reason(
    "Android's lists of input and output devices. The Microphone card records from the input it " +
        "names; the speaker and headphone cards play to the output it names, if any.",
)

private val ANDROID_SPECS: List<AndroidSpec> = listOf(
    AndroidSpec(
        "android.keys", "Android key events", "Activity.dispatchKeyEvent · KeyEvent key codes",
        "P71, P72",
        "The pad's buttons arriving as Android key codes (Cross is BUTTON_A, Circle BUTTON_B, " +
            "PS BUTTON_MODE). Mute sends no key event of any code; pressing it toggles the mute " +
            "LED on the pad itself.",
        "Down then up for each press; nothing arrives for Mute.",
        "Press any button, Mute included, and watch which rows appear.",
        listOf("in.button", "in.key"), TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.axes", "Android joystick axes",
        "Activity.dispatchGenericMotionEvent · MotionEvent axes", "P73",
        "The sticks (AXIS_X, Y, Z, RZ), the triggers (LTRIGGER, RTRIGGER) and the D-pad " +
            "(HAT_X, HAT_Y) as joystick axes.",
        "Sticks -1 to 1, triggers 0 to 1, D-pad -1, 0 or 1.",
        "Move each stick, squeeze each trigger, press the D-pad.",
        listOf("in.stick", "in.trigger", "in.dpad"), TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.touchpad", "Android touchpad pointer",
        "Activity.dispatchGenericMotionEvent · touchpad pointer", "P74",
        "The touchpad as a mouse pointer with a click; two fingers become Android's scroll " +
            "gesture.",
        "Pointer in screen coordinates; the click is mouse button 1.",
        "Drag one finger, click the pad, then try two fingers.",
        listOf("in.touch", "in.button.touchpad_click"), TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.sensors", "Android motion sensors",
        "InputDevice.getSensorManager() · gyroscope, accelerometer", "P75",
        "The gyroscope (range 2048) and accelerometer (range 4) in SI units.",
        "Gyroscope in rad/s, accelerometer in m/s squared.", "Tilt and turn the pad.",
        listOf("in.gyro", "in.accel"), TryIt.Reason(HAND_REASON),
    ),
    AndroidSpec(
        "android.vibrators", "Rumble motors, Android vibrators 0 and 1",
        "VibratorManager vibrator ids 0 and 1", "P76",
        "The left rumble motor as Android's vibrator id 0 and the right as id 1.",
        "Amplitude 1 to 255.",
        "Tap Try it and feel each side; it feels weaker than Direct USB.",
        listOf("out.rumble"),
        TryIt.Action(
            TryKind.RUMBLE,
            listOf(TryChoice.RUMBLE_LEFT, TryChoice.RUMBLE_RIGHT, TryChoice.RUMBLE_STOP),
        ),
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
        "android.audio", "Audio devices, Android lists",
        "AudioManager.getDevices(GET_DEVICES_INPUTS and GET_DEVICES_OUTPUTS) · USB headset", "P80",
        "The pad's microphone listed as a USB headset input at 48 kHz, 2 channels, and whether " +
            "Android lists any output device for the pad.",
        "On the Pixel no output device is listed (FACTS P80).",
        "Nothing to press: they are lists.",
        emptyList(), SEE_AUDIO,
    ),
)

private fun androidCard(spec: AndroidSpec): EndpointCard {
    val rows = spec.row.split(", ")
    return EndpointCard(
        id = spec.id, kind = CardKind.ANDROID, title = spec.title, where = listOf(spec.where),
        guess = cardText(spec.what, listOf(spec.ends), spec.move), factsRow = factsRowText(rows),
        confidence = confidenceOfRows(rows), tryIt = spec.tryIt, listens = spec.listens,
    )
}

fun androidCards(): List<EndpointCard> = ANDROID_SPECS.map(::androidCard)
