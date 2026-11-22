package dev.dsprobe.app.debug

import androidx.annotation.StringRes
import dev.dsprobe.app.R
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.pad.Transport
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState

/**
 * How the pad is reached right now. [tag] is the word the log carries. The Bluetooth address is
 * never read to tell these apart (FACTS P52, P78).
 */
enum class Connection(val tag: String, @param:StringRes val labelRes: Int) {
    DIRECT_USB("direct_usb", R.string.connection_direct_usb),
    ANDROID_USB("android_usb", R.string.connection_android_usb),
    ANDROID_BLUETOOTH("android_bluetooth", R.string.connection_android_bluetooth),
    NONE("none", R.string.connection_none),
    ;

    /** The three connections an expectation can exist for. */
    companion object {
        val MEASURABLE: List<Connection> = listOf(DIRECT_USB, ANDROID_USB, ANDROID_BLUETOOTH)
    }
}

/**
 * The rule (P6): Direct USB is our route on a pad UsbManager lists; on the Android route a pad in
 * UsbManager's list is Android over USB, and a pad that is only an input device is Android over
 * Bluetooth. Direct USB claims the pad, so Android lists no input device then (P4): [isInputDevice]
 * is not asked for it.
 */
fun detectConnection(route: Route, inUsbList: Boolean, isInputDevice: Boolean): Connection = when {
    route == Route.DIRECT_USB -> if (inUsbList) Connection.DIRECT_USB else Connection.NONE
    inUsbList -> Connection.ANDROID_USB
    isInputDevice -> Connection.ANDROID_BLUETOOTH
    else -> Connection.NONE
}

private fun detect(route: Route, pad: FoundPad): Connection =
    detectConnection(route, pad.transport == Transport.USB, pad.inputDeviceIds.isNotEmpty())

/** The connection of the live route; every state without a live route is [Connection.NONE]. */
fun connectionOf(state: RouteState): Connection = when (state) {
    is RouteState.DirectUsb -> detect(Route.DIRECT_USB, state.pad)
    is RouteState.AndroidMode -> detect(Route.ANDROID, state.pad)
    else -> Connection.NONE
}
