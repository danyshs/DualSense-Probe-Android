package dev.dsprobe.app.route

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.pad.PadFinder

private const val TAG = "PadSession"

/** Direct USB arrives with its own step; until then the controller refuses the switch and none of this runs. */
private const val DIRECT_USB_BUILT = false

/**
 * The app's one wiring point: finder, live state, the Android route, the controller, and the timers
 * the controller asks for. Main thread only.
 */
class PadSession(context: Context) : RouteHost {
    val live = LiveState()
    val feed = EventFeed()
    val androidRoute = AndroidRoute(context, live, feed)
    val controller = RouteController(this, DIRECT_USB_BUILT) { Log.w(TAG, it) }
    val directUsbAvailable: Boolean = DIRECT_USB_BUILT

    private val finder = PadFinder(context)
    private val handler = Handler(Looper.getMainLooper())
    private val timers = HashMap<RouteTimer, Runnable>()

    init {
        finder.start { controller.onFound(finder.find()) }
        controller.onFound(finder.find())
    }

    fun onActivityStart() {
        controller.onStart()
        controller.onFound(finder.find())
    }

    fun onActivityStop() = controller.onStop()

    override fun startAndroidRoute(pad: FoundPad) = androidRoute.start(pad)

    override fun stopAndroidRoute() = androidRoute.stop()

    override fun requestUsbPermission(pad: FoundPad) = error("Direct USB is not built: no permission request")

    override fun openDirectUsb(pad: FoundPad) = error("Direct USB is not built: no claim")

    override fun releaseDirectUsb() = error("Direct USB is not built: nothing to release")

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
