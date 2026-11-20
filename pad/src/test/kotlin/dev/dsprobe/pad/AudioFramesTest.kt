package dev.dsprobe.pad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFramesTest {
    private val front = ChannelMask(0b0011)
    private val haptics = ChannelMask(0b1100)

    @Test fun toneFillsOnlyTheMaskedChannels() {
        val pcm = toneFrames(440, 0.25f, front, 480)
        assertEquals(480 * 4, pcm.size)
        for (f in 0 until 480) {
            assertEquals(0, pcm[f * 4 + 2].toInt())
            assertEquals(0, pcm[f * 4 + 3].toInt())
            assertEquals(pcm[f * 4], pcm[f * 4 + 1])
        }
        assertTrue(pcm.any { it.toInt() != 0 })
    }

    @Test fun hapticsMaskFillsChannels3And4() {
        val pcm = toneFrames(150, 0.25f, haptics, 480)
        for (f in 0 until 480) {
            assertEquals(0, pcm[f * 4].toInt())
            assertEquals(0, pcm[f * 4 + 1].toInt())
        }
        assertTrue(pcm.any { it.toInt() != 0 })
    }

    @Test fun toneStaysUnderTheAmplitudeCapAndHasTheRightPitch() {
        val pcm = toneFrames(480, 0.25f, front, 4_800) // 480 Hz: exactly 48 cycles in 100 ms
        val (peak, rms) = peakAndRms(pcm, channel = 0, channelCount = 4)
        assertTrue("peak ${peak}", peak in 8_000..8_192)
        assertEquals(peak / Math.sqrt(2.0), rms, peak * 0.01)
        var crossings = 0
        for (f in 1 until 4_800) if (pcm[(f - 1) * 4] < 0 && pcm[f * 4] >= 0) crossings++
        assertEquals(47, crossings) // the first cycle starts at 0, not after a negative sample
    }

    @Test fun zeroAmplitudeIsSilence() {
        assertTrue(toneFrames(440, 0f, front, 96).all { it.toInt() == 0 })
    }

    @Test fun toneRefusesBadArguments() {
        val cap = Limits.AUDIO_TEST_DURATION_MS_MAX * 48
        assertThrows(IllegalArgumentException::class.java) { toneFrames(0, 0.1f, front, 10) }
        assertThrows(IllegalArgumentException::class.java) { toneFrames(24_000, 0.1f, front, 10) }
        assertThrows(IllegalArgumentException::class.java) { toneFrames(440, 0.26f, front, 10) }
        assertThrows(IllegalArgumentException::class.java) { toneFrames(440, -0.1f, front, 10) }
        assertThrows(IllegalArgumentException::class.java) { toneFrames(440, 0.1f, ChannelMask(16), 10) }
        assertThrows(IllegalArgumentException::class.java) { toneFrames(440, 0.1f, front, 0) }
        assertThrows(IllegalArgumentException::class.java) { toneFrames(440, 0.1f, front, cap + 1) }
        assertEquals(cap * 4, toneFrames(440, 0.1f, front, cap).size)
    }

    @Test fun meterReadsPeakAndRmsOfOneChannel() {
        val pcm = shortArrayOf(100, -7, -300, 7, 0, -7, 200, 7) // 4 frames, 2 channels
        val (peak, rms) = peakAndRms(pcm, channel = 0, channelCount = 2)
        assertEquals(300, peak)
        assertEquals(Math.sqrt((100.0 * 100 + 300 * 300 + 0 + 200 * 200) / 4), rms, 1e-9)
        assertEquals(7, peakAndRms(pcm, channel = 1, channelCount = 2).first)
    }

    @Test fun meterHandlesFullScaleNegative() {
        assertEquals(32768, peakAndRms(shortArrayOf(Short.MIN_VALUE), 0, 1).first)
    }

    @Test fun meterRefusesBadArguments() {
        assertThrows(IllegalArgumentException::class.java) { peakAndRms(ShortArray(0), 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { peakAndRms(ShortArray(3), 0, 2) }
        assertThrows(IllegalArgumentException::class.java) { peakAndRms(ShortArray(4), 2, 2) }
        assertThrows(IllegalArgumentException::class.java) { peakAndRms(ShortArray(4), 0, 0) }
    }
}
