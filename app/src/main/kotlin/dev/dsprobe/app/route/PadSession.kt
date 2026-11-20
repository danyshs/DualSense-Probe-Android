package dev.dsprobe.app.route

import android.content.Context
import android.content.pm.ApplicationInfo
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.output.LiveRoute
import dev.dsprobe.app.output.OutputController
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
    val outputs = OutputController(::liveRoute, SystemClock::uptimeMillis, ::logWarn)

    /** The debug "Crash now" button exists only in a debuggable build. */
    val crashNowAvailable: Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val finder = PadFinder(context)
    private val usbManager: UsbManager = checkNotNull(context.getSystemService(UsbManager::class.java)) { "no UsbManager" }
    private val permission = UsbPermission(context, ::logWarn)
    private val handler = Handler(Looper.getMainLooper())
    private val timers = HashMap<RouteTimer, Runnable>()
    private var direct: DirectUsbRoute? = null

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
