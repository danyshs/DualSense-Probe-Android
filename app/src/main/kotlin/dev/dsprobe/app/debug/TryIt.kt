package dev.dsprobe.app.debug

import androidx.annotation.StringRes
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.PatternId
import dev.dsprobe.app.route.Feature
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb

/** What a Try it button fires. MIC, SPEAKER and HEADPHONES go to Android's audio, the rest to OutputController. */
enum class TryKind { LIGHTBAR, PLAYER_LEDS, MUTE_LED, RUMBLE, TRIGGERS, ALL_OFF, MIC, SPEAKER, HEADPHONES }

/** A card's Try it: something to fire, or the stated reason there is nothing to fire. */
sealed interface TryIt {
    data class Action(val kind: TryKind, val choices: List<TryChoice> = choicesOf(kind)) : TryIt

    data class Reason(val text: String) : TryIt
}

/** One thing a Try it can send. The first choice of a kind is what the big button fires. */
enum class TryChoice(val kind: TryKind, @param:StringRes val labelRes: Int) {
    RED(TryKind.LIGHTBAR, R.string.try_red),
    GREEN(TryKind.LIGHTBAR, R.string.try_green),
    BLUE(TryKind.LIGHTBAR, R.string.try_blue),
    WHITE(TryKind.LIGHTBAR, R.string.try_white),
    LIGHT_OFF(TryKind.LIGHTBAR, R.string.leds_off),
    LEDS_ALL(TryKind.PLAYER_LEDS, R.string.leds_all),
    LEDS_OFF(TryKind.PLAYER_LEDS, R.string.leds_off),
    MUTE_PULSE(TryKind.MUTE_LED, R.string.mute_pulse),
    MUTE_ON(TryKind.MUTE_LED, R.string.mute_on),
    MUTE_OFF(TryKind.MUTE_LED, R.string.mute_off),
    RUMBLE_BOTH(TryKind.RUMBLE, R.string.try_both),
    RUMBLE_LEFT(TryKind.RUMBLE, R.string.try_left),
    RUMBLE_RIGHT(TryKind.RUMBLE, R.string.try_right),
    RUMBLE_PATTERN(TryKind.RUMBLE, R.string.try_pattern),
    RUMBLE_STOP(TryKind.RUMBLE, R.string.try_stop),
    TRIGGER_R2(TryKind.TRIGGERS, R.string.try_r2),
    TRIGGER_L2(TryKind.TRIGGERS, R.string.try_l2),
    TRIGGER_OFF(TryKind.TRIGGERS, R.string.try_triggers_off),
    ALL_OFF(TryKind.ALL_OFF, R.string.try_all_off),
}

/** The choices of a kind, default first. Audio kinds have none: their cards draw their own controls. */
fun choicesOf(kind: TryKind): List<TryChoice> = TryChoice.entries.filter { it.kind == kind }

/** The Android mode row that locks a kind of Try it, null where none does. */
fun featureOf(kind: TryKind): Feature? = when (kind) {
    TryKind.LIGHTBAR -> Feature.LIGHTBAR
    TryKind.PLAYER_LEDS -> Feature.PLAYER_LEDS
    TryKind.MUTE_LED -> Feature.MUTE_LED
    TryKind.RUMBLE -> Feature.RUMBLE
    TryKind.TRIGGERS -> Feature.ADAPTIVE_TRIGGERS
    TryKind.ALL_OFF, TryKind.MIC, TryKind.SPEAKER, TryKind.HEADPHONES -> null
}

private val WHITE = Rgb(255u, 255u, 255u)
private val BLACK = Rgb(0u, 0u, 0u)

private fun colourOf(choice: TryChoice): Rgb = when (choice) {
    TryChoice.RED -> Rgb(255u, 0u, 0u)
    TryChoice.GREEN -> Rgb(0u, 255u, 0u)
    TryChoice.BLUE -> Rgb(0u, 0u, 255u)
    TryChoice.WHITE -> WHITE
    else -> BLACK
}

/** Sends [choice] through the same OutputController calls the Outputs screen uses. */
fun fireChoice(choice: TryChoice, outputs: OutputController) {
    val strength = AppLimits.RUMBLE_INTENSITY_DEFAULT
    when (choice) {
        TryChoice.RED, TryChoice.GREEN, TryChoice.BLUE, TryChoice.WHITE, TryChoice.LIGHT_OFF -> {
            outputs.setLightbar(colourOf(choice))
            outputs.flushLightbar()
        }
        TryChoice.LEDS_ALL -> outputs.setPlayerLeds(Limits.PLAYER_LED_MASK_MAX)
        TryChoice.LEDS_OFF -> outputs.setPlayerLeds(0)
        TryChoice.MUTE_PULSE -> outputs.setMuteLed(MuteLed.PULSE)
        TryChoice.MUTE_ON -> outputs.setMuteLed(MuteLed.ON)
        TryChoice.MUTE_OFF -> outputs.setMuteLed(MuteLed.OFF)
        TryChoice.RUMBLE_BOTH -> outputs.setRumble(strength, strength)
        TryChoice.RUMBLE_LEFT -> outputs.setRumble(strength, 0)
        TryChoice.RUMBLE_RIGHT -> outputs.setRumble(0, strength)
        TryChoice.RUMBLE_PATTERN ->
            outputs.playPattern(PatternId.HEARTBEAT, strength, strength, AppLimits.RUMBLE_PATTERN_DURATION_MS_MIN, AppLimits.RUMBLE_PULSE_RATE_HZ_DEFAULT)
        TryChoice.RUMBLE_STOP -> outputs.setRumble(0, 0)
        TryChoice.TRIGGER_R2 -> outputs.pressTrigger(true)
        TryChoice.TRIGGER_L2 -> outputs.pressTrigger(false)
        TryChoice.TRIGGER_OFF -> {
            outputs.clearTrigger(true)
            outputs.clearTrigger(false)
        }
        TryChoice.ALL_OFF -> allOff(outputs)
    }
}

// the same all-off the pad's release sends, spelled as the calls a panel makes
private fun allOff(outputs: OutputController) {
    outputs.setRumble(0, 0)
    outputs.clearTrigger(true)
    outputs.clearTrigger(false)
    outputs.setLightbar(BLACK)
    outputs.flushLightbar()
    outputs.setPlayerLeds(0)
    outputs.setMuteLed(MuteLed.OFF)
}
