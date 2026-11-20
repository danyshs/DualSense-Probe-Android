package dev.dsprobe.app.route

import dev.dsprobe.app.pad.FindResult
import dev.dsprobe.app.pad.FoundPad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHost : RouteHost {
    val calls = ArrayList<String>()
    var onResync: () -> Unit = {}
    override fun startAndroidRoute(pad: FoundPad) { calls.add("start ${pad.inputDeviceIds}") }
    override fun stopAndroidRoute() { calls.add("stop") }
    override fun requestUsbPermission(pad: FoundPad) { calls.add("permission") }
    override fun openDirectUsb(pad: FoundPad) { calls.add("open") }
    override fun releaseDirectUsb() { calls.add("release") }
    override fun startTimer(timer: RouteTimer, ms: Long) { calls.add("timer $timer") }
    override fun cancelTimer(timer: RouteTimer) { calls.add("cancel $timer") }
    override fun resyncInputs() { calls.add("resync"); onResync() }
}

class RouteControllerTest {
    private val host = FakeHost()
    private val logged = ArrayList<String>()

    private fun controller(directUsb: Boolean = true) = RouteController(host, directUsb) { logged.add(it) }

    @Test fun aPlugInReadsAsUsbThenInputDevicesAndEndsInAndroidMode() {
        val c = controller()
        c.onStart()
        c.onFound(FindResult(usbHit, null, false))
        assertEquals(RouteState.NoPad(usbHit), c.state.value)
        c.onFound(FindResult(usbHit, usbPad, false))
        assertEquals(androidMode(usbPad), c.state.value)
        assertEquals(listOf("timer STRANDED_GRACE", "cancel STRANDED_GRACE", "start [7]"), host.calls)
    }

    @Test fun anUnplugReadsAsGone() {
        val c = controller()
        c.onStart()
        c.onFound(FindResult(usbHit, usbPad, false))
        c.onFound(FindResult.NONE)
        assertEquals(RouteState.NoPad(), c.state.value)
    }

    @Test fun aSecondPadShowsTooManyAndOneLeftReturns() {
        val c = controller()
        c.onStart()
        c.onFound(FindResult(null, btPad, false))
        c.onFound(FindResult(null, null, true))
        assertEquals(RouteState.TooMany, c.state.value)
        c.onFound(FindResult(null, btPad, false))
        assertEquals(androidMode(btPad), c.state.value)
        c.onFound(FindResult(null, null, true))
        c.onFound(FindResult.NONE)
        assertEquals(RouteState.NoPad(), c.state.value)
    }

    @Test fun switchIsRefusedAndLoggedUntilDirectUsbIsBuilt() {
        val c = controller(directUsb = false)
        c.onStart()
        c.onFound(FindResult(usbHit, usbPad, false))
        c.switchToDirectUsb()
        assertEquals(androidMode(usbPad), c.state.value)
        assertEquals(1, logged.size)
    }

    @Test fun fullTripThroughDirectUsbAndBack() {
        val c = controller()
        c.onStart()
        c.onFound(FindResult(usbHit, usbPad, false))
        host.calls.clear()
        c.switchToDirectUsb()
        c.post(RouteEvent.PermissionResult(true))
        c.post(RouteEvent.ClaimResult(OpenResult.Opened))
        assertEquals(RouteState.DirectUsb(usbPad), c.state.value)
        c.backToAndroidMode()
        host.onResync = { c.post(RouteEvent.InputDevicesAppeared(usbPad.copy(inputDeviceIds = listOf(20)))) }
        c.post(RouteEvent.ReleaseFinished)
        assertEquals(androidMode(usbPad.copy(inputDeviceIds = listOf(20))), c.state.value)
        assertEquals(
            listOf("stop", "permission", "open", "release", "timer REBIND", "resync", "cancel REBIND", "start [20]"),
            host.calls,
        )
    }

    @Test fun backgroundingWhileClaimedReleases() {
        val c = controller()
        c.onStart()
        c.onFound(FindResult(usbHit, usbPad, false))
        c.switchToDirectUsb()
        c.post(RouteEvent.PermissionResult(true))
        c.post(RouteEvent.ClaimResult(OpenResult.Opened))
        c.onStop()
        assertTrue(c.state.value is RouteState.Releasing)
        assertEquals(false, c.foreground)
    }

    @Test fun anEventStormIsStoppedLoudly() {
        val c = controller()
        host.onResync = { repeat(100) { c.post(RouteEvent.AppStarted) } }
        c.onFound(FindResult(usbHit, usbPad, false))
        c.switchToDirectUsb()
        c.post(RouteEvent.PermissionResult(true))
        c.post(RouteEvent.ClaimResult(OpenResult.Opened))
        c.backToAndroidMode()
        val failed = runCatching { c.post(RouteEvent.ReleaseFinished) }.exceptionOrNull()
        assertTrue(failed is IllegalStateException)
    }
}

class EventsForTest {
    @Test fun eachChangeMakesItsEventsInOrder() {
        val both = FindResult(usbHit, usbPad, false)
        assertEquals(
            listOf(RouteEvent.UsbAttached(usbHit), RouteEvent.InputDevicesAppeared(usbPad)),
            eventsFor(FindResult.NONE, both),
        )
        assertEquals(listOf(RouteEvent.UsbDetached, RouteEvent.InputDevicesGone), eventsFor(both, FindResult.NONE))
        assertEquals(emptyList<RouteEvent>(), eventsFor(both, both))
    }

    @Test fun aSecondPadReplacesTheFirst() {
        val one = FindResult(usbHit, usbPad, false)
        val many = FindResult(null, null, true)
        assertEquals(listOf(RouteEvent.UsbDetached, RouteEvent.SecondPadSeen), eventsFor(one, many))
        assertEquals(emptyList<RouteEvent>(), eventsFor(many, many))
    }

    @Test fun movedDeviceIdsReadAsAppeared() {
        val moved = usbPad.copy(inputDeviceIds = listOf(9))
        assertEquals(
            listOf(RouteEvent.InputDevicesAppeared(moved)),
            eventsFor(FindResult(usbHit, usbPad, false), FindResult(usbHit, moved, false)),
        )
    }
}
