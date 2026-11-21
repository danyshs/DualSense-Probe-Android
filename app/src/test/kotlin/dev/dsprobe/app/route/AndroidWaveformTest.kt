package dev.dsprobe.app.route

import dev.dsprobe.app.output.PatternId
import dev.dsprobe.app.output.buildPattern
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val STEPS = listOf(RumbleStep(200, 0, 100), RumbleStep(0, 50, 250), RumbleStep(1, 255, 40))

class AndroidWaveformTest {
    @Test fun twoVibratorsTheLowerIdIsLeftAndTheNextIsRight() {
        val lanes = waveformsFor(listOf(7, 3), STEPS)
        assertEquals(listOf(3, 7), lanes.map { it.vibratorId })
        assertArrayEquals(intArrayOf(200, 0, 1), lanes[0].amplitudes)
        assertArrayEquals(intArrayOf(0, 50, 255), lanes[1].amplitudes)
        for (lane in lanes) assertArrayEquals(longArrayOf(100, 250, 40), lane.timingsMs)
    }

    @Test fun oneVibratorPlaysTheStrongerOfTheTwoMotors() {
        val lanes = waveformsFor(listOf(4), STEPS)
        assertEquals(1, lanes.size)
        assertArrayEquals(intArrayOf(200, 50, 255), lanes.single().amplitudes)
    }

    @Test fun noVibratorsMeansNoWaveforms() {
        assertTrue(waveformsFor(emptyList(), STEPS).isEmpty())
    }

    @Test fun aVibratorThatWouldNeverMoveIsLeftOut() {
        val leftOnly = waveformsFor(listOf(0, 1), listOf(RumbleStep(90, 0, 100)))
        assertEquals(listOf(0), leftOnly.map { it.vibratorId })
        assertTrue(waveformsFor(listOf(0, 1), listOf(RumbleStep(0, 0, 100))).isEmpty())
    }

    @Test fun idsPastTwoAreUnused() {
        assertEquals(listOf(0, 1), waveformsFor(listOf(0, 1, 2), STEPS).map { it.vibratorId })
    }

    @Test fun aSmallStrengthIsNeverRoundedDownToOff() {
        val lane = waveformsFor(listOf(0, 1), listOf(RumbleStep(1, 1, 10))).first()
        assertArrayEquals(intArrayOf(1), lane.amplitudes)
    }

    @Test fun everyPatternBecomesTimingsThatMatchAndAmplitudesOffOrOneTo255() {
        for (id in PatternId.entries) {
            val steps = buildPattern(id, 255, 255, 5_000, 4)
            for (ids in listOf(listOf(0, 1), listOf(0))) {
                for (lane in waveformsFor(ids, steps)) {
                    assertEquals(steps.size, lane.timingsMs.size)
                    assertEquals(steps.size, lane.amplitudes.size)
                    assertEquals(totalDurationMs(steps).toLong(), lane.timingsMs.sum())
                    assertTrue("$id timings", lane.timingsMs.all { it >= 1 })
                    assertTrue("$id amplitudes", lane.amplitudes.all { it in 0..255 })
                    assertTrue("$id has a moving step", lane.amplitudes.any { it in 1..255 })
                }
            }
        }
    }

    @Test fun anEmptyPatternIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            waveformsFor(listOf(0, 1), emptyList())
        }
    }
}
