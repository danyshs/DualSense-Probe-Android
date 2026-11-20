package dev.dsprobe.app.route

import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.pad.PRODUCT_DUALSENSE
import dev.dsprobe.app.pad.SeenUsb
import dev.dsprobe.app.pad.Transport
import dev.dsprobe.app.pad.VENDOR_SONY

// the UsbDevice object can't exist in a JVM test, so the pads carry null in its place
val usbPad = FoundPad(null, listOf(7), PRODUCT_DUALSENSE, Transport.USB)
val btPad = FoundPad(null, listOf(8), PRODUCT_DUALSENSE, Transport.BLUETOOTH)
val usbHit = SeenUsb(null, VENDOR_SONY, PRODUCT_DUALSENSE)

fun androidMode(pad: FoundPad = usbPad, notice: Notice? = null) =
    RouteState.AndroidMode(pad, reductions(Route.ANDROID, pad.transport), notice)
