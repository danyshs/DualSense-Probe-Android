package dev.dsprobe.app.live

import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad

enum class MotionUnit { RAW_COUNTS, SI }

data class Motion(val x: Float, val y: Float, val z: Float, val unit: MotionUnit)

/** [x], [y] in 0..1 across the touchpad rectangle; [isPointer] when it is Android's mouse pointer scaled to the screen. */
data class TouchPoint(val id: Int, val x: Float, val y: Float, val isPointer: Boolean) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "touch point outside 0..1: $x, $y" }
    }
}

sealed interface Battery {
    data class Percent(val percent: Int) : Battery
    /** Level and charging nibbles as the pad sends them; their meaning is untested (P18). */
    data class RawNibble(val levelNibble: Int, val chargingNibble: Int) : Battery
    /** Android's own answer, shown as given when it isn't a usable percentage (measured on USB: capacity -0.01, status unknown). */
    data class AndroidRaw(val capacity: Float, val status: Int) : Battery
}

/**
 * Route-neutral pad state. Sticks run -1..1 with right and down positive, as Android's axes do;
 * triggers 0..1. Null means the current route can't read it.
 */
data class PadState(
    val leftX: Float,
    val leftY: Float,
    val rightX: Float,
    val rightY: Float,
    val l2: Float,
    val r2: Float,
    val dpad: Dpad,
    val held: Set<Button>,
    val gyro: Motion?,
    val accel: Motion?,
    val touches: List<TouchPoint>,
    val battery: Battery?,
    val headphonesIn: Boolean?,
    val sequence: Int?,
) {
    init {
        require(leftX in -1f..1f && leftY in -1f..1f && rightX in -1f..1f && rightY in -1f..1f) { "stick outside -1..1" }
        require(l2 in 0f..1f && r2 in 0f..1f) { "trigger outside 0..1" }
    }

    companion object {
        val EMPTY = PadState(
            leftX = 0f, leftY = 0f, rightX = 0f, rightY = 0f, l2 = 0f, r2 = 0f,
            dpad = Dpad.NONE, held = emptySet(), gyro = null, accel = null, touches = emptyList(),
            battery = null, headphonesIn = null, sequence = null,
        )
    }
}
