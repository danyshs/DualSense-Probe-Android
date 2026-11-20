package dev.dsprobe.pad

/** Report and protocol limits (FACTS P3, P17, P31-P39, P45). Units and counts in the names, qualifiers last. */
object Limits {
    const val USB_INPUT_REPORT_BYTES = 64
    const val USB_OUTPUT_REPORT_BYTES = 63
    const val USB_FEATURE_FIRMWARE_REPORT_BYTES = 64
    const val USB_FEATURE_FIRMWARE_REPORT_ID = 0x20

    // interface 3, the HID one (P2)
    const val USB_HID_INTERFACE_ID = 3
    const val USB_HID_IN_ENDPOINT_ADDRESS = 0x84
    const val USB_HID_OUT_ENDPOINT_ADDRESS = 0x03
    const val USB_HID_ENDPOINT_PACKET_BYTES = 64

    const val OUTPUT_INTERVAL_MS_MIN = 10
    const val OUTPUT_RESEND_INTERVAL_MS = 500 // placeholder until a phone test shows whether the pad holds its output
    const val RUMBLE_DURATION_MS_MAX = 5_000
    const val TRIGGER_EFFECT_DURATION_MS_MAX = 30_000
    const val AUDIO_TEST_DURATION_MS_MAX = 10_000

    const val SPEAKER_VOLUME_MAX = 0x64
    const val HEADPHONE_VOLUME_MAX = 0x7f
    const val MIC_VOLUME_MAX = 0x40
    const val TRIGGER_FORCE_MAX = 0xc0
    const val TRIGGER_EFFECT_BYTES_MAX = 11
    const val PLAYER_LED_MASK_MAX = 0x1f
    const val MOTOR_STRENGTH_MAX = 255
    const val AUDIO_AMPLITUDE_MAX = 0.25f // a guess: tune by ear

    const val TOUCH_POINTS_MAX = 2
    const val TOUCH_X_PX_MAX = 1919
    const val TOUCH_Y_PX_MAX = 1079

    const val AUDIO_SAMPLE_RATE_HZ = 48_000
    const val AUDIO_OUT_CHANNELS = 4
    const val AUDIO_FRAMES_PER_PACKET = 48

    // all three are guesses, tune on the phone
    const val ISO_URBS_IN_FLIGHT = 8
    const val ISO_PACKETS_PER_URB = 8
    const val ISO_STALL_TIMEOUT_MS = 1_000
}
