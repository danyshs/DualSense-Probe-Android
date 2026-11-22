package dev.dsprobe.app.ui

import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import dev.dsprobe.app.debug.DebugRunner
import dev.dsprobe.app.debug.Readout
import dev.dsprobe.app.debug.ReadoutTracker
import dev.dsprobe.app.route.PadSession

/**
 * The two values the feed screens watch: the trace version, and the open guided card's live
 * readout, read from the pad's current state every frame.
 */
class FeedPump {
    val traceVersion: MutableLongState = mutableLongStateOf(0L)
    val readout: MutableState<Readout?> = mutableStateOf(null)
    private val tracker = ReadoutTracker()

    /** The gyroscope card's Reset button. */
    fun resetGyro() = tracker.resetGyro()

    /**
     * One pass per frame while a feed screen is on top: feed to trace, tour and runner, output
     * timers, then the two values.
     */
    suspend fun run(session: PadSession, runner: DebugRunner) {
        val hub = session.debug
        while (true) {
            withFrameMillis { frameMs ->
                hub.pump(runner)
                session.outputs.tick()
                if (traceVersion.longValue != hub.trace.version) {
                    traceVersion.longValue = hub.trace.version
                }
                val card = hub.tour.current
                val next = card?.let {
                    tracker.read(it, session.live.takeFrame().state, hub.trace.latestReport, frameMs)
                }
                if (readout.value != next) readout.value = next
            }
        }
    }
}
