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

/** What the all-off report applies (P45): rumble in both modes, triggers, lightbar, player LEDs, mute LED. */
val RELEASE_FIELDS: Set<OutputField> = setOf(
    OutputField.RUMBLE_BOTH_MODES, OutputField.RIGHT_TRIGGER, OutputField.LEFT_TRIGGER,
    OutputField.LIGHTBAR, OutputField.PLAYER_LEDS, OutputField.MUTE_LED,
)

private const val REPORT_ID_OUTPUT = 0x02

// byte offsets (P30-P43)
private const val FLAG0 = 1
private const val FLAG1 = 2
private const val FLAG2 = 39
private const val RUMBLE_RIGHT = 3
private const val RUMBLE_LEFT = 4
private const val HEADPHONE_VOLUME = 5
private const val SPEAKER_VOLUME = 6
private const val MIC_VOLUME = 7
private const val AUDIO_PATH = 8
private const val MUTE_LED = 9
private const val POWER_SAVE = 10
private const val RIGHT_TRIGGER = 11
private const val LEFT_TRIGGER = 22
private const val SPEAKER_PREAMP = 38
private const val PLAYER_LEDS = 44
private const val LIGHTBAR = 45

// valid-flag bits
private const val F0_COMPAT_VIBRATION = 0x01
private const val F0_MOTORS = 0x02
private const val F0_RIGHT_TRIGGER = 0x04
private const val F0_LEFT_TRIGGER = 0x08
private const val F0_HEADPHONE_VOLUME = 0x10
private const val F0_SPEAKER_VOLUME = 0x20
private const val F0_MIC_VOLUME = 0x40
private const val F0_AUDIO_PATH = 0x80
private const val F1_MUTE_LED = 0x01
private const val F1_POWER_SAVE = 0x02
private const val F1_LIGHTBAR = 0x04
private const val F1_PLAYER_LEDS = 0x10
private const val F1_SPEAKER_PREAMP = 0x80
private const val F2_VIBRATION_V2 = 0x04

private const val PATH_SHIFT = 4
private const val PATH_HEADPHONES = 0
private const val PATH_SPEAKER = 3
private const val SPEAKER_PREAMP_GAIN = 2 // the working speaker route (P40, P61)

private fun ByteArray.setBits(i: Int, bits: Int) {
    this[i] = (this[i].toInt() or bits).toByte()
}

private fun ByteArray.put(i: Int, v: UByte) {
    this[i] = v.toByte()
}

private fun requireWithinCaps(s: OutputState) {
    require(s.playerLedMask in 0..Limits.PLAYER_LED_MASK_MAX) { "player LED mask" }
    require(s.speakerVolume.toInt() <= Limits.SPEAKER_VOLUME_MAX) { "speaker volume" }
    require(s.headphoneVolume.toInt() <= Limits.HEADPHONE_VOLUME_MAX) { "headphone volume" }
    require(s.micVolume.toInt() <= Limits.MIC_VOLUME_MAX) { "mic volume" }
    require(s.r2Effect.bytes.size <= Limits.TRIGGER_EFFECT_BYTES_MAX) { "r2 effect size" }
    require(s.l2Effect.bytes.size <= Limits.TRIGGER_EFFECT_BYTES_MAX) { "l2 effect size" }
}

private fun putRumble(out: ByteArray, s: OutputState, bothModes: Boolean) {
    out.put(RUMBLE_RIGHT, s.rumbleRight)
    out.put(RUMBLE_LEFT, s.rumbleLeft)
    out.setBits(FLAG0, F0_MOTORS)
    if (bothModes || s.rumbleMode == RumbleMode.COMPATIBLE) out.setBits(FLAG0, F0_COMPAT_VIBRATION)
    if (bothModes || s.rumbleMode == RumbleMode.V2) out.setBits(FLAG2, F2_VIBRATION_V2)
}

private fun putTrigger(out: ByteArray, effect: TriggerEffect, offset: Int, flag0Bit: Int) {
    for ((i, b) in effect.bytes.withIndex()) out.put(offset + i, b)
    out.setBits(FLAG0, flag0Bit)
}

private fun putAudioRoute(out: ByteArray, s: OutputState) {
    when (s.audioRoute) {
        AudioRoute.NONE -> return
        AudioRoute.SPEAKER -> {
            out.put(SPEAKER_VOLUME, s.speakerVolume)
            out[AUDIO_PATH] = (PATH_SPEAKER shl PATH_SHIFT).toByte()
            out[SPEAKER_PREAMP] = SPEAKER_PREAMP_GAIN.toByte()
            out.setBits(FLAG0, F0_AUDIO_PATH or F0_SPEAKER_VOLUME)
            out.setBits(FLAG1, F1_SPEAKER_PREAMP)
        }
        AudioRoute.HEADPHONES -> {
            out.put(HEADPHONE_VOLUME, s.headphoneVolume)
            out[AUDIO_PATH] = (PATH_HEADPHONES shl PATH_SHIFT).toByte()
            out.setBits(FLAG0, F0_AUDIO_PATH or F0_HEADPHONE_VOLUME)
        }
    }
}

/** Mic on: volume plus a power-save byte of 0 (the proven mic-on report, P62). */
private fun putMic(out: ByteArray, s: OutputState) {
    out.put(MIC_VOLUME, s.micVolume)
    out[POWER_SAVE] = 0
    out.setBits(FLAG0, F0_MIC_VOLUME)
    out.setBits(FLAG1, F1_POWER_SAVE)
}

private fun putField(out: ByteArray, s: OutputState, field: OutputField) {
    when (field) {
        OutputField.RUMBLE -> putRumble(out, s, bothModes = false)
        OutputField.RUMBLE_BOTH_MODES -> putRumble(out, s, bothModes = true)
        OutputField.RIGHT_TRIGGER -> putTrigger(out, s.r2Effect, RIGHT_TRIGGER, F0_RIGHT_TRIGGER)
        OutputField.LEFT_TRIGGER -> putTrigger(out, s.l2Effect, LEFT_TRIGGER, F0_LEFT_TRIGGER)
        OutputField.LIGHTBAR -> {
            out.put(LIGHTBAR, s.lightbar.r)
            out.put(LIGHTBAR + 1, s.lightbar.g)
            out.put(LIGHTBAR + 2, s.lightbar.b)
            out.setBits(FLAG1, F1_LIGHTBAR)
        }
        OutputField.PLAYER_LEDS -> {
            out[PLAYER_LEDS] = s.playerLedMask.toByte()
            out.setBits(FLAG1, F1_PLAYER_LEDS)
        }
        OutputField.MUTE_LED -> {
            out[MUTE_LED] = s.muteLed.ordinal.toByte() // OFF 0, ON 1, PULSE 2 (P37)
            out.setBits(FLAG1, F1_MUTE_LED)
        }
        OutputField.AUDIO_ROUTE -> putAudioRoute(out, s)
        OutputField.MIC_VOLUME -> putMic(out, s)
    }
}

/**
 * Builds output report 0x02 (63 bytes). Only the groups in [changed] get their values and valid
 * flags, so one change never re-sends another feature by accident.
 */
fun buildUsbOutput(state: OutputState, changed: Set<OutputField>): ByteArray {
    require(changed.isNotEmpty()) { "a report that changes nothing" }
    requireWithinCaps(state)
    val out = ByteArray(Limits.USB_OUTPUT_REPORT_BYTES)
    out[0] = REPORT_ID_OUTPUT.toByte()
    for (field in OutputField.entries) {
        if (field in changed) putField(out, state, field)
    }
    check(out.size == Limits.USB_OUTPUT_REPORT_BYTES) { "output size ${out.size}" }
    check(out[0].toInt() == REPORT_ID_OUTPUT) { "output report id" }
    return out
}
