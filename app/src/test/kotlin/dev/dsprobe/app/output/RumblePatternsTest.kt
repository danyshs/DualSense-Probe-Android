package dev.dsprobe.app.output

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.RumbleStep
import dev.dsprobe.app.route.totalDurationMs
import dev.dsprobe.pad.Limits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

private val DURATIONS_MS = listOf(1_000, 2_500, 5_000)
private val RATES_HZ = listOf(AppLimits.RUMBLE_PULSE_RATE_HZ_MIN, AppLimits.RUMBLE_PULSE_RATE_HZ_DEFAULT, AppLimits.RUMBLE_PULSE_RATE_HZ_MAX)

class RumblePatternsTest {
    private fun build(id: PatternId, left: Int = 255, right: Int = 255, ms: Int = 5_000, hz: Int = 4) = buildPattern(id, left, right, ms, hz)

    @Test fun everyPatternHasATableEntry() {
        assertEquals(PatternId.entries.toSet(), PATTERNS.keys)
        for ((id, def) in PATTERNS) assertTrue("$id has steps", def.cycle.isNotEmpty())
    }

    @Test fun theTableValuesAreAllWithin0To255() {
        for ((id, def) in PATTERNS) {
            for (s in def.cycle) assertTrue("$id $s", s.left in 0..255 && s.right in 0..255 && s.durationMs > 0)
        }
    }

    @Test fun everyBuiltPatternStaysInsideTheValueRangeAndTheRumbleCap() {
        for (id in PatternId.entries) for (ms in DURATIONS_MS) for (hz in RATES_HZ) {
            val steps = build(id, ms = ms, hz = hz)
            assertTrue("$id $ms $hz", steps.isNotEmpty() && steps.size <= AppLimits.RUMBLE_STEPS_MAX)
            for (s in steps) assertTrue("$id $s", s.left in 0..255 && s.right in 0..255 && s.durationMs >= 1)
            val total = totalDurationMs(steps)
            assertTrue("$id total $total", total in 1..minOf(ms, Limits.RUMBLE_DURATION_MS_MAX))
        }
    }

    @Test fun everyPatternExceptImpactFillsItsWholeDuration() {
        for (id in PatternId.entries - PatternId.IMPACT) for (ms in DURATIONS_MS) {
            assertEquals("$id $ms", ms, totalDurationMs(build(id, ms = ms)))
        }
    }

    @Test fun impactIsOneShortBurstWhateverTheDuration() {
        val steps = build(PatternId.IMPACT, ms = 5_000)
        assertEquals(listOf(RumbleStep(255, 255, 200)), steps)
    }

    @Test fun intensityIsACeilingPerMotor() {
        for (id in PatternId.entries) {
            val steps = build(id, left = 100, right = 40)
            assertTrue(id.name, steps.all { it.left <= 100 && it.right <= 40 })
            assertTrue(id.name, steps.any { it.left > 0 || it.right > 0 })
        }
        assertTrue(build(PatternId.CONSTANT, left = 0, right = 90).all { it.left == 0 && it.right == 90 })
    }

    @Test fun pulseRateSetsThePeriod() {
        val slow = build(PatternId.PULSE, hz = 1, ms = 2_000)
        val fast = build(PatternId.PULSE, hz = 10, ms = 2_000)
        assertEquals(listOf(500, 500, 500, 500), slow.map { it.durationMs })
        assertEquals(50, fast[0].durationMs)
        assertEquals(40, fast.size)
        assertEquals(listOf(255, 0), slow.take(2).map { it.left })
    }

    @Test fun rampUpNeverFallsAndRampDownNeverRises() {
        val up = build(PatternId.RAMP_UP).map { it.left }
        val down = build(PatternId.RAMP_DOWN).map { it.left }
        assertEquals(up.sorted(), up)
        assertEquals(down.sortedDescending(), down)
        assertTrue(up.first() < up.last() && down.first() > down.last())
        assertEquals(255, up.last())
    }

    @Test fun alternateNeverRunsBothMotorsAtOnce() {
        val steps = build(PatternId.ALTERNATE)
        assertTrue(steps.all { (it.left == 0) != (it.right == 0) })
        assertTrue(steps.any { it.left > 0 } && steps.any { it.right > 0 })
    }

    @Test fun engineKeepsTheLeftMotorSteadyAndPulsesTheRight() {
        val steps = build(PatternId.ENGINE)
        assertEquals(1, steps.map { it.left }.toSet().size)
        assertTrue(steps.map { it.right }.toSet().size > 1)
    }

    @Test fun heartbeatHasTwoBeatsAndARestInEachSecond() {
        val first = build(PatternId.HEARTBEAT, ms = 1_000)
        assertEquals(listOf(255, 0, 180, 0), first.map { it.left })
    }

    @Test fun anOutOfRangeAskIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { build(PatternId.PULSE, left = 256) }
        assertThrows(IllegalArgumentException::class.java) { build(PatternId.PULSE, ms = 999) }
        assertThrows(IllegalArgumentException::class.java) { build(PatternId.PULSE, ms = 5_001) }
        assertThrows(IllegalArgumentException::class.java) { build(PatternId.PULSE, hz = 0) }
        assertThrows(IllegalArgumentException::class.java) { build(PatternId.PULSE, hz = 11) }
    }
}
