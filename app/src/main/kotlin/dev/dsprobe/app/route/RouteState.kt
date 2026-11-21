package dev.dsprobe.app.route

import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.pad.SeenUsb

enum class Route { ANDROID, DIRECT_USB }

/** What a failed or abandoned switch to Direct USB shows on the Android mode screen. */
enum class NoticeKind {
    PERMISSION_DENIED,
    PERMISSION_TIMED_OUT,
    NO_PERMISSION,
    WRONG_LAYOUT,
    CLAIM_FAILED,
}

data class Notice(val kind: NoticeKind, val detail: String = "")

/**
 * What claiming the pad's HID interface gave back. The claim itself is built in the Direct USB
 * step.
 */
sealed interface OpenResult {
    data object Opened : OpenResult
    data object NoPermission : OpenResult
    data class WrongLayout(val detail: String) : OpenResult
    data class ClaimFailed(val errno: Int) : OpenResult
}

enum class ReleaseReason { BACK, APP_STOPPED, USB_DETACHED, READER_ERROR }

/** The eight states of the route state machine. */
sealed interface RouteState {
    /**
     * [usb] is set while UsbManager lists a pad and the grace period for its input devices runs.
     */
    data class NoPad(val usb: SeenUsb? = null) : RouteState
    data object TooMany : RouteState
    data class AndroidMode(val pad: FoundPad, val notice: Notice? = null) : RouteState
    data class AskingUsbPermission(val pad: FoundPad, val grantWaitingForApp: Boolean = false) :
        RouteState
    data class DirectUsb(val pad: FoundPad, val secondPadIgnored: Boolean = false) : RouteState
    data class Releasing(
        val pad: FoundPad,
        val reason: ReleaseReason,
        val usbGone: Boolean = false,
    ) : RouteState
    data class Stranded(val usb: SeenUsb) : RouteState
    data class WaitingForAndroid(val pad: FoundPad) : RouteState
}

/** One event per arrow of the state table. */
sealed interface RouteEvent {
    data object SwitchRequested : RouteEvent
    data object BackRequested : RouteEvent
    data class PermissionResult(val granted: Boolean) : RouteEvent
    data class ClaimResult(val result: OpenResult) : RouteEvent
    data class UsbAttached(val usb: SeenUsb) : RouteEvent
    data object UsbDetached : RouteEvent
    data class InputDevicesAppeared(val pad: FoundPad) : RouteEvent
    data object InputDevicesGone : RouteEvent
    data object SecondPadSeen : RouteEvent
    data object ReleaseFinished : RouteEvent
    data class ReaderError(val detail: String) : RouteEvent
    data object AppStopped : RouteEvent
    data object AppStarted : RouteEvent
    data object PermissionWaitTimeout : RouteEvent
    data object StrandedTimeout : RouteEvent
    data object RebindTimeout : RouteEvent
}

enum class RouteTimer { STRANDED_GRACE, PERMISSION_WAIT, REBIND }

/** What the controller must do after a transition; the machine itself touches nothing. */
sealed interface RouteEffect {
    data class StartAndroidRoute(val pad: FoundPad) : RouteEffect
    data object StopAndroidRoute : RouteEffect
    data class RequestUsbPermission(val pad: FoundPad) : RouteEffect
    data class OpenDirectUsb(val pad: FoundPad) : RouteEffect
    data object ReleaseDirectUsb : RouteEffect
    data class StartTimer(val timer: RouteTimer, val ms: Long) : RouteEffect
    data class CancelTimer(val timer: RouteTimer) : RouteEffect

    /**
     * Re-read what Android lists now and report it, in case it came back while an event was being
     * ignored.
     */
    data object ResyncInputs : RouteEffect
}

/** [ignoredReason] is set when the event changed nothing; the controller logs it. */
data class RouteStep(
    val state: RouteState,
    val effects: List<RouteEffect>,
    val ignoredReason: String? = null,
)

/** Everything the controller calls out to. Implemented by the app's session; faked in tests. */
interface RouteHost {
    fun startAndroidRoute(pad: FoundPad)
    fun stopAndroidRoute()
    fun requestUsbPermission(pad: FoundPad)
    fun openDirectUsb(pad: FoundPad)
    fun releaseDirectUsb()
    fun startTimer(timer: RouteTimer, ms: Long)
    fun cancelTimer(timer: RouteTimer)
    fun resyncInputs()
}
