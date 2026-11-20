package dev.dsprobe.app.ui

import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import dev.dsprobe.app.debug.DebugRunner
import dev.dsprobe.app.debug.rawBytes
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.pad.toHex

/** The two values the feed screens watch: the trace version, and the live raw bytes of the open guided card. */
class FeedPump {
    val traceVersion: MutableLongState = mutableLongStateOf(0L)
    val liveRaw: MutableState<String?> = mutableStateOf(null)

    /** One pass per frame while a feed screen is on top: feed to trace, tour and runner, output timers, then the two values. */
    suspend fun run(session: PadSession, runner: DebugRunner) {
        val hub = session.debug
        while (true) {
            withFrameMillis {
                hub.pump(runner)
                session.outputs.tick()
                if (traceVersion.longValue != hub.trace.version) traceVersion.longValue = hub.trace.version
                val card = hub.tour.current
                val report = hub.trace.latestReport
                val raw = if (card != null && card.rawByte >= 0 && report != null) rawBytes(card, report).toHex() else null
                if (liveRaw.value != raw) liveRaw.value = raw
            }
        }
    }
}
