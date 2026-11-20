package dev.dsprobe.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dsprobe.app.AppLimits
import kotlin.math.PI
import kotlin.math.sin

/** What a tone Try it shows: ready, playing, played, no device to play to, or an error. */
sealed interface ToneState {
    data object Idle : ToneState

    data object Playing : ToneState

    data object Played : ToneState

    data object NoDevice : ToneState

    data object Failed : ToneState
}

private const val CHANNELS_STEREO = 2
private const val MS_PER_SECOND = 1_000

/** A stereo 16-bit sine, both channels the same, [amplitudeRatio] of full scale. */
fun sineTone(rateHz: Int, freqHz: Int, durationMs: Long, amplitudeRatio: Float): ShortArray {
    require(rateHz > 0 && freqHz in 1 until rateHz / 2) { "rate $rateHz freq $freqHz" }
    require(durationMs in 1..AppLimits.TONE_MS * 10) { "duration $durationMs" }
    require(amplitudeRatio in 0f..1f) { "amplitude $amplitudeRatio" }
    val frames = (rateHz * durationMs / MS_PER_SECOND).toInt()
    val out = ShortArray(frames * CHANNELS_STEREO)
    for (i in 0 until frames) {
        val v = (sin(2.0 * PI * freqHz * i / rateHz) * amplitudeRatio * Short.MAX_VALUE).toInt().toShort()
        out[i * CHANNELS_STEREO] = v
        out[i * CHANNELS_STEREO + 1] = v
    }
    return out
}

/**
 * Plays a one-second tone to the pad's USB output as Android lists it, found by a live query on every tap.
 * With no such device it says so and stays ready, so a device that appears later works on the next tap.
 */
class AndroidTone(private val context: Context) {
    var state: ToneState by mutableStateOf(ToneState.Idle)
        private set
    private val main = Handler(Looper.getMainLooper())
    private var worker: Thread? = null

    private fun findOutput(audio: AudioManager): AudioDeviceInfo? =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }

    private fun post(next: ToneState) {
        main.post { state = next }
    }

    /** [quiet] is the headphones tone. */
    fun play(quiet: Boolean) {
        if (worker?.isAlive == true) return
        val audio = checkNotNull(context.getSystemService(AudioManager::class.java)) { "no AudioManager" }
        val device = findOutput(audio)
        if (device == null) {
            state = ToneState.NoDevice
            return
        }
        val ratio = if (quiet) AppLimits.TONE_HEADPHONES_AMPLITUDE_RATIO else AppLimits.TONE_SPEAKER_AMPLITUDE_RATIO
        val samples = sineTone(AppLimits.TONE_SAMPLE_RATE_HZ, AppLimits.TONE_FREQUENCY_HZ, AppLimits.TONE_MS, ratio)
        state = ToneState.Playing
        worker = Thread({ playOn(device, samples) }, "tone-test").also { it.start() }
    }

    /** Cuts a tone short. */
    fun stop() {
        worker?.interrupt()
        worker?.join(AppLimits.RELEASE_JOIN_TIMEOUT_MS)
        worker = null
        state = ToneState.Idle
    }

    // runs on the worker thread: write once, play, wait the tone's length, release
    private fun playOn(device: AudioDeviceInfo, samples: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AppLimits.TONE_SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            val written = track.write(samples, 0, samples.size)
            if (written != samples.size || !track.setPreferredDevice(device)) {
                post(ToneState.Failed)
                return
            }
            track.play()
            Thread.sleep(AppLimits.TONE_MS + AppLimits.TONE_TAIL_MS)
            post(ToneState.Played)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            track.release()
        }
    }
}
