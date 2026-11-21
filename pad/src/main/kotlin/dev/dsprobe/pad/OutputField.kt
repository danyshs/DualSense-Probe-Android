package dev.dsprobe.pad

/** One group of bytes with its own valid flag; a report names the groups it applies. */
enum class OutputField {
    RUMBLE, // the mode in OutputState picks which valid flags go up
    RUMBLE_BOTH_MODES, // flags of both modes, for the all-off report (P45)
    RIGHT_TRIGGER,
    LEFT_TRIGGER,
    LIGHTBAR,
    PLAYER_LEDS,
    MUTE_LED,
    AUDIO_ROUTE, // route bytes and the volume that route uses; NONE sends nothing
    MIC_VOLUME,
}
