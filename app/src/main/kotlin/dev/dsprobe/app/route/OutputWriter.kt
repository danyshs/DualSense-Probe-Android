package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
import dev.dsprobe.pad.OutputState
import dev.dsprobe.pad.RELEASE_FIELDS
import dev.dsprobe.pad.RumbleMode
import dev.dsprobe.pad.TriggerEffect
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

/** Told after every send, on the writer thread: the report, what it carried, and whether the pad took it. Must not throw. */
fun interface SendObserver {
    fun sent(report: ByteArray, fields: Set<OutputField>, ok: Boolean, isFinal: Boolean, isResend: Boolean)
}

private sealed interface Plan {
    data object Done : Plan
    data class Wait(val ms: Long) : Plan
    class Send(
        val report: ByteArray,
        val fields: Set<OutputField>,
        val isFinal: Boolean,
        val rumble: MotorLevels?,
        val isResend: Boolean = false,
    ) : Plan
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
    private val observer: SendObserver = SendObserver { _, _, _, _, _ -> },
) {
    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private var wakeNeeded = false
    private var state = OutputState.allOff()
    private val dirty: MutableSet<OutputField> = EnumSet.noneOf(OutputField::class.java)
    private val touched: MutableSet<OutputField> = EnumSet.noneOf(OutputField::class.java)
    private var lastSendMs: Long? = null
    private var rumbleDeadlineMs: Long? = null
    private var rumbleSteps: List<RumbleStep>? = null
    private var rumbleStartMs = 0L
    private var rumbleStepIndex = -1
    private var rightTriggerDeadlineMs: Long? = null
    private var leftTriggerDeadlineMs: Long? = null
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

    /** The motor values in the last rumble-carrying report the pad took: what it was told, not what it felt. */
    @Volatile
    var rumbleSent = MotorLevels.OFF
        private set

    /** The rumble flags in use now. */
    val rumbleMode: RumbleMode get() = lock.withLock { state.rumbleMode }

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

    /** Picks the rumble flags the firmware understands. Not a change to send: nothing goes out until rumble is asked for. */
    fun setRumbleMode(mode: RumbleMode) = lock.withLock {
        state = state.copy(rumbleMode = mode)
    }

    /**
     * Rumble that stops by itself: the writer zeroes it at the deadline, so a stalled UI can't keep the
     * pad rumbling. Both motors at 0 means stop now. False (and counted) once the release has begun.
     */
    fun setRumble(left: UByte, right: UByte, durationMs: Int): Boolean = lock.withLock {
        require(durationMs in 1..Limits.RUMBLE_DURATION_MS_MAX) { "rumble duration $durationMs ms" }
        if (closing) {
            droppedAfterCloseCount += 1
            return@withLock false
        }
        rumbleSteps = null
        state = state.copy(rumbleLeft = left, rumbleRight = right)
        val stopped = left.toInt() == 0 && right.toInt() == 0
        rumbleDeadlineMs = if (stopped) null else nowMs() + durationMs
        dirty.add(OutputField.RUMBLE)
        touched.add(OutputField.RUMBLE)
        poke()
        true
    }

    /**
     * A rumble pattern, played by the writer itself: it moves to each step on time and zeroes both
     * motors when the last one ends, so a stalled UI can't keep the pad rumbling. The whole pattern
     * must fit inside [Limits.RUMBLE_DURATION_MS_MAX]. False (and counted) once the release has begun.
     */
    fun playRumble(steps: List<RumbleStep>): Boolean = lock.withLock {
        require(steps.isNotEmpty()) { "rumble pattern has no steps" }
        val totalMs = totalDurationMs(steps)
        require(totalMs <= Limits.RUMBLE_DURATION_MS_MAX) { "rumble pattern $totalMs ms" }
        if (closing) {
            droppedAfterCloseCount += 1
            return@withLock false
        }
        val now = nowMs()
        rumbleSteps = steps
        rumbleStartMs = now
        rumbleStepIndex = -1
        rumbleDeadlineMs = now + totalMs
        touched.add(OutputField.RUMBLE)
        advanceRumble(now)
        poke()
        true
    }

    /**
     * A trigger effect that ends by itself: the writer sets it back to off at the deadline, so a stalled
     * UI can't leave a trigger stiff. [TriggerEffect.OFF] clears it now. False (and counted) once the
     * release has begun.
     */
    fun setTrigger(isRight: Boolean, effect: TriggerEffect, durationMs: Int): Boolean = lock.withLock {
        require(durationMs in 1..Limits.TRIGGER_EFFECT_DURATION_MS_MAX) { "trigger duration $durationMs ms" }
        if (closing) {
            droppedAfterCloseCount += 1
            return@withLock false
        }
        val deadlineMs = if (effect == TriggerEffect.OFF) null else nowMs() + durationMs
        val field = if (isRight) OutputField.RIGHT_TRIGGER else OutputField.LEFT_TRIGGER
        if (isRight) {
            state = state.copy(r2Effect = effect)
            rightTriggerDeadlineMs = deadlineMs
        } else {
            state = state.copy(l2Effect = effect)
            leftTriggerDeadlineMs = deadlineMs
        }
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
        // nothing is sent after the final report, not even a step that came due since
        if (halted || finalPlanned) return Plan.Done
        expireRumble(now)
        expireTriggers(now)
        advanceRumble(now)
        val next = planSends(now)
        val wakeAt = listOfNotNull(nextRumbleWakeMs(), rightTriggerDeadlineMs, leftTriggerDeadlineMs).minOrNull()
        // never sleep past the moment the rumble steps on or a rumble or trigger has to stop
        if (next is Plan.Wait && wakeAt != null) return Plan.Wait(minOf(next.ms, (wakeAt - now).coerceAtLeast(1L)))
        return next
    }

    private fun nextRumbleWakeMs(): Long? {
        val deadline = rumbleDeadlineMs
        val steps = rumbleSteps
        if (steps == null || rumbleStepIndex < 0) return deadline
        val stepEnd = rumbleStartMs + stepEndMs(steps, rumbleStepIndex)
        return if (deadline == null) stepEnd else minOf(deadline, stepEnd)
    }

    private fun advanceRumble(now: Long) {
        val steps = rumbleSteps ?: return
        val index = stepIndexAt(steps, now - rumbleStartMs) ?: return
        if (index == rumbleStepIndex) return
        rumbleStepIndex = index
        state = state.copy(rumbleLeft = steps[index].left.toUByte(), rumbleRight = steps[index].right.toUByte())
        dirty.add(OutputField.RUMBLE)
    }

    private fun expireRumble(now: Long) {
        val deadline = rumbleDeadlineMs ?: return
        if (now < deadline) return
        state = state.copy(rumbleLeft = 0u, rumbleRight = 0u)
        rumbleDeadlineMs = null
        rumbleSteps = null
        rumbleStepIndex = -1
        dirty.add(OutputField.RUMBLE)
    }

    private fun expireTriggers(now: Long) {
        val right = rightTriggerDeadlineMs
        if (right != null && now >= right) {
            state = state.copy(r2Effect = TriggerEffect.OFF)
            rightTriggerDeadlineMs = null
            dirty.add(OutputField.RIGHT_TRIGGER)
        }
        val left = leftTriggerDeadlineMs
        if (left != null && now >= left) {
            state = state.copy(l2Effect = TriggerEffect.OFF)
            leftTriggerDeadlineMs = null
            dirty.add(OutputField.LEFT_TRIGGER)
        }
    }

    private fun planSends(now: Long): Plan {
        val sinceSendMs = lastSendMs?.let { now - it } ?: Long.MAX_VALUE
        val finalDue = closing && !finalPlanned
        if (dirty.isNotEmpty() || finalDue) {
            if (sinceSendMs < Limits.OUTPUT_INTERVAL_MS_MIN) return Plan.Wait(Limits.OUTPUT_INTERVAL_MS_MIN - sinceSendMs)
            lastSendMs = now
            if (dirty.isNotEmpty()) return sendDirty()
            finalPlanned = true
            return Plan.Send(allOffReport(), RELEASE_FIELDS, isFinal = true, rumble = MotorLevels.OFF)
        }
        if (closing) return Plan.Done
        if (touched.isEmpty()) return Plan.Wait(Limits.OUTPUT_RESEND_INTERVAL_MS.toLong())
        if (sinceSendMs < Limits.OUTPUT_RESEND_INTERVAL_MS) return Plan.Wait(Limits.OUTPUT_RESEND_INTERVAL_MS - sinceSendMs)
        lastSendMs = now
        return Plan.Send(buildUsbOutput(state, touched), EnumSet.copyOf(touched), isFinal = false, rumble = rumbleIn(touched), isResend = true)
    }

    private fun rumbleIn(fields: Set<OutputField>): MotorLevels? =
        if (OutputField.RUMBLE in fields) MotorLevels(state.rumbleLeft.toInt(), state.rumbleRight.toInt()) else null

    private fun sendDirty(): Plan {
        val fields: Set<OutputField> = EnumSet.copyOf(dirty)
        dirty.clear()
        return Plan.Send(buildUsbOutput(state, fields), fields, isFinal = false, rumble = rumbleIn(fields))
    }

    private fun deliver(job: Plan.Send) {
        check(job.report.size == Limits.USB_OUTPUT_REPORT_BYTES) { "output report is ${job.report.size} bytes" }
        val ok = sink.send(job.report)
        // tracing must never get in the way of the pad
        boundary("send observer", log) { observer.sent(job.report, job.fields, ok, job.isFinal, job.isResend) }
        lock.withLock {
            if (ok) {
                sentCount += 1
                failuresInARow = 0
                job.rumble?.let { rumbleSent = it }
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
