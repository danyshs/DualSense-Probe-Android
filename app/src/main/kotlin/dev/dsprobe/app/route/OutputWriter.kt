package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
import dev.dsprobe.pad.OutputState
import dev.dsprobe.pad.RELEASE_FIELDS
import dev.dsprobe.pad.allOff
import dev.dsprobe.pad.buildUsbOutput
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Where finished reports go: true when the pad took all of it. */
fun interface ReportSink {
    fun send(report: ByteArray): Boolean
}

private sealed interface Plan {
    data object Done : Plan
    data class Wait(val ms: Long) : Plan
    class Send(val report: ByteArray, val fields: Set<OutputField>, val isFinal: Boolean) : Plan
}

/**
 * The one copy of what the pad should be doing, and the one thread that sends it. UI calls [change];
 * the writer thread takes the state and the dirty set together and sends one report for them, so a
 * newer value overwrites an older one per field and nothing is dropped. [finish] queues the all-off
 * report as the very last one: after any pending change, instead of any resend, and nothing after it.
 * Only fields the app has set are resent: entering Direct USB never repaints LEDs nobody asked for.
 */
class OutputWriter(
    private val sink: ReportSink,
    private val nowMs: () -> Long,
    private val onFailing: (String) -> Unit,
    private val log: (String) -> Unit,
) {
    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private var wakeNeeded = false
    private var state = OutputState.allOff()
    private val dirty: MutableSet<OutputField> = EnumSet.noneOf(OutputField::class.java)
    private val touched: MutableSet<OutputField> = EnumSet.noneOf(OutputField::class.java)
    private var lastSendMs: Long? = null
    private var closing = false
    private var finalPlanned = false
    private var failuresInARow = 0
    private var failingReported = false

    @Volatile
    private var halted = false

    @Volatile
    private var crashRequested = false

    @Volatile
    var sentCount = 0L
        private set

    @Volatile
    var failedCount = 0L
        private set

    @Volatile
    var droppedAfterCloseCount = 0L
        private set

    /** Edits the one state and marks [field] dirty. False (and counted) once the release has begun. */
    fun change(field: OutputField, edit: (OutputState) -> OutputState): Boolean = lock.withLock {
        if (closing) {
            droppedAfterCloseCount += 1
            return@withLock false
        }
        state = edit(state)
        dirty.add(field)
        touched.add(field)
        poke()
        true
    }

    /** Begins the release: the all-off report goes out last, then the writer is done. Safe to call twice. */
    fun finish() = lock.withLock {
        closing = true
        poke()
    }

    /** The crash path: nothing is sent after this, not even the all-off (the crash handler sends its own). */
    fun halt() {
        halted = true
        lock.withLock { poke() }
    }

    /** Debug button: the writer thread throws on its next pass, to prove the crash handler hands the pad back. */
    fun crashNow() {
        crashRequested = true
        lock.withLock { poke() }
    }

    /** The release report, built once the same way the writer builds it. */
    fun allOffReport(): ByteArray = buildUsbOutput(OutputState.allOff(), RELEASE_FIELDS)

    private fun poke() {
        wakeNeeded = true
        wake.signalAll()
    }

    /** What to do at [now], decided under the lock; sending happens outside it. */
    private fun plan(now: Long): Plan {
        if (halted) return Plan.Done
        val sinceSendMs = lastSendMs?.let { now - it } ?: Long.MAX_VALUE
        val finalDue = closing && !finalPlanned
        if (dirty.isNotEmpty() || finalDue) {
            if (sinceSendMs < Limits.OUTPUT_INTERVAL_MS_MIN) return Plan.Wait(Limits.OUTPUT_INTERVAL_MS_MIN - sinceSendMs)
            lastSendMs = now
            if (dirty.isNotEmpty()) return sendDirty()
            finalPlanned = true
            return Plan.Send(allOffReport(), RELEASE_FIELDS, isFinal = true)
        }
        if (closing) return Plan.Done
        if (touched.isEmpty()) return Plan.Wait(Limits.OUTPUT_RESEND_INTERVAL_MS.toLong())
        if (sinceSendMs < Limits.OUTPUT_RESEND_INTERVAL_MS) return Plan.Wait(Limits.OUTPUT_RESEND_INTERVAL_MS - sinceSendMs)
        lastSendMs = now
        return Plan.Send(buildUsbOutput(state, touched), EnumSet.copyOf(touched), isFinal = false)
    }

    private fun sendDirty(): Plan {
        val fields: Set<OutputField> = EnumSet.copyOf(dirty)
        dirty.clear()
        return Plan.Send(buildUsbOutput(state, fields), fields, isFinal = false)
    }

    private fun deliver(job: Plan.Send) {
        check(job.report.size == Limits.USB_OUTPUT_REPORT_BYTES) { "output report is ${job.report.size} bytes" }
        val ok = sink.send(job.report)
        lock.withLock {
            if (ok) {
                sentCount += 1
                failuresInARow = 0
                return
            }
            failedCount += 1
            failuresInARow += 1
            // a failed change goes out again on the next pass; a failed final report is logged, not retried
            if (!job.isFinal) dirty.addAll(job.fields)
        }
        log("output report not taken (${job.fields})")
        reportFailingOnce()
    }

    private fun reportFailingOnce() {
        val tell = lock.withLock {
            val over = failuresInARow >= AppLimits.SEND_FAILURES_IN_A_ROW_MAX && !failingReported
            if (over) failingReported = true
            over
        }
        if (tell) onFailing("output reports failing: ${AppLimits.SEND_FAILURES_IN_A_ROW_MAX} in a row")
    }

    /**
     * One pass: sends at most one report. Returns how long to wait before the next pass (0 = go
     * again now), or null when the writer has nothing more to do.
     */
    fun pumpOnce(): Long? {
        check(!crashRequested) { "debug crash requested on the writer thread" }
        val job = lock.withLock { plan(nowMs()) }
        return when (job) {
            Plan.Done -> null
            is Plan.Wait -> job.ms
            is Plan.Send -> {
                deliver(job)
                0L
            }
        }
    }

    private fun awaitWake(ms: Long) = lock.withLock {
        if (!wakeNeeded) wake.await(ms, TimeUnit.MILLISECONDS)
        wakeNeeded = false
    }

    /** The writer thread's body. Ends when the final report is out, or on [halt]. */
    fun run() {
        while (!halted) {
            val waitMs = pumpOnce() ?: return
            if (waitMs > 0L) awaitWake(waitMs)
        }
    }
}
