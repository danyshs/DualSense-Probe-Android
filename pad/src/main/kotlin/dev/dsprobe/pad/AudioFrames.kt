package dev.dsprobe.pad

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val PCM_FULL_SCALE = 32767.0
private const val PCM_PEAK_MAX = 32768
private const val RMS_SLACK = 1e-6
private const val FREQ_HZ_MAX = Limits.AUDIO_SAMPLE_RATE_HZ / 2 - 1
private const val FRAMES_PER_MS = Limits.AUDIO_SAMPLE_RATE_HZ / 1000
private const val FRAMES_MAX = Limits.AUDIO_TEST_DURATION_MS_MAX * FRAMES_PER_MS

/**
 * Interleaved 16-bit sine for the 4-channel 48 kHz OUT stream; channels outside [channels] stay
 * silent.
 */
fun toneFrames(freqHz: Int, amplitude: Float, channels: ChannelMask, frames: Int): ShortArray {
    require(freqHz in 1..FREQ_HZ_MAX) { "freq $freqHz Hz" }
    require(amplitude in 0f..Limits.AUDIO_AMPLITUDE_MAX) { "amplitude $amplitude" }
    require(channels.bits in 0 until (1 shl Limits.AUDIO_OUT_CHANNELS)) {
        "channel mask ${channels.bits}"
    }
    require(frames in 1..FRAMES_MAX) { "frames $frames" }
    val out = ShortArray(frames * Limits.AUDIO_OUT_CHANNELS)
    val radiansPerFrame = 2.0 * PI * freqHz / Limits.AUDIO_SAMPLE_RATE_HZ
    for (f in 0 until frames) {
        val sample = (sin(radiansPerFrame * f) * amplitude * PCM_FULL_SCALE).roundToInt().toShort()
        for (ch in 0 until Limits.AUDIO_OUT_CHANNELS) {
            if (channels.bits and (1 shl ch) != 0) out[f * Limits.AUDIO_OUT_CHANNELS + ch] = sample
        }
    }
    check(out.size == frames * Limits.AUDIO_OUT_CHANNELS) { "tone size" }
    return out
}

/**
 * Peak magnitude and RMS (in sample counts) of one channel of interleaved PCM: the mic level meter.
 */
fun peakAndRms(pcm: ShortArray, channel: Int, channelCount: Int): Pair<Int, Double> {
    require(channelCount > 0) { "channelCount $channelCount" }
    require(channel in 0 until channelCount) { "channel $channel of $channelCount" }
    require(pcm.isNotEmpty() && pcm.size % channelCount == 0) { "pcm size ${pcm.size}" }
    var peak = 0
    var sumSquares = 0.0
    val frames = pcm.size / channelCount
    for (f in 0 until frames) {
        val v = pcm[f * channelCount + channel].toInt()
        val magnitude = if (v < 0) -v else v
        if (magnitude > peak) peak = magnitude
        sumSquares += v.toDouble() * v
    }
    val rms = sqrt(sumSquares / frames)
    check(peak <= PCM_PEAK_MAX && rms <= peak + RMS_SLACK) { "meter bounds" }
    return Pair(peak, rms)
}
