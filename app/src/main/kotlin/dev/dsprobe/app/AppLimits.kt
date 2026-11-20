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

    const val ROUTE_TRACE_ROWS_MAX = 500
    const val ROUTE_TRACE_AXIS_INTERVAL_MS_MIN = 100L // a guess: 10 rows a second per axis stays readable
    const val INPUT_TEST_TIMEOUT_MS = 10_000L
    const val CONFIRM_TAP_TIMEOUT_MS = 60_000L
    const val DEBUG_RUN_MS_MAX = 1_800_000L
    const val ROUTE_SWITCH_TIMEOUT_MS = 30_000L // a guess: long enough for the USB permission dialog
    const val CROSS_TALK_THRESHOLD_COUNTS = 8
    const val STICK_EDGE_TOLERANCE_COUNTS = 16
    const val TRIGGER_REACHED_COUNTS_MIN = 247
    const val COUNTS_PER_BYTE_MAX = 255f
    const val MOTION_DISTINCT_VALUES_MIN = 8 // a guess: a still pad shows 1-2 values, a tilted one many
    const val LOG_BYTES_MAX = 2_000_000
    const val LOG_FOOTER_BYTES_RESERVED = 4_000
    const val LOG_LINE_CHARS_MAX = 2_000
    const val RAW_REPORTS_PER_TEST_MAX = 50
    const val RESULT_DETAIL_CHARS_MAX = 300
    const val REFLECTED_METHODS_PER_CLASS_MAX = 2_000
    const val PROBE_MATCHES_LISTED_MAX = 60
    const val KEY_CODES_PROBED_MAX = 1_024
    const val APK_HASH_BUFFER_BYTES = 65_536

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
