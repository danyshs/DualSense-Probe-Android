package dev.dsprobe.app.debug

import dev.dsprobe.pad.FieldSource
import dev.dsprobe.pad.INPUT_MAP

private const val HELD_LINE = "1 while held, 0 when let go."
private const val FLAG_LINE =
    "A yes or no flag, separate from the travel value; FACTS does not say at what point it flips."

// P13, P14, P15: the plain names; where each bit lives comes from INPUT_MAP
private val BUTTON_LABELS: Map<String, String> = mapOf(
    "square" to "Square",
    "cross" to "Cross",
    "circle" to "Circle",
    "triangle" to "Triangle",
    "l1" to "L1",
    "r1" to "R1",
    "l2" to "L2 click flag",
    "r2" to "R2 click flag",
    "create" to "Create",
    "options" to "Options",
    "l3" to "L3 stick click",
    "r3" to "R3 stick click",
    "ps" to "PS",
    "touchpad_click" to "Touchpad click",
    "mute" to "Mute",
)

private fun buttonGuess(src: FieldSource): FieldGuess {
    val name = src.id.removePrefix("in.button.")
    val label = checkNotNull(BUTTON_LABELS[name]) { "no button label for ${src.id}" }
    return FieldGuess(label, if (name == "l2" || name == "r2") FLAG_LINE else HELD_LINE)
}

private val STICK_GUESSES: Map<String, FieldGuess> = mapOf(
    "x" to FieldGuess(
        "x, left↔right",
        "0 = full left, 128 = centre, 255 = full right.",
        "-1.0 = full left, 0.0 = centre, +1.0 = full right.",
    ),
    "y" to FieldGuess(
        "y, up↕down",
        "0 and 255 are the two ends and 128 is centre. FACTS does not say which end is up: the " +
            "live value shows it.",
        "-1.0 and +1.0 are the two ends and 0.0 is centre. FACTS does not say which end is up: " +
            "the live value shows it.",
    ),
)

private val TRIGGER_GUESS = FieldGuess(
    "travel",
    "0 = untouched, 255 = squeezed all the way.",
    "0.0 = untouched, 1.0 = squeezed all the way.",
)

private val MOTION_SHAPE: Map<String, String> = mapOf(
    "x" to "A signed number, drawn as a left↔right bar.",
    "y" to "A signed number, drawn as an up↕down bar.",
    "z" to "A signed number, drawn as an up↕down bar.",
)

private fun motionGuess(axis: String): FieldGuess = FieldGuess(
    "$axis axis",
    checkNotNull(MOTION_SHAPE[axis]) { "no motion axis $axis" },
)

// P17: one line per part of a finger, the same for both fingers
private val TOUCH_GUESSES: Map<String, FieldGuess> = mapOf(
    "id" to FieldGuess("contact id", "7 bits, 0-127; it may change when you lift and touch again."),
    "active" to FieldGuess(
        "touching flag",
        "Bit 7 reads 1 when NOT touching and 0 while a finger is down.",
    ),
    "x.low8" to FieldGuess(
        "x, low part",
        "The low 8 bits of left to right; with the high part, x runs 0-1919.",
    ),
    "x.high4" to FieldGuess(
        "x, high part",
        "The top 4 bits of left to right, 0-15; it changes only on big sideways moves.",
    ),
    "y.low4" to FieldGuess(
        "y, low part",
        "The low 4 bits of top to bottom, 0-15; it changes on small up and down moves.",
    ),
    "y.high8" to FieldGuess(
        "y, high part",
        "The top 8 bits of top to bottom; with the low part, y runs 0-1079.",
    ),
)

private val OTHER_GUESSES: Map<String, FieldGuess> = mapOf(
    "in.sequence" to FieldGuess("counter", "It wraps from 255 back to 0."),
    "in.dpad" to FieldGuess(
        "direction",
        "0 = up, then clockwise: 1 up-right, 2 right, 3 down-right, 4 down, 5 down-left, 6 left, " +
            "7 up-left; 8 = nothing pressed.",
    ),
    "in.battery.level" to FieldGuess(
        "level",
        "Readings of 8 and 9 were seen; how that maps to a percentage is untested: your answer " +
            "settles it.",
    ),
    "in.battery.charging" to FieldGuess(
        "charging state",
        "A reading of 2 was seen on USB; what each value means is untested: your answer settles " +
            "it.",
    ),
    "in.headphones_in" to FieldGuess("headphones", "1 = plugged in, 0 = nothing there."),
    "in.mic_in" to FieldGuess(
        "headset microphone",
        "1 = attached, 0 = not; this bit is untested: your answer settles it.",
    ),
)

private fun guessFor(src: FieldSource): FieldGuess {
    val id = src.id
    val axis = id.substringAfterLast('.')
    return when {
        id.startsWith("in.button.") -> buttonGuess(src)

        id.startsWith("in.stick.") -> checkNotNull(STICK_GUESSES[axis]) { "no stick guess for $id" }

        id.startsWith("in.trigger.") -> TRIGGER_GUESS

        id.startsWith("in.gyro.") || id.startsWith("in.accel.") -> motionGuess(axis)

        id.startsWith("in.touch.") -> {
            val part = id.removePrefix("in.touch.").substringAfter('.')
            checkNotNull(TOUCH_GUESSES[part]) { "no touch guess for $id" }
        }

        else -> checkNotNull(OTHER_GUESSES[id]) { "no guess for $id" }
    }
}

/**
 * One guess per INPUT_MAP entry, built once; a field with no text fails here, at start, not on a
 * card.
 */
val INPUT_GUESSES: Map<String, FieldGuess> = INPUT_MAP.associate { it.id to guessFor(it) }
