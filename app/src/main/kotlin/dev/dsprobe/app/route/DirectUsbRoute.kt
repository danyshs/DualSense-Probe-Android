package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
import dev.dsprobe.pad.ParseResult
import dev.dsprobe.pad.RumbleMode
import dev.dsprobe.pad.parseFirmwareInfo
import dev.dsprobe.pad.parseUsbInput
import dev.dsprobe.pad.supportsRumbleV2
import java.util.concurrent.atomic.AtomicBoolean

private const val NANOS_PER_MS = 1_000_000L
private const val READER_NAME = "dsprobe-usb-reader"
private const val WRITER_NAME = "dsprobe-usb-writer"

/**
 * Direct USB: interface 3 is claimed. A reader thread turns input reports into [LiveState]; the
 * [writer] is the only thing that sends. [onFault] is called from those threads and must hop to the
 * main thread; the route never releases itself on a fault (the controller asks for it).
 */
class DirectUsbRoute(
    private val link: UsbLink,
    private val live: LiveState,
    private val feed: EventFeed,
    private val onFault: (String) -> Unit,
    private val log: (String) -> Unit,
    nowMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
) : PadRoute {
    val writer = OutputWriter({ link.writeReport(it) }, nowMs, ::fault, log)
    private val readerStop = AtomicBoolean(false)
    private val faulted = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val linkReleased = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private var writerThread: Thread? = null

    // a Long cannot wrap in a session at a few hundred reports a second
    @Volatile
    var reportsRead = 0L
        private set

    @Volatile
    var rejectedCount = 0L
        private set

    @Volatile
    var lateDropCount = 0
        private set

    companion object {
        /** The crash handler's one way to the open claim: set on start, cleared on release. */
        @Volatile
        var current: DirectUsbRoute? = null
            private set
    }

    /** Starts both threads. Once. */
    fun start() {
        check(readerThread == null && writerThread == null) { "route already started" }
        check(current == null) { "another Direct USB route is open" }
        live.reset()
        pickRumbleMode()
        current = this
        readerThread = Thread(::readLoop, READER_NAME).also { it.start() }
        writerThread = Thread(writer::run, WRITER_NAME).also { it.start() }
    }

    /** Reads the firmware once, before the threads start, and picks the rumble flags it understands. */
    private fun pickRumbleMode() {
        val reply = askOrNull("firmware read", log) {
            link.readFeature(Limits.USB_FEATURE_FIRMWARE_REPORT_ID, Limits.USB_FEATURE_FIRMWARE_REPORT_BYTES) ?: ByteArray(0)
        }
        val info = reply?.let(::parseFirmwareInfo)
        if (info == null) {
            log("firmware report unreadable: rumble stays in compatible mode")
            return
        }
        val mode = if (info.supportsRumbleV2()) RumbleMode.V2 else RumbleMode.COMPATIBLE
        writer.setRumbleMode(mode)
        log("firmware update version 0x%04x, rumble %s".format(info.updateVersion.toInt(), mode))
    }

    private fun fault(detail: String) {
        // one fault is enough: the controller releases, and everything after it is noise
        if (faulted.compareAndSet(false, true)) onFault(detail)
    }

    private fun readLoop() {
        live.setWriter(Thread.currentThread())
        try {
            while (!readerStop.get()) {
                when (val r = link.readReport(AppLimits.READ_TIMEOUT_MS)) {
                    ReadResult.Timeout -> Unit
                    is ReadResult.Report -> onReport(r.bytes)
                    is ReadResult.Failed -> {
                        readerStop.set(true)
                        fault("read failed: ${r.detail}")
                    }
                }
            }
        } catch (e: RuntimeException) {
            // a callback or the claim going away under us after stop is expected; before stop it is a fault
            if (readerStop.get()) lateDropCount = nextLateDropCount(lateDropCount) else fault("reader threw $e")
            readerStop.set(true)
        }
    }

    private fun onReport(bytes: ByteArray) {
        if (readerStop.get()) return
        if (feed.isActive) feed.publish(FeedEvent(System.nanoTime(), Route.DIRECT_USB, bytes, null))
        when (val parsed = parseUsbInput(bytes)) {
            is ParseResult.Ok -> {
                live.publish(toPadState(parsed.input))
                reportsRead += 1
            }
            // bad pad data is counted and logged, never asserted
            is ParseResult.Rejected -> {
                rejectedCount += 1
                if (rejectedCount <= AppLimits.REJECTED_REPORTS_LOGGED_MAX) log("input report rejected: ${parsed.reason} ${parsed.detail}")
            }
        }
    }

    /**
     * Gives the pad back, in this order: the writer sends its all-off report last and exits, the
     * reader stops, both are joined, then the interface is released and the claim closed.
     * Idempotent; safe from any thread but the route's own two.
     */
    fun release() {
        if (!released.compareAndSet(false, true)) return
        val deadlineNs = System.nanoTime() + AppLimits.RELEASE_JOIN_TIMEOUT_MS * NANOS_PER_MS
        writer.finish()
        join(writerThread, deadlineNs)
        readerStop.set(true)
        join(readerThread, deadlineNs)
        releaseLinkOnce()
        live.setWriter(null)
        live.reset()
    }

    private fun join(thread: Thread?, deadlineNs: Long) {
        if (thread == null) return
        val leftMs = (deadlineNs - System.nanoTime()) / NANOS_PER_MS
        boundary("join ${thread.name}", log) { thread.join(leftMs.coerceAtLeast(1L)) }
        if (thread.isAlive) log("${thread.name} still running after the release wait")
    }

    private fun releaseLinkOnce() {
        if (!linkReleased.compareAndSet(false, true)) return
        boundary("releaseInterface", log) { link.releaseInterfaces() }
        boundary("close", log) { link.close() }
        if (current === this) current = null
    }

    /**
     * The crash path: never joins (the crashing thread may be the writer). Stops both threads from
     * sending, sends all-off itself, releases and closes. Never throws.
     */
    fun emergencyRelease() {
        if (linkReleased.get()) return
        readerStop.set(true)
        writer.halt()
        boundary("emergency all-off", log) { link.writeReport(writer.allOffReport()) }
        releaseLinkOnce()
    }

    /** Debug button: throws on the writer thread. */
    fun crashWriterNow() = writer.crashNow()

    /** Every control becomes one change of the writer's state; the writer builds the bytes. */
    override fun send(req: OutputRequest): ApiAnswer {
        val taken = when (req) {
            is OutputRequest.Rumble -> writer.setRumble(req.left, req.right, req.durationMs)
            is OutputRequest.RumblePattern -> writer.playRumble(req.steps)
            is OutputRequest.SetRumbleMode -> {
                writer.setRumbleMode(req.mode)
                !released.get()
            }
            is OutputRequest.Lightbar -> writer.change(OutputField.LIGHTBAR) { it.copy(lightbar = req.rgb) }
            is OutputRequest.PlayerLeds -> {
                require(req.mask in 0..Limits.PLAYER_LED_MASK_MAX) { "player LED mask ${req.mask}" }
                writer.change(OutputField.PLAYER_LEDS) { it.copy(playerLedMask = req.mask) }
            }
            is OutputRequest.SetMuteLed -> writer.change(OutputField.MUTE_LED) { it.copy(muteLed = req.mode) }
            is OutputRequest.Trigger -> return ApiAnswer.Failed("adaptive triggers are not built yet")
            is OutputRequest.AudioTone -> return ApiAnswer.Failed("audio is not built yet")
        }
        return if (taken) ApiAnswer.Ok else ApiAnswer.Failed("the pad is being handed back")
    }

    override val rumbleMode: RumbleMode get() = writer.rumbleMode

    override fun rumbleSent(): MotorLevels = writer.rumbleSent

    override fun stop() = release()
}
