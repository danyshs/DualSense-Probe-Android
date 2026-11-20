package dev.dsprobe.app

/** App-side limits; the report and protocol ones live in :pad's Limits. Units in the names, qualifiers last. */
object AppLimits {
    const val STRANDED_GRACE_MS = 3_000L // a guess: a fresh plug's input devices appear well within it
    const val ANDROID_REBIND_TIMEOUT_MS = 5_000L
    const val PERMISSION_BACKGROUND_WAIT_MS = 30_000L
    const val ROUTE_EVENTS_PER_DRAIN_MAX = 64
    const val FEED_EVENTS_MAX = 4_096
    const val BATTERY_POLL_INTERVAL_MS = 5_000L

    const val ANSWERS_POLL_INTERVAL_MS = 1_000L
    const val PLAYER_LED_COUNT = 5
    const val RUMBLE_STEPS_MAX = 512 // 5 s of 10 ms steps, the writer's minimum gap
    const val RUMBLE_PATTERN_DURATION_MS_MIN = 1_000
    const val RUMBLE_PULSE_RATE_HZ_MIN = 1
    const val RUMBLE_PULSE_RATE_HZ_MAX = 10
    const val RUMBLE_PULSE_RATE_HZ_DEFAULT = 4
    const val RUMBLE_INTENSITY_DEFAULT = 200 // a guess: strong but not full, so full is a visible step up
    const val RUMBLE_TRACE_SAMPLES_MAX = 120
    const val TRIGGER_START_MAX = 255 // the start position is one byte
    const val TRIGGER_START_DEFAULT = 0x30 // the start of the proven effect 01 30 c0
    const val COLOUR_SEND_INTERVAL_MS_MIN = 33L // a guess: about 30 colour updates a second; tune on the phone

    const val READ_TIMEOUT_MS = 100
    const val FEATURE_READ_TIMEOUT_MS = 100 // a guess: same as the read wait
    const val OUTPUT_WRITE_TIMEOUT_MS = 100 // a guess: same as the read wait
    const val RELEASE_JOIN_TIMEOUT_MS = 500L
    const val CRASH_RECORD_BYTES_MAX = 64_000
    const val SEND_FAILURES_IN_A_ROW_MAX = 5
    const val REJECTED_REPORTS_LOGGED_MAX = 20

    const val PRESS_GLOW_FADE_MS = 120L // a guess: tune by eye
    const val STICK_TRAIL_MS = 300L // a guess: tune by eye
    const val STICK_TRAIL_POINTS_MAX = 32
    const val STICK_FULL_DEFLECTION_RATIO_MIN = 0.97f
    const val TRIGGER_PEAK_HOLD_MS = 1_000L
    const val CLICK_RIPPLE_MS = 400L
    const val CLICK_RIPPLES_MAX = 4
    const val MOTION_TRACE_SAMPLES_MAX = 120
    const val ACCEL_TILT_DEGREES_MAX = 8f
}
