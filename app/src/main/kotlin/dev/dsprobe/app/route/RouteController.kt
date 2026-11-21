package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.pad.FindResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What changed between two looks at the pad, as events. USB events first, then input device events,
 * then the second-pad event, so a plug-in reads NoPad(usb) then AndroidMode and an unplug reads the
 * other way.
 */
private const val EVENTS_PER_LOOK_MAX = 3 // one each at most: usb, input devices, second pad

fun eventsFor(prev: FindResult, next: FindResult): List<RouteEvent> {
    val out = ArrayList<RouteEvent>(EVENTS_PER_LOOK_MAX)
    if (prev.usb == null && next.usb != null) out.add(RouteEvent.UsbAttached(next.usb))
    if (prev.usb != null && next.usb == null) out.add(RouteEvent.UsbDetached)
    val nextPad = next.inputPad
    if (nextPad != null && nextPad != prev.inputPad) {
        out.add(RouteEvent.InputDevicesAppeared(nextPad))
    }
    val prevHadInputs = prev.inputPad != null || prev.tooMany
    val nextHasInputs = nextPad != null || next.tooMany
    if (prevHadInputs && !nextHasInputs) out.add(RouteEvent.InputDevicesGone)
    if (next.tooMany && !prev.tooMany) out.add(RouteEvent.SecondPadSeen)
    check(out.size <= EVENTS_PER_LOOK_MAX) { "events $out" }
    return out
}

/**
 * The only owner of the route: nothing else starts or stops one. Main thread only. Events queue,
 * so an effect that posts an event (a resync) is handled after the current one, never inside it.
 */
class RouteController(private val host: RouteHost, private val log: (String) -> Unit) {
    private val mutableState = MutableStateFlow<RouteState>(RouteState.NoPad())
    val state: StateFlow<RouteState> = mutableState.asStateFlow()

    private val queue = ArrayDeque<RouteEvent>()
    private var draining = false
    private var lastFind = FindResult.NONE
    var foreground = false
        private set

    fun post(event: RouteEvent) {
        queue.addLast(event)
        if (draining) return
        draining = true
        try {
            drain()
        } finally {
            draining = false
        }
    }

    private fun drain() {
        var handled = 0
        while (queue.isNotEmpty()) {
            check(handled < AppLimits.ROUTE_EVENTS_PER_DRAIN_MAX) {
                "route event storm: $handled events in one drain"
            }
            handled += 1
            step(queue.removeFirst())
        }
    }

    private fun step(event: RouteEvent) {
        if (event == RouteEvent.AppStarted) foreground = true
        if (event == RouteEvent.AppStopped) foreground = false
        val result = routeStep(mutableState.value, event, foreground)
        mutableState.value = result.state
        result.ignoredReason?.let { log(it) }
        for (effect in result.effects) run(effect)
    }

    private fun run(effect: RouteEffect) {
        when (effect) {
            is RouteEffect.StartAndroidRoute -> host.startAndroidRoute(effect.pad)
            RouteEffect.StopAndroidRoute -> host.stopAndroidRoute()
            is RouteEffect.RequestUsbPermission -> host.requestUsbPermission(effect.pad)
            is RouteEffect.OpenDirectUsb -> host.openDirectUsb(effect.pad)
            RouteEffect.ReleaseDirectUsb -> host.releaseDirectUsb()
            is RouteEffect.StartTimer -> host.startTimer(effect.timer, effect.ms)
            is RouteEffect.CancelTimer -> host.cancelTimer(effect.timer)
            RouteEffect.ResyncInputs -> host.resyncInputs()
        }
    }

    /** A fresh look at the pad: the difference from the last look becomes events. */
    fun onFound(next: FindResult) {
        val events = eventsFor(lastFind, next)
        lastFind = next
        for (event in events) post(event)
    }

    fun onStart() = post(RouteEvent.AppStarted)

    fun onStop() = post(RouteEvent.AppStopped)

    fun onUsbDetached() = post(RouteEvent.UsbDetached)

    fun backToAndroidMode() = post(RouteEvent.BackRequested)

    fun switchToDirectUsb() = post(RouteEvent.SwitchRequested)
}
