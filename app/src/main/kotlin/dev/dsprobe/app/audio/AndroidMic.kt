package dev.dsprobe.app.audio

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dsprobe.app.AppLimits
import kotlin.math.abs

/** What the mic Try it shows: nothing yet, a live level, the loudest level it reached, or why it could not record. */
sealed interface MicState {
    data object Idle : MicState

    data class Recording(val level: Float, val msLeft: Long) : MicState

    data class Done(val peak: Float) : MicState

    data class Failed(val why: MicFailure, val code: Int = 0) : MicState
}

/** Why a recording did not happen; the card turns each into a plain sentence. */
enum class MicFailure { PERMISSION_REFUSED, NO_DEVICE, OPEN_FAILED, READ_ERROR }

/** The loudest sample in the first [count] of [samples], 0 (silence) to 1 (full scale). */
fun peakLevel(samples: ShortArray, count: Int): Float {
    require(count in 0..samples.size) { "count $count of ${samples.size}" }
    var peak = 0
    for (i in 0 until count) peak = maxOf(peak, abs(samples[i].toInt()))
    return (peak / AppLimits.MIC_SAMPLE_PEAK).coerceIn(0f, 1f)
}

private const val CHANNELS_STEREO = 2

/**
 * Records from the pad's microphone as Android lists it (a USB headset input) for [AppLimits.MIC_RECORD_MS],
 * shows a live level and keeps nothing. One recording at a time, on its own thread. [state] is main thread state.
 */
class AndroidMic(private val context: Context) {
    var state: MicState by mutableStateOf(MicState.Idle)
        private set
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var stopRequested = false
    private var worker: Thread? = null

    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** The pad's USB headset input, asked fresh each time. */
    private fun findInput(audio: AudioManager): AudioDeviceInfo? =
        audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }

    private fun post(next: MicState) {
        main.post { state = next }
    }

    /** Starts a recording. The caller has the permission; without it this says so on the card and records nothing. */
    fun start() {
        if (worker?.isAlive == true) return
        if (!hasPermission()) {
            refuse()
            return
        }
        val audio = checkNotNull(context.getSystemService(AudioManager::class.java)) { "no AudioManager" }
        val device = findInput(audio)
        if (device == null) {
            state = MicState.Failed(MicFailure.NO_DEVICE)
            return
        }
        stopRequested = false
        state = MicState.Recording(0f, AppLimits.MIC_RECORD_MS)
        worker = Thread({ record(device) }, "mic-test").also { it.start() }
    }

    /** The user said no to the permission question: the card says why nothing was recorded. */
    fun refuse() {
        state = MicState.Failed(MicFailure.PERMISSION_REFUSED)
    }

    /** Ends a recording early; the card goes back to ready. */
    fun stop() {
        stopRequested = true
        worker?.join(AppLimits.RELEASE_JOIN_TIMEOUT_MS)
        worker = null
        state = MicState.Idle
    }

    private fun open(device: AudioDeviceInfo): AudioRecord? {
        if (!hasPermission()) return null
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AppLimits.MIC_SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBytes = AudioRecord.getMinBufferSize(AppLimits.MIC_SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return null
        // the permission is checked above; a revoke between the check and here is handled, not assumed away
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBytes, AppLimits.MIC_READ_SAMPLES * Short.SIZE_BYTES * CHANNELS_STEREO))
                .build()
        } catch (e: SecurityException) {
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED || !record.setPreferredDevice(device)) {
            record.release()
            return null
        }
        return record
    }

    // runs on the worker thread; the loop ends by time, by stop, or by a read error
    private fun record(device: AudioDeviceInfo) {
        val record = open(device)
        if (record == null) {
            post(MicState.Failed(MicFailure.OPEN_FAILED))
            return
        }
        val buffer = ShortArray(AppLimits.MIC_READ_SAMPLES * CHANNELS_STEREO)
        val startMs = SystemClock.uptimeMillis()
        var peak = 0f
        var failure: Int? = null
        try {
            record.startRecording()
            while (!stopRequested && SystemClock.uptimeMillis() - startMs < AppLimits.MIC_RECORD_MS) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) {
                    failure = read
                    break
                }
                val level = peakLevel(buffer, read)
                peak = maxOf(peak, level)
                val left = (AppLimits.MIC_RECORD_MS - (SystemClock.uptimeMillis() - startMs)).coerceAtLeast(0L)
                post(MicState.Recording(level, left))
            }
        } finally {
            record.stop()
            record.release()
        }
        if (!stopRequested) post(failure?.let { MicState.Failed(MicFailure.READ_ERROR, it) } ?: MicState.Done(peak))
    }
}
