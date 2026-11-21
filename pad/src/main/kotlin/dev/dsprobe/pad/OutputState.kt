package dev.dsprobe.pad

enum class RumbleMode { COMPATIBLE, V2 }

enum class MuteLed { OFF, ON, PULSE }

enum class AudioRoute { NONE, SPEAKER, HEADPHONES }

data class Rgb(val r: UByte, val g: UByte, val b: UByte)

/** Up to 11 effect bytes, mode first; the rest of the 11-byte slot is sent as zeros (P39). */
data class TriggerEffect(val bytes: List<UByte>) {
    init {
        require(bytes.size <= Limits.TRIGGER_EFFECT_BYTES_MAX) { "effect bytes ${bytes.size}" }
    }

    companion object {
        private const val MODE_RESISTANCE: UByte = 0x01u

        val OFF = TriggerEffect(emptyList())

        /** Mode 0x01: resistance from [startPos] with [force] (P39). */
        fun resistance(startPos: UByte, force: UByte): TriggerEffect {
            require(force.toInt() <= Limits.TRIGGER_FORCE_MAX) { "force $force" }
            return TriggerEffect(listOf(MODE_RESISTANCE, startPos, force))
        }
    }
}

/** Everything the output report can drive; the writer holds the one copy. */
data class OutputState(
    val rumbleLeft: UByte,
    val rumbleRight: UByte,
    val rumbleMode: RumbleMode,
    val lightbar: Rgb,
    val playerLedMask: Int, // 5 bits
    val muteLed: MuteLed,
    val r2Effect: TriggerEffect,
    val l2Effect: TriggerEffect,
    val audioRoute: AudioRoute,
    val speakerVolume: UByte,
    val headphoneVolume: UByte,
    val micVolume: UByte,
) {
    init {
        require(playerLedMask in 0..Limits.PLAYER_LED_MASK_MAX) { "player LED mask $playerLedMask" }
        require(speakerVolume.toInt() <= Limits.SPEAKER_VOLUME_MAX) {
            "speaker volume $speakerVolume"
        }
        require(headphoneVolume.toInt() <= Limits.HEADPHONE_VOLUME_MAX) {
            "headphone volume $headphoneVolume"
        }
        require(micVolume.toInt() <= Limits.MIC_VOLUME_MAX) { "mic volume $micVolume" }
    }

    companion object
}

/**
 * Rumble 0, triggers off, mute LED off, player LEDs off, lightbar 0,0,0 (P45); no audio route,
 * volumes 0.
 */
fun OutputState.Companion.allOff(): OutputState = OutputState(
    rumbleLeft = 0u,
    rumbleRight = 0u,
    rumbleMode = RumbleMode.COMPATIBLE,
    lightbar = Rgb(0u, 0u, 0u),
    playerLedMask = 0,
    muteLed = MuteLed.OFF,
    r2Effect = TriggerEffect.OFF,
    l2Effect = TriggerEffect.OFF,
    audioRoute = AudioRoute.NONE,
    speakerVolume = 0u,
    headphoneVolume = 0u,
    micVolume = 0u,
)
