package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.pad.Transport
import dev.dsprobe.app.route.RouteEffect.CancelTimer
import dev.dsprobe.app.route.RouteEffect.OpenDirectUsb
import dev.dsprobe.app.route.RouteEffect.ReleaseDirectUsb
import dev.dsprobe.app.route.RouteEffect.RequestUsbPermission
import dev.dsprobe.app.route.RouteEffect.ResyncInputs
import dev.dsprobe.app.route.RouteEffect.StartAndroidRoute
import dev.dsprobe.app.route.RouteEffect.StartTimer
import dev.dsprobe.app.route.RouteEffect.StopAndroidRoute
import dev.dsprobe.app.route.RouteEvent.AppStarted
import dev.dsprobe.app.route.RouteEvent.AppStopped
import dev.dsprobe.app.route.RouteEvent.BackRequested
import dev.dsprobe.app.route.RouteEvent.ClaimResult
import dev.dsprobe.app.route.RouteEvent.InputDevicesAppeared
import dev.dsprobe.app.route.RouteEvent.InputDevicesGone
import dev.dsprobe.app.route.RouteEvent.PermissionResult
import dev.dsprobe.app.route.RouteEvent.PermissionWaitTimeout
import dev.dsprobe.app.route.RouteEvent.ReaderError
import dev.dsprobe.app.route.RouteEvent.RebindTimeout
import dev.dsprobe.app.route.RouteEvent.ReleaseFinished
import dev.dsprobe.app.route.RouteEvent.SecondPadSeen
import dev.dsprobe.app.route.RouteEvent.StrandedTimeout
import dev.dsprobe.app.route.RouteEvent.SwitchRequested
import dev.dsprobe.app.route.RouteEvent.UsbAttached
import dev.dsprobe.app.route.RouteEvent.UsbDetached
import dev.dsprobe.app.route.RouteState.AskingUsbPermission
import dev.dsprobe.app.route.RouteState.DirectUsb
import dev.dsprobe.app.route.RouteState.NoPad
import dev.dsprobe.app.route.RouteState.Releasing
import dev.dsprobe.app.route.RouteState.Stranded
import dev.dsprobe.app.route.RouteState.TooMany
import dev.dsprobe.app.route.RouteState.WaitingForAndroid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private val STRANDED_TIMER = StartTimer(RouteTimer.STRANDED_GRACE, AppLimits.STRANDED_GRACE_MS)

private fun step(state: RouteState, event: RouteEvent, fg: Boolean = true) = routeStep(state, event, fg)

/** One test per arrow of the state table, plus the odd orders. */
class RouteMachineTest {
    // NoPad
    @Test fun noPadSeesInputDevicesAndEntersAndroidMode() {
        val r = step(NoPad(usbHit), InputDevicesAppeared(usbPad))
        assertEquals(androidMode(usbPad), r.state)
        assertEquals(listOf(CancelTimer(RouteTimer.STRANDED_GRACE), StartAndroidRoute(usbPad)), r.effects)
        assertNull(r.ignoredReason)
    }

    @Test fun androidRouteStaysOffWhileTheAppIsAway() {
        val r = step(NoPad(), InputDevicesAppeared(btPad), fg = false)
        assertEquals(androidMode(btPad), r.state)
        assertEquals(listOf(CancelTimer(RouteTimer.STRANDED_GRACE)), r.effects)
    }

    @Test fun usbListedWithoutInputDevicesStartsTheGraceTimerThenStrands() {
        val waiting = step(NoPad(), UsbAttached(usbHit))
        assertEquals(NoPad(usbHit), waiting.state)
        assertEquals(listOf(STRANDED_TIMER), waiting.effects)
        assertEquals(Stranded(usbHit), step(waiting.state, StrandedTimeout).state)
    }

    @Test fun noPadStrandedTimeoutWithoutUsbIsIgnored() {
        assertNotNull(step(NoPad(), StrandedTimeout).ignoredReason)
    }

    @Test fun noPadUnplugCancelsTheGraceTimer() {
        val r = step(NoPad(usbHit), UsbDetached)
        assertEquals(NoPad(), r.state)
        assertEquals(listOf(CancelTimer(RouteTimer.STRANDED_GRACE)), r.effects)
    }

    @Test fun noPadSecondPadMeansTooMany() {
        assertEquals(TooMany, step(NoPad(), SecondPadSeen).state)
    }

    // TooMany
    @Test fun tooManyLeavesWhenOneIsUnplugged() {
        assertEquals(androidMode(btPad), step(TooMany, InputDevicesAppeared(btPad)).state)
        assertEquals(NoPad(), step(TooMany, InputDevicesGone).state)
        assertEquals(NoPad(usbHit), step(TooMany, UsbAttached(usbHit)).state)
    }

    // AndroidMode
    @Test fun switchOnUsbAsksForPermissionAndStopsAndroidRoute() {
        val r = step(androidMode(), SwitchRequested)
        assertEquals(AskingUsbPermission(usbPad), r.state)
        assertEquals(listOf(StopAndroidRoute, RequestUsbPermission(usbPad)), r.effects)
    }

    @Test fun switchOverBluetoothIsRefused() {
        val r = step(androidMode(btPad), SwitchRequested)
        assertEquals(androidMode(btPad), r.state)
        assertNotNull(r.ignoredReason)
    }

    @Test fun androidModeUnplugGoesToNoPad() {
        val r = step(androidMode(), UsbDetached)
        assertEquals(NoPad(), r.state)
        assertEquals(listOf(StopAndroidRoute), r.effects)
    }

    @Test fun usbPadLosingItsInputDevicesWhileListedWaitsForTheGraceThenStrands() {
        val r = step(androidMode(), InputDevicesGone)
        assertEquals(NoPad(usbHit), r.state)
        assertEquals(listOf(StopAndroidRoute, STRANDED_TIMER), r.effects)
    }

    @Test fun bluetoothPadDisconnectGoesToNoPad() {
        assertEquals(NoPad(), step(androidMode(btPad), InputDevicesGone).state)
    }

    @Test fun androidModeSecondPadMeansTooMany() {
        val r = step(androidMode(), SecondPadSeen)
        assertEquals(TooMany, r.state)
        assertEquals(listOf(StopAndroidRoute), r.effects)
    }

    @Test fun newDeviceIdsRestartTheAndroidRoute() {
        val moved = usbPad.copy(inputDeviceIds = listOf(9))
        val r = step(androidMode(), InputDevicesAppeared(moved))
        assertEquals(androidMode(moved), r.state)
        assertEquals(listOf(StopAndroidRoute, StartAndroidRoute(moved)), r.effects)
    }

    @Test fun sameDeviceIdsChangeNothing() {
        val r = step(androidMode(), InputDevicesAppeared(usbPad))
        assertEquals(androidMode(), r.state)
        assertEquals(emptyList<RouteEffect>(), r.effects)
    }

    @Test fun cableNextToAPairedPadMakesItUsbAndAlsoPaired() {
        val r = step(androidMode(btPad), UsbAttached(usbHit))
        val mode = r.state as RouteState.AndroidMode
        assertEquals(Transport.USB, mode.pad.transport)
        assertEquals(true, mode.pad.alsoPairedBluetooth)
    }

    @Test fun appStopAndStartTearDownAndRebuildTheAndroidRoute() {
        assertEquals(listOf(StopAndroidRoute), step(androidMode(), AppStopped, fg = false).effects)
        assertEquals(listOf(StartAndroidRoute(usbPad)), step(androidMode(), AppStarted).effects)
    }

    // AskingUsbPermission
    @Test fun grantedWhileInFrontOpensTheClaim() {
        val r = step(AskingUsbPermission(usbPad), PermissionResult(true))
        assertEquals(AskingUsbPermission(usbPad), r.state)
        assertEquals(listOf(OpenDirectUsb(usbPad)), r.effects)
    }

    @Test fun claimOpenedEntersDirectUsb() {
        assertEquals(DirectUsb(usbPad), step(AskingUsbPermission(usbPad), ClaimResult(OpenResult.Opened)).state)
    }

    @Test fun claimOpenedAfterTheAppLeftIsGivenStraightBack() {
        val r = step(AskingUsbPermission(usbPad), ClaimResult(OpenResult.Opened), fg = false)
        assertEquals(Releasing(usbPad, ReleaseReason.APP_STOPPED), r.state)
        assertEquals(listOf(ReleaseDirectUsb), r.effects)
    }

    @Test fun everyFailedClaimReturnsToAndroidModeWithItsReason() {
        val cases = listOf(
            OpenResult.NoPermission to Notice(NoticeKind.NO_PERMISSION),
            OpenResult.WrongLayout("x") to Notice(NoticeKind.WRONG_LAYOUT, "x"),
            OpenResult.ClaimFailed(13) to Notice(NoticeKind.CLAIM_FAILED, "13"),
        )
        for ((result, notice) in cases) {
            val r = step(AskingUsbPermission(usbPad), ClaimResult(result))
            assertEquals(androidMode(usbPad, notice), r.state)
            assertEquals(listOf(StartAndroidRoute(usbPad)), r.effects)
        }
    }

    @Test fun permissionRefusedReturnsToAndroidMode() {
        val r = step(AskingUsbPermission(usbPad), PermissionResult(false))
        assertEquals(androidMode(usbPad, Notice(NoticeKind.PERMISSION_DENIED)), r.state)
    }

    @Test fun grantedInTheBackgroundWaitsForTheAppThenOpens() {
        val waiting = step(AskingUsbPermission(usbPad), PermissionResult(true), fg = false)
        assertEquals(AskingUsbPermission(usbPad, grantWaitingForApp = true), waiting.state)
        assertEquals(listOf(StartTimer(RouteTimer.PERMISSION_WAIT, AppLimits.PERMISSION_BACKGROUND_WAIT_MS)), waiting.effects)
        val back = step(waiting.state, AppStarted)
        assertEquals(listOf(CancelTimer(RouteTimer.PERMISSION_WAIT), OpenDirectUsb(usbPad)), back.effects)
    }

    @Test fun grantedInTheBackgroundTimesOutToAndroidMode() {
        val waiting = AskingUsbPermission(usbPad, grantWaitingForApp = true)
        val r = step(waiting, PermissionWaitTimeout, fg = false)
        assertEquals(androidMode(usbPad, Notice(NoticeKind.PERMISSION_TIMED_OUT)), r.state)
    }

    @Test fun askingBackUnplugAndSecondPad() {
        assertEquals(androidMode(usbPad), step(AskingUsbPermission(usbPad), BackRequested).state)
        assertEquals(NoPad(), step(AskingUsbPermission(usbPad), UsbDetached).state)
        assertEquals(TooMany, step(AskingUsbPermission(usbPad), SecondPadSeen).state)
    }

    // DirectUsb and Releasing
    @Test fun everyExitFromDirectUsbReleases() {
        val cases = listOf(
            BackRequested to Releasing(usbPad, ReleaseReason.BACK),
            AppStopped to Releasing(usbPad, ReleaseReason.APP_STOPPED),
            UsbDetached to Releasing(usbPad, ReleaseReason.USB_DETACHED, usbGone = true),
            ReaderError("boom") to Releasing(usbPad, ReleaseReason.READER_ERROR),
        )
        for ((event, expected) in cases) {
            val r = step(DirectUsb(usbPad), event, fg = event != AppStopped)
            assertEquals(expected, r.state)
            assertEquals(listOf(ReleaseDirectUsb), r.effects)
        }
    }

    @Test fun directUsbSecondPadIsIgnoredWithABanner() {
        assertEquals(DirectUsb(usbPad, secondPadIgnored = true), step(DirectUsb(usbPad), SecondPadSeen).state)
    }

    @Test fun androidDevicesComingAndGoingWhileClaimedIsExpected() {
        val r = step(DirectUsb(usbPad), InputDevicesGone)
        assertEquals(DirectUsb(usbPad), r.state)
        assertEquals(emptyList<RouteEffect>(), r.effects)
    }

    @Test fun releaseFinishedWaitsForAndroidAndAsksForAResync() {
        val r = step(Releasing(usbPad, ReleaseReason.BACK), ReleaseFinished)
        assertEquals(WaitingForAndroid(usbPad), r.state)
        assertEquals(listOf(StartTimer(RouteTimer.REBIND, AppLimits.ANDROID_REBIND_TIMEOUT_MS), ResyncInputs), r.effects)
    }

    @Test fun releaseAfterAnUnplugEndsAtNoPad() {
        assertEquals(NoPad(), step(Releasing(usbPad, ReleaseReason.USB_DETACHED, usbGone = true), ReleaseFinished).state)
        val unplugged = step(Releasing(usbPad, ReleaseReason.BACK), UsbDetached).state
        assertEquals(NoPad(), step(unplugged, ReleaseFinished).state)
    }

    // WaitingForAndroid
    @Test fun androidDevicesBackMeansAndroidMode() {
        val fresh = usbPad.copy(inputDeviceIds = listOf(12))
        val r = step(WaitingForAndroid(usbPad), InputDevicesAppeared(fresh))
        assertEquals(androidMode(fresh), r.state)
        assertEquals(listOf(CancelTimer(RouteTimer.REBIND), StartAndroidRoute(fresh)), r.effects)
    }

    @Test fun rebindTimeoutMeansStranded() {
        assertEquals(Stranded(usbHit), step(WaitingForAndroid(usbPad), RebindTimeout).state)
    }

    @Test fun waitingUnplugAndSecondPad() {
        assertEquals(NoPad(), step(WaitingForAndroid(usbPad), UsbDetached).state)
        assertEquals(TooMany, step(WaitingForAndroid(usbPad), SecondPadSeen).state)
    }

    // Stranded
    @Test fun strandedLeavesWhenDevicesAppearOrOnUnplug() {
        assertEquals(androidMode(usbPad), step(Stranded(usbHit), InputDevicesAppeared(usbPad)).state)
        assertEquals(NoPad(), step(Stranded(usbHit), UsbDetached).state)
        assertEquals(TooMany, step(Stranded(usbHit), SecondPadSeen).state)
    }

    // odd orders: logged and ignored, never thrown
    @Test fun oddPairsChangeNothingAndSayWhy() {
        val odd = listOf(
            androidMode() to ReleaseFinished,
            NoPad() to ClaimResult(OpenResult.Opened),
            Stranded(usbHit) to PermissionResult(true),
            DirectUsb(usbPad) to SwitchRequested,
            Releasing(usbPad, ReleaseReason.BACK) to BackRequested,
            WaitingForAndroid(usbPad) to StrandedTimeout,
        )
        for ((state, event) in odd) {
            val r = step(state, event)
            assertEquals(state, r.state)
            assertEquals(emptyList<RouteEffect>(), r.effects)
            assertNotNull("no reason for $event in $state", r.ignoredReason)
        }
    }
}
