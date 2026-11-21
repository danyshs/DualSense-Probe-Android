package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.Limits

/** Both motors' strengths, 0..255. */
data class MotorLevels(val left: Int, val right: Int) {
    companion object {
        val OFF = MotorLevels(0, 0)
    }
}

/** One stretch of rumble: both strengths held for [durationMs]. A pattern is a list of these. */
data class RumbleStep(val left: Int, val right: Int, val durationMs: Int) {
    init {
        require(left in 0..Limits.MOTOR_STRENGTH_MAX && right in 0..Limits.MOTOR_STRENGTH_MAX) {
            "rumble step strength $left, $right"
        }
        require(durationMs in 1..Limits.RUMBLE_DURATION_MS_MAX) {
            "rumble step duration $durationMs ms"
        }
    }
}

/** Whole length of [steps]; the list is bounded, so the sum cannot overflow. */
fun totalDurationMs(steps: List<RumbleStep>): Int {
    require(steps.size <= AppLimits.RUMBLE_STEPS_MAX) { "rumble steps ${steps.size}" }
    var total = 0
    for (step in steps) total += step.durationMs
    return total
}

/** Which step is playing [elapsedMs] after the start; null once the last one has finished. */
fun stepIndexAt(steps: List<RumbleStep>, elapsedMs: Long): Int? {
    require(steps.size <= AppLimits.RUMBLE_STEPS_MAX) { "rumble steps ${steps.size}" }
    var endMs = 0L
    for ((i, step) in steps.withIndex()) {
        endMs += step.durationMs
        if (elapsedMs < endMs) return i
    }
    return null
}

/** Milliseconds from the start to the end of step [index]. */
fun stepEndMs(steps: List<RumbleStep>, index: Int): Int {
    require(index in steps.indices) { "step index $index of ${steps.size}" }
    return totalDurationMs(steps.subList(0, index + 1))
}
