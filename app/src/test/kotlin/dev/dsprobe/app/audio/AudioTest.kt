package dev.dsprobe.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTest {
    @Test fun peakLevelIsTheLoudestSampleOfTheFirstCountSamplesOnly() {
        val samples = shortArrayOf(100, -16_384, 200, Short.MIN_VALUE)
        assertEquals(0.5f, peakLevel(samples, 3), 0.0001f)
        assertEquals(1f, peakLevel(samples, 4), 0.0001f)
        assertEquals(0f, peakLevel(samples, 0), 0f)
    }

    @Test fun aToneHasTwoChannelsOfTheRightLengthAndStaysUnderItsAmplitude() {
        val tone = sineTone(48_000, 440, 1_000, 0.4f)
        assertEquals(48_000 * 2, tone.size)
        val peak = tone.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("peak $peak", peak in 10_000..(0.4f * Short.MAX_VALUE).toInt())
        for (i in 0 until tone.size step 2) {
            assertEquals(
                "both channels carry the same sample",
                tone[i],
                tone[
                    i +
                        1,
                ],
            )
        }
    }

    @Test fun theHeadphonesToneIsQuieterThanTheSpeakerTone() {
        val quiet = sineTone(48_000, 440, 100, 0.1f).maxOf { it.toInt() }
        val loud = sineTone(48_000, 440, 100, 0.4f).maxOf { it.toInt() }
        assertTrue(quiet < loud)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aFrequencyAboveHalfTheRateIsRefused() {
        sineTone(48_000, 30_000, 100, 0.1f)
    }
}
