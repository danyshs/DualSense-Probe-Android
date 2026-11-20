package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.Limits

/** One vibrator's share of a pattern, in the shape `VibrationEffect.createWaveform` takes. */
class Waveform(val vibratorId: Int, val timingsMs: LongArray, val amplitudes: IntArray)

private fun lane(id: Int, steps: List<RumbleStep>, pick: (RumbleStep) -> Int): Waveform? {
    val amplitudes = IntArray(steps.size) { pick(steps[it]) }
    // 0 means off; anything else is 1..255, never rounded down to off
    for (a in amplitudes) check(a in 0..Limits.MOTOR_STRENGTH_MAX) { "amplitude $a" }
    if (amplitudes.all { it == 0 }) return null
    return Waveform(id, LongArray(steps.size) { steps[it].durationMs.toLong() }, amplitudes)
}

/**
 * A pattern for the vibrators Android reports: the lower id is left, the next is right, one id plays
 * the stronger of the two, ids past two are unused. A vibrator that would never move is left out.
 */
fun waveformsFor(ids: List<Int>, steps: List<RumbleStep>): List<Waveform> {
    require(steps.isNotEmpty() && steps.size <= AppLimits.RUMBLE_STEPS_MAX) { "rumble steps ${steps.size}" }
    val sorted = ids.sorted()
    return when (sorted.size) {
        0 -> emptyList()
        1 -> listOfNotNull(lane(sorted[0], steps) { maxOf(it.left, it.right) })
        else -> listOfNotNull(lane(sorted[0], steps) { it.left }, lane(sorted[1], steps) { it.right })
    }
}
