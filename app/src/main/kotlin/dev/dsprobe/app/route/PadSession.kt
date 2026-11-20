package dev.dsprobe.app.route

import android.content.Context
import android.content.pm.ApplicationInfo
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.CrashHandler
import dev.dsprobe.app.debug.DebugHub
import dev.dsprobe.app.debug.DebugRunner
import dev.dsprobe.app.debug.LogFooter
import dev.dsprobe.app.debug.LogInput
import dev.dsprobe.app.debug.RunnerEnv
import dev.dsprobe.app.debug.TestResult
import dev.dsprobe.app.debug.buildCatalogue
import dev.dsprobe.app.debug.padHeaderFields
import dev.dsprobe.app.debug.phoneHeaderFields
import dev.dsprobe.app.debug.renderLog
import dev.dsprobe.app.ui.padOf
import dev.dsprobe.pad.FirmwareInfo
import java.io.File
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.output.LiveRoute
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.PendingAsk
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.pad.PadFinder

private const val TAG = "PadSession"

private fun logWarn(message: String) {
    Log.w(TAG, message)
}

/**
 * The app's one wiring point: finder, live state, both routes, the controller, and the timers
 * the controller asks for. Main thread only.
 */
class PadSession(context: Context) : RouteHost {
    val live = LiveState()
    val feed = EventFeed()
    val androidRoute = AndroidRoute(context, live, feed)
    val controller = RouteController(this, ::logWarn)
    val outputs = OutputController(::liveRoute, SystemClock::uptimeMillis, ::logWarn, ::noteChangedAnswer)
    val debug = DebugHub(feed)

    // the developer's "did it change?" goes into the trace next to what Android said
    private fun noteChangedAnswer(ask: PendingAsk, changed: Boolean) {
        if (feed.isActive) feed.publish(changedAnswerEvent(System.nanoTime(), ask.kind, ask.sent, ask.answer, changed))
    }

    /** What the runner needs from the app: the live route, the route switches, sends, and Android mode's locks. */
    fun runnerEnv(): RunnerEnv = object : RunnerEnv {
        override fun currentRoute(): Route? = when (controller.state.value) {
            is RouteState.AndroidMode -> Route.ANDROID
            is RouteState.DirectUsb -> Route.DIRECT_USB
            else -> null
        }

        override fun requestRoute(route: Route) {
            if (route == Route.DIRECT_USB) controller.switchToDirectUsb() else controller.backToAndroidMode()
        }

        override fun send(req: OutputRequest): ApiAnswer = liveRoute()?.route?.send(req) ?: ApiAnswer.Failed("no pad is live")

        // no answers at all means nothing is offered
        override fun isLocked(feature: Feature): Boolean = androidRoute.answers.value?.let { a -> reductions(a).any { it.feature == feature } } ?: true

        override fun setMuteWatched(on: Boolean) {
            androidRoute.muteWatched = on
        }

        override fun muteKeyCode(): Int? = androidRoute.answers.value?.muteKeyCode
    }

    fun newRunner(): DebugRunner = DebugRunner(buildCatalogue(), runnerEnv(), SystemClock::uptimeMillis)

    /** The record the crash handler wrote last time the app crashed; null when it never did. */
    fun crashRecord(): String? {
        val file = File(appContext.filesDir, CrashHandler.RECORD_FILE)
        return if (file.isFile) file.readText().take(AppLimits.CRASH_RECORD_BYTES_MAX) else null
    }

    /** The whole log as text: header, Android's view, the results, the trace rows, the crash record, the footer. */
    fun exportLog(results: List<TestResult>): String {
        val pad = padOf(controller.state.value)
        // a header that cannot be read must not stop the log: say so in its place
        val phone = askOrNull("phone header", ::logWarn) { phoneHeaderFields(appContext) } ?: listOf("header" to "unavailable")
        val header = phone +
            padHeaderFields(pad?.productId, pad?.transport, pad?.alsoPairedBluetooth ?: false, lastFirmware)
        val stats = debug.stats
        val footer = LogFooter(stats.reportsReceived, stats.reportsDropped, stats.longestGapMs, debug.feedOverflow, debug.trace.axisRowsSkipped, androidRoute.lateEventsDropped)
        val crash = askOrNull("crash record", ::logWarn) { crashRecord().orEmpty() }?.ifEmpty { null }
        return renderLog(LogInput(header, androidRoute.answers.value ?: lastAnswers, results, debug.logRows(), crash, footer, debug.tour.answers))
    }

    /** The debug "Crash now" button exists only in a debuggable build. */
    val crashNowAvailable: Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val finder = PadFinder(context)
    private val usbManager: UsbManager = checkNotNull(context.getSystemService(UsbManager::class.java)) { "no UsbManager" }
    private val permission = UsbPermission(context, ::logWarn)
    private val handler = Handler(Looper.getMainLooper())
    private val timers = HashMap<RouteTimer, Runnable>()
    private var direct: DirectUsbRoute? = null
    private var lastFirmware: FirmwareInfo? = null
    private var lastAnswers: AndroidAnswers? = null
    private val appContext: Context = context.applicationContext

    init {
        finder.start { controller.onFound(finder.find()) }
        controller.onFound(finder.find())
    }

    fun onActivityStart() {
        controller.onStart()
        controller.onFound(finder.find())
    }

    fun onActivityStop() = controller.onStop()

    /** For the crash record: where the route machine was, without building a session on a crash. */
    fun describeRoute(): String = controller.state.value::class.simpleName ?: "unknown"

    /** Debug button: throws on the writer thread while claimed. */
    fun crashNow() {
        check(crashNowAvailable) { "crash button in a build that is not debuggable" }
        direct?.crashWriterNow()
    }

    // the route outputs go to: the claim while Direct USB is open, else Android mode while it is live
    private fun liveRoute(): LiveRoute? = direct?.let { LiveRoute(it, Route.DIRECT_USB) }
        ?: if (androidRoute.isLive) LiveRoute(androidRoute, Route.ANDROID) else null

    override fun startAndroidRoute(pad: FoundPad) {
        outputs.reset()
        androidRoute.start(pad)
    }

    override fun stopAndroidRoute() {
        // the answers outlive the route, so a log made after a switch still carries Android's view
        androidRoute.answers.value?.let { lastAnswers = it }
        androidRoute.stop()
        outputs.reset()
    }

    override fun requestUsbPermission(pad: FoundPad) {
        val device = pad.usbDevice
        if (device == null) {
            controller.post(RouteEvent.PermissionResult(false))
            return
        }
        if (usbManager.hasPermission(device)) {
            controller.post(RouteEvent.PermissionResult(true))
            return
        }
        val asked = permission.request(usbManager, device) { granted -> controller.post(RouteEvent.PermissionResult(granted)) }
        if (!asked) controller.post(RouteEvent.PermissionResult(false))
    }

    override fun openDirectUsb(pad: FoundPad) {
        check(direct == null) { "a Direct USB route is already open" }
        val device = pad.usbDevice
        val outcome = if (device == null) OpenOutcome(OpenResult.WrongLayout("no USB device"), null) else claimHidInterface(usbManager, device, ::logWarn)
        outcome.link?.let { link ->
            val route = DirectUsbRoute(link, live, feed, ::onRouteFault, ::logWarn)
            direct = route
            route.start()
            lastFirmware = route.firmware
            outputs.reset()
        }
        controller.post(RouteEvent.ClaimResult(outcome.result))
    }

    // called on the reader or writer thread: hop to the main thread, where the controller lives
    private fun onRouteFault(detail: String) {
        handler.post { controller.post(RouteEvent.ReaderError(detail)) }
    }

    override fun releaseDirectUsb() {
        val route = direct
        direct = null
        if (route != null) boundary("release Direct USB", ::logWarn) { route.release() }
        outputs.reset()
        controller.post(RouteEvent.ReleaseFinished)
    }

    override fun startTimer(timer: RouteTimer, ms: Long) {
        cancelTimer(timer)
        val task = Runnable {
            timers.remove(timer)
            controller.post(timeoutEvent(timer))
        }
        timers[timer] = task
        handler.postDelayed(task, ms)
    }

    override fun cancelTimer(timer: RouteTimer) {
        timers.remove(timer)?.let { handler.removeCallbacks(it) }
    }

    override fun resyncInputs() {
        finder.find().inputPad?.let { controller.post(RouteEvent.InputDevicesAppeared(it)) }
    }

    private fun timeoutEvent(timer: RouteTimer): RouteEvent = when (timer) {
        RouteTimer.STRANDED_GRACE -> RouteEvent.StrandedTimeout
        RouteTimer.PERMISSION_WAIT -> RouteEvent.PermissionWaitTimeout
        RouteTimer.REBIND -> RouteEvent.RebindTimeout
    }
}
