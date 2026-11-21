package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.pad.SeenUsb
import dev.dsprobe.app.pad.Transport
import dev.dsprobe.app.pad.VENDOR_SONY
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
import dev.dsprobe.app.route.RouteState.AndroidMode
import dev.dsprobe.app.route.RouteState.AskingUsbPermission
import dev.dsprobe.app.route.RouteState.DirectUsb
import dev.dsprobe.app.route.RouteState.NoPad
import dev.dsprobe.app.route.RouteState.Releasing
import dev.dsprobe.app.route.RouteState.Stranded
import dev.dsprobe.app.route.RouteState.TooMany
import dev.dsprobe.app.route.RouteState.WaitingForAndroid

/**
 * The pure transition function: (state, event) gives the next state and what to do about it.
 * [foreground] already reflects [event] (AppStarted means true, AppStopped false). A pair with no
 * transition comes back unchanged with a reason: such pairs are Android broadcasts arriving in an
 * odd order, which is outside input, not our bug.
 */
fun routeStep(state: RouteState, event: RouteEvent, foreground: Boolean): RouteStep = when (state) {
    is NoPad -> onNoPad(state, event, foreground)
    is TooMany -> onTooMany(state, event, foreground)
    is AndroidMode -> onAndroidMode(state, event, foreground)
    is AskingUsbPermission -> onAsking(state, event, foreground)
    is DirectUsb -> onDirectUsb(state, event)
    is Releasing -> onReleasing(state, event)
    is Stranded -> onStranded(state, event, foreground)
    is WaitingForAndroid -> onWaiting(state, event, foreground)
}

private fun ignore(state: RouteState, event: RouteEvent): RouteStep =
    RouteStep(state, emptyList(), "ignored $event in $state")

private fun go(state: RouteState, vararg effects: RouteEffect): RouteStep =
    RouteStep(state, effects.toList())

/** Enters Android mode; its listeners only start while the app is in front. */
private fun androidMode(
    pad: FoundPad,
    notice: Notice?,
    foreground: Boolean,
    vararg before: RouteEffect,
): RouteStep {
    val mode = AndroidMode(pad, notice)
    val start = if (foreground) listOf(StartAndroidRoute(pad)) else emptyList()
    return RouteStep(mode, before.toList() + start)
}

private fun stranded(pad: FoundPad): SeenUsb {
    check(pad.transport == Transport.USB) { "only a USB pad can be stranded" }
    return SeenUsb(pad.usbDevice, VENDOR_SONY, pad.productId)
}

private fun onNoPad(s: NoPad, e: RouteEvent, fg: Boolean): RouteStep = when (e) {
    is UsbAttached -> go(
        NoPad(e.usb),
        StartTimer(RouteTimer.STRANDED_GRACE, AppLimits.STRANDED_GRACE_MS),
    )

    UsbDetached -> go(NoPad(), CancelTimer(RouteTimer.STRANDED_GRACE))

    is InputDevicesAppeared -> androidMode(e.pad, null, fg, CancelTimer(RouteTimer.STRANDED_GRACE))

    StrandedTimeout -> if (s.usb != null) go(Stranded(s.usb)) else ignore(s, e)

    SecondPadSeen -> go(TooMany, CancelTimer(RouteTimer.STRANDED_GRACE))

    AppStarted, AppStopped -> go(s)

    else -> ignore(s, e)
}

private fun onTooMany(s: TooMany, e: RouteEvent, fg: Boolean): RouteStep = when (e) {
    is InputDevicesAppeared -> androidMode(e.pad, null, fg)

    // one pad is left and it has no input devices yet: the grace period decides if it is stranded
    is UsbAttached -> go(
        NoPad(e.usb),
        StartTimer(RouteTimer.STRANDED_GRACE, AppLimits.STRANDED_GRACE_MS),
    )

    InputDevicesGone -> go(NoPad())

    AppStarted, AppStopped -> go(s)

    else -> ignore(s, e)
}

private fun onAndroidMode(s: AndroidMode, e: RouteEvent, fg: Boolean): RouteStep = when (e) {
    SwitchRequested ->
        if (s.pad.transport == Transport.USB) {
            go(AskingUsbPermission(s.pad), StopAndroidRoute, RequestUsbPermission(s.pad))
        } else {
            ignore(s, e)
        }

    is UsbAttached -> onAndroidUsbAttached(s, e)

    UsbDetached -> if (s.pad.transport == Transport.USB) {
        go(NoPad(), StopAndroidRoute)
    } else {
        ignore(s, e)
    }

    is InputDevicesAppeared ->
        if (e.pad == s.pad) go(s) else androidMode(e.pad, null, fg, StopAndroidRoute)

    InputDevicesGone -> onAndroidInputsGone(s)

    SecondPadSeen -> go(TooMany, StopAndroidRoute)

    AppStopped -> go(s, StopAndroidRoute)

    AppStarted -> go(s, StartAndroidRoute(s.pad))

    else -> ignore(s, e)
}

/** A cable plugged in next to a paired pad: the same pad, now on USB too. */
private fun onAndroidUsbAttached(s: AndroidMode, e: UsbAttached): RouteStep {
    if (s.pad.transport == Transport.USB) return ignore(s, e)
    val pad = s.pad.copy(
        usbDevice = e.usb.device,
        transport = Transport.USB,
        alsoPairedBluetooth = true,
    )
    return go(AndroidMode(pad, s.notice))
}

/** USB pad whose input devices vanished while it is still listed: another app may hold it. */
private fun onAndroidInputsGone(s: AndroidMode): RouteStep = if (s.pad.transport == Transport.USB) {
    go(
        NoPad(stranded(s.pad)),
        StopAndroidRoute,
        StartTimer(RouteTimer.STRANDED_GRACE, AppLimits.STRANDED_GRACE_MS),
    )
} else {
    go(NoPad(), StopAndroidRoute)
}

private fun onAsking(s: AskingUsbPermission, e: RouteEvent, fg: Boolean): RouteStep = when (e) {
    is PermissionResult -> onPermissionResult(s, e, fg)

    AppStarted ->
        if (s.grantWaitingForApp) {
            go(
                s.copy(grantWaitingForApp = false),
                CancelTimer(RouteTimer.PERMISSION_WAIT),
                OpenDirectUsb(s.pad),
            )
        } else {
            go(s)
        }

    PermissionWaitTimeout ->
        if (s.grantWaitingForApp) {
            androidMode(
                s.pad,
                Notice(NoticeKind.PERMISSION_TIMED_OUT),
                fg,
            )
        } else {
            ignore(s, e)
        }

    is ClaimResult -> onClaimResult(s, e.result, fg)

    BackRequested -> androidMode(s.pad, null, fg, CancelTimer(RouteTimer.PERMISSION_WAIT))

    UsbDetached -> go(NoPad(), CancelTimer(RouteTimer.PERMISSION_WAIT))

    SecondPadSeen -> go(TooMany, CancelTimer(RouteTimer.PERMISSION_WAIT))

    AppStopped -> go(s)

    else -> ignore(s, e)
}

private fun onPermissionResult(
    s: AskingUsbPermission,
    e: PermissionResult,
    fg: Boolean,
): RouteStep = when {
    !e.granted -> androidMode(
        s.pad,
        Notice(NoticeKind.PERMISSION_DENIED),
        fg,
        CancelTimer(RouteTimer.PERMISSION_WAIT),
    )

    fg -> go(s, OpenDirectUsb(s.pad))

    // answered while the app is away: the claim waits until it is back in front
    else -> go(
        s.copy(grantWaitingForApp = true),
        StartTimer(RouteTimer.PERMISSION_WAIT, AppLimits.PERMISSION_BACKGROUND_WAIT_MS),
    )
}

private fun onClaimResult(s: AskingUsbPermission, r: OpenResult, fg: Boolean): RouteStep =
    when (r) {
        OpenResult.Opened ->
            // claimed while the app was leaving: give it straight back
            if (fg) {
                go(
                    DirectUsb(s.pad),
                )
            } else {
                go(Releasing(s.pad, ReleaseReason.APP_STOPPED), ReleaseDirectUsb)
            }

        OpenResult.NoPermission -> androidMode(s.pad, Notice(NoticeKind.NO_PERMISSION), fg)

        is OpenResult.WrongLayout -> androidMode(
            s.pad,
            Notice(NoticeKind.WRONG_LAYOUT, r.detail),
            fg,
        )

        is OpenResult.ClaimFailed -> androidMode(
            s.pad,
            Notice(NoticeKind.CLAIM_FAILED, r.errno.toString()),
            fg,
        )
    }

private fun release(pad: FoundPad, reason: ReleaseReason, usbGone: Boolean = false): RouteStep =
    go(Releasing(pad, reason, usbGone), ReleaseDirectUsb)

private fun onDirectUsb(s: DirectUsb, e: RouteEvent): RouteStep = when (e) {
    BackRequested -> release(s.pad, ReleaseReason.BACK)

    AppStopped -> release(s.pad, ReleaseReason.APP_STOPPED)

    UsbDetached -> release(s.pad, ReleaseReason.USB_DETACHED, usbGone = true)

    is ReaderError -> release(s.pad, ReleaseReason.READER_ERROR)

    // the claim itself makes Android drop and later restore the pad's input devices
    SecondPadSeen -> go(s.copy(secondPadIgnored = true))

    InputDevicesGone, is InputDevicesAppeared, is UsbAttached, AppStarted -> RouteStep(
        s,
        emptyList(),
        "expected while claimed: $e",
    )

    else -> ignore(s, e)
}

private fun onReleasing(s: Releasing, e: RouteEvent): RouteStep = when (e) {
    ReleaseFinished ->
        if (s.usbGone) {
            go(NoPad())
        } else {
            go(
                WaitingForAndroid(s.pad),
                StartTimer(RouteTimer.REBIND, AppLimits.ANDROID_REBIND_TIMEOUT_MS),
                ResyncInputs,
            )
        }

    UsbDetached -> go(s.copy(usbGone = true))

    else -> ignore(s, e)
}

private fun onStranded(s: Stranded, e: RouteEvent, fg: Boolean): RouteStep = when (e) {
    is InputDevicesAppeared -> androidMode(e.pad, null, fg)
    UsbDetached -> go(NoPad())
    SecondPadSeen -> go(TooMany)
    AppStarted, AppStopped -> go(s)
    else -> ignore(s, e)
}

private fun onWaiting(s: WaitingForAndroid, e: RouteEvent, fg: Boolean): RouteStep = when (e) {
    is InputDevicesAppeared -> androidMode(e.pad, null, fg, CancelTimer(RouteTimer.REBIND))
    RebindTimeout -> go(Stranded(stranded(s.pad)))
    UsbDetached -> go(NoPad(), CancelTimer(RouteTimer.REBIND))
    SecondPadSeen -> go(TooMany, CancelTimer(RouteTimer.REBIND))
    AppStarted, AppStopped -> go(s)
    else -> ignore(s, e)
}
