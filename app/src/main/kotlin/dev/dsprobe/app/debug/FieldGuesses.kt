package dev.dsprobe.app.debug

import dev.dsprobe.pad.FieldSource
import dev.dsprobe.pad.INPUT_MAP

/**
 * What one input field probably is, in three parts a card shows as one paragraph: what it is, what the ends
 * of its range mean, and how to make it move. Every word comes from a FACTS.md row; where the row says
 * the meaning is untested, the text says so and says that the user's answer settles it.
 */
data class FieldGuess(val title: String, val what: String, val ends: String, val move: String) {
    init {
        require(title.isNotBlank() && what.isNotBlank() && ends.isNotBlank() && move.isNotBlank()) { "empty part in guess '$title'" }
    }

    /** The paragraph under the card's title. */
    val text: String get() = "$what $ends $move"
}

private fun bitOf(mask: Int): Int = Integer.numberOfTrailingZeros(mask)

private fun heldEnds(src: FieldSource): String {
    val bit = bitOf(checkNotNull(src.bitMask) { src.id })
    return "Bit $bit of byte ${src.byteOffset} reads 1 while it is held and 0 when it is let go."
}

private class ButtonText(val title: String, val what: String, val move: String)

// P13, P14, P15: the plain names; the bit position comes from INPUT_MAP
private val BUTTON_TEXT: Map<String, ButtonText> = mapOf(
    "square" to ButtonText("Square button", "The Square face button, the left one of the four.", "Press Square."),
    "cross" to ButtonText("Cross button", "The Cross face button, the bottom one of the four.", "Press Cross."),
    "circle" to ButtonText("Circle button", "The Circle face button, the right one of the four.", "Press Circle."),
    "triangle" to ButtonText("Triangle button", "The Triangle face button, the top one of the four.", "Press Triangle."),
    "l1" to ButtonText("L1 shoulder button", "The left shoulder button, L1.", "Press L1."),
    "r1" to ButtonText("R1 shoulder button", "The right shoulder button, R1.", "Press R1."),
    "l2" to ButtonText(
        "L2, click flag",
        "A yes or no flag for the left trigger, separate from its 0-255 travel value.",
        "Squeeze L2 slowly and watch where the flag flips; FACTS does not say at what point.",
    ),
    "r2" to ButtonText(
        "R2, click flag",
        "A yes or no flag for the right trigger, separate from its 0-255 travel value.",
        "Squeeze R2 slowly and watch where the flag flips; FACTS does not say at what point.",
    ),
    "create" to ButtonText("Create button", "The small Create button left of the touchpad.", "Press Create."),
    "options" to ButtonText("Options button", "The small Options button right of the touchpad.", "Press Options."),
    "l3" to ButtonText("L3, left stick click", "The left stick pressed straight down.", "Push the left stick down like a button."),
    "r3" to ButtonText("R3, right stick click", "The right stick pressed straight down.", "Push the right stick down like a button."),
    "ps" to ButtonText("PS button", "The PlayStation logo button in the middle.", "Press the PS button."),
    "touchpad_click" to ButtonText("Touchpad click", "The whole touchpad pressed down like a button.", "Press the touchpad down until it clicks."),
    "mute" to ButtonText("Mute button", "The microphone Mute button.", "Press Mute."),
)

private fun buttonGuess(src: FieldSource): FieldGuess {
    val t = checkNotNull(BUTTON_TEXT[src.id.removePrefix("in.button.")]) { "no button text for ${src.id}" }
    return FieldGuess(t.title, t.what, heldEnds(src), t.move)
}

private fun stickGuess(side: String, axis: String): FieldGuess {
    val lower = side.lowercase()
    if (axis == "x") {
        return FieldGuess("$side stick, left↔right", "How far the $lower stick is pushed sideways.", "0 = full left, 128 = centre, 255 = full right.", "Push the $lower stick sideways.")
    }
    return FieldGuess(
        "$side stick, up↕down",
        "How far the $lower stick is pushed up or down.",
        "0 and 255 are the two ends and 128 is centre. FACTS does not say which end is up: the live value shows it.",
        "Push the $lower stick up, then down.",
    )
}

private fun triggerGuess(side: String): FieldGuess = FieldGuess(
    "$side trigger, travel",
    "How far the $side trigger is squeezed.",
    "0 = untouched, 255 = squeezed all the way.",
    "Squeeze $side slowly from rest to the end.",
)

private fun motionGuess(isGyro: Boolean, axis: String): FieldGuess {
    val name = if (isGyro) "Gyroscope" else "Accelerometer"
    val what = if (isGyro) "How fast the pad turns around the $axis axis of the gyroscope." else "The push or gravity along the $axis axis of the accelerometer."
    val still = if (isGyro) "A signed 16-bit number, 0 = not turning." else "A signed 16-bit number; a pad lying still shows gravity on one axis."
    val ends = "$still Which way the $axis axis points, its sign and its scale are untested: your answer settles them."
    val move = if (isGyro) "Turn the pad slowly in the air, one direction at a time, and see whether $axis moves." else "Tip the pad so each side points down in turn and see whether $axis moves."
    return FieldGuess("$name, ${axis.uppercase()} axis", what, ends, move)
}

private fun touchGuess(point: Int, field: String): FieldGuess {
    val n = point + 1
    val who = if (point == 0) "the first finger" else "the second finger"
    val second = if (point == 0) "" else " Keep a first finger down while you do it."
    return when (field) {
        "id" -> FieldGuess(
            "Finger $n, contact id", "A number that names $who on the touchpad.", "7 bits, 0-127.",
            "Touch with $who, lift it, touch again and see whether the id changes.$second",
        )
        "active" -> FieldGuess(
            "Finger $n, touching flag", "Whether $who is on the pad.", "Bit 7 reads 1 when NOT touching and 0 while a finger is down.",
            "Put $who on the pad, then lift it.$second",
        )
        "x.low8" -> FieldGuess(
            "Finger $n, x position (low part)", "The low 8 bits of where $who is, left to right.", "Together with the high part, x runs 0-1919.",
            "Slide $who sideways across the pad.$second",
        )
        "x.high4" -> FieldGuess(
            "Finger $n, x position (high part)", "The top 4 bits of where $who is, left to right.", "Reads 0-15; it changes only on big sideways moves.",
            "Slide $who from one side edge to the other.$second",
        )
        "y.low4" -> FieldGuess(
            "Finger $n, y position (low part)", "The low 4 bits of where $who is, top to bottom.", "Reads 0-15; it changes on small up and down moves.",
            "Slide $who a little up and down.$second",
        )
        "y.high8" -> FieldGuess(
            "Finger $n, y position (high part)", "The top 8 bits of where $who is, top to bottom.", "Together with the low part, y runs 0-1079.",
            "Slide $who from the top edge to the bottom edge.$second",
        )
        else -> error("no touch text for $field")
    }
}

private val OTHER_GUESSES: Map<String, FieldGuess> = mapOf(
    "in.sequence" to FieldGuess(
        "Report counter", "A counter that goes up by one with every report the pad sends.", "It wraps from 255 back to 0.",
        "Nothing to press: it counts by itself while Direct USB reports arrive.",
    ),
    "in.dpad" to FieldGuess(
        "D-pad direction", "The D-pad as one number.",
        "0 = up, then clockwise: 1 up-right, 2 right, 3 down-right, 4 down, 5 down-left, 6 left, 7 up-left; 8 = nothing pressed.",
        "Press each D-pad direction, then each corner.",
    ),
    "in.battery.level" to FieldGuess(
        "Battery level", "The battery level as a 4-bit number.", "Readings of 8 and 9 were seen; how that maps to a percentage is untested: your answer settles it.",
        "Nothing to press: it drifts as the battery drains or charges, so just watch it.",
    ),
    "in.battery.charging" to FieldGuess(
        "Battery charging state", "The charging state as a 4-bit number.", "A reading of 2 was seen on USB; what each value means is untested: your answer settles it.",
        "It changes when charging starts or stops, which a USB cable makes hard to cause, so just watch it.",
    ),
    "in.headphones_in" to FieldGuess(
        "Headphones plugged in", "Whether something is in the pad's headphone jack.", "1 = plugged in, 0 = nothing there.",
        "Plug headphones into the pad, then pull them out.",
    ),
    "in.mic_in" to FieldGuess(
        "Headset microphone plugged in", "Whether a microphone is attached to the headphone jack.", "1 = attached, 0 = not; this bit is untested: your answer settles it.",
        "Plug a headset that has a microphone into the pad.",
    ),
)

private fun guessFor(src: FieldSource): FieldGuess {
    val id = src.id
    return when {
        id.startsWith("in.button.") -> buttonGuess(src)
        id.startsWith("in.stick.") -> stickGuess(if (id.contains(".left.")) "Left" else "Right", id.substringAfterLast('.'))
        id.startsWith("in.trigger.") -> triggerGuess(if (id.endsWith("l2")) "L2" else "R2")
        id.startsWith("in.gyro.") -> motionGuess(true, id.substringAfterLast('.'))
        id.startsWith("in.accel.") -> motionGuess(false, id.substringAfterLast('.'))
        id.startsWith("in.touch.") -> {
            val point = id.split('.')[2].toInt()
            touchGuess(point, id.substringAfter("in.touch.$point."))
        }
        else -> checkNotNull(OTHER_GUESSES[id]) { "no guess for $id" }
    }
}

/** One guess per INPUT_MAP entry, built once; a field with no text fails here, at start, not on a card. */
val INPUT_GUESSES: Map<String, FieldGuess> = INPUT_MAP.associate { it.id to guessFor(it) }
