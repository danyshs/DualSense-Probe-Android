package dev.dsprobe.app.output

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.RumbleStep
import dev.dsprobe.app.route.totalDurationMs
import dev.dsprobe.pad.Limits

enum class PatternId { CONSTANT, PULSE, RAMP_UP, RAMP_DOWN, HEARTBEAT, ALTERNATE, IMPACT, ENGINE }

/** How a pattern's cycle fills the time asked for. */
enum class Fill {
    REPEAT, // the cycle again and again, cut off at the duration
    RATED, // like REPEAT, but the cycle is resized to one pulse period of the rate slider
    STRETCH, // the cycle once, resized to span the whole duration
    ONCE, // the cycle once at its own length; the duration does not matter
}

/**
 * A pattern is data: its cycle as left, right, duration_ms steps (255 is the motor's intensity
 * ceiling).
 */
data class PatternDef(val fill: Fill, val cycle: List<RumbleStep>)

private const val MS_PER_SECOND = 1_000

private fun step(left: Int, right: Int, durationMs: Int) = RumbleStep(left, right, durationMs)

private val RAMP_LEVELS = listOf(26, 51, 77, 102, 128, 153, 179, 204, 230, 255)

val PATTERNS: Map<PatternId, PatternDef> = mapOf(
    PatternId.CONSTANT to PatternDef(Fill.REPEAT, listOf(step(255, 255, 1_000))),
    PatternId.PULSE to PatternDef(Fill.RATED, listOf(step(255, 255, 50), step(0, 0, 50))),
    PatternId.RAMP_UP to PatternDef(Fill.STRETCH, RAMP_LEVELS.map { step(it, it, 100) }),
    PatternId.RAMP_DOWN to
        PatternDef(Fill.STRETCH, RAMP_LEVELS.reversed().map { step(it, it, 100) }),
    PatternId.HEARTBEAT to
        PatternDef(
            Fill.REPEAT,
            listOf(step(255, 255, 80), step(0, 0, 100), step(180, 180, 80), step(0, 0, 740)),
        ),
    PatternId.ALTERNATE to PatternDef(Fill.REPEAT, listOf(step(255, 0, 200), step(0, 255, 200))),
    PatternId.IMPACT to PatternDef(Fill.ONCE, listOf(step(255, 255, 200))),
    PatternId.ENGINE to PatternDef(Fill.REPEAT, listOf(step(80, 255, 100), step(80, 90, 100))),
)

/**
 * The cycle with its durations scaled so they add up to [targetMs]; every step keeps at least 1 ms.
 */
private fun resized(cycle: List<RumbleStep>, targetMs: Int): List<RumbleStep> {
    val baseMs = totalDurationMs(cycle)
    var cumulativeMs = 0
    var previousEndMs = 0
    return cycle.map {
        cumulativeMs += it.durationMs
        val endMs = (cumulativeMs.toLong() * targetMs / baseMs).toInt()
        val durationMs = (endMs - previousEndMs).coerceAtLeast(1)
        previousEndMs = endMs
        it.copy(durationMs = durationMs)
    }
}

private fun scaled(cycle: List<RumbleStep>, leftMax: Int, rightMax: Int): List<RumbleStep> =
    cycle.map {
        it.copy(
            left = it.left * leftMax / Limits.MOTOR_STRENGTH_MAX,
            right =
            it.right * rightMax / Limits.MOTOR_STRENGTH_MAX,
        )
    }

/**
 * The cycle laid out over [durationMs]: repeated when [again], cut so nothing runs past the end.
 */
private fun laidOut(cycle: List<RumbleStep>, durationMs: Int, again: Boolean): List<RumbleStep> {
    val steps = ArrayList<RumbleStep>()
    var elapsedMs = 0
    do {
        for (beat in cycle) {
            if (elapsedMs >= durationMs) break
            val keptMs = minOf(beat.durationMs, durationMs - elapsedMs)
            steps.add(beat.copy(durationMs = keptMs))
            elapsedMs += keptMs
            check(steps.size <= AppLimits.RUMBLE_STEPS_MAX) {
                "pattern grew past ${AppLimits.RUMBLE_STEPS_MAX} steps"
            }
        }
    } while (again && elapsedMs < durationMs)
    return steps
}

/**
 * The timed steps of pattern [id]: each motor scaled to its intensity ceiling (0..255), laid out
 * over
 * [durationMs] (1 to 5 s). [rateHz] only matters to Pulse. The result is what the writer plays.
 */
fun buildPattern(
    id: PatternId,
    leftMax: Int,
    rightMax: Int,
    durationMs: Int,
    rateHz: Int,
): List<RumbleStep> {
    require(leftMax in 0..Limits.MOTOR_STRENGTH_MAX && rightMax in 0..Limits.MOTOR_STRENGTH_MAX) {
        "intensity $leftMax, $rightMax"
    }
    require(durationMs in AppLimits.RUMBLE_PATTERN_DURATION_MS_MIN..Limits.RUMBLE_DURATION_MS_MAX) {
        "pattern duration $durationMs ms"
    }
    require(rateHz in AppLimits.RUMBLE_PULSE_RATE_HZ_MIN..AppLimits.RUMBLE_PULSE_RATE_HZ_MAX) {
        "pulse rate $rateHz Hz"
    }
    val def = PATTERNS.getValue(id)
    val cycle = when (def.fill) {
        Fill.RATED -> resized(def.cycle, MS_PER_SECOND / rateHz)
        Fill.STRETCH -> resized(def.cycle, durationMs)
        Fill.REPEAT, Fill.ONCE -> def.cycle
    }
    val again = def.fill == Fill.REPEAT || def.fill == Fill.RATED
    val steps = laidOut(
        scaled(cycle, leftMax, rightMax),
        if (def.fill == Fill.ONCE) {
            Limits.RUMBLE_DURATION_MS_MAX
        } else {
            durationMs
        },
        again,
    )
    check(steps.isNotEmpty() && totalDurationMs(steps) <= Limits.RUMBLE_DURATION_MS_MAX) {
        "pattern $id built badly"
    }
    return steps
}
