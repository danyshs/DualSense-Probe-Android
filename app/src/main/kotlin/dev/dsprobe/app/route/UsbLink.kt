package dev.dsprobe.app.route

import dev.dsprobe.pad.Limits

/** What one wait on the input endpoint gave. */
sealed interface ReadResult {
    data object Timeout : ReadResult
    class Report(val bytes: ByteArray) : ReadResult
    data class Failed(val detail: String) : ReadResult
}

/**
 * What the route needs from an open claim of the HID interface. The Android side is [AndroidUsbLink];
 * tests fake it so the release order and the reader can run on the JVM.
 */
interface UsbLink {
    /** Waits up to [timeoutMs] for one input report. */
    fun readReport(timeoutMs: Int): ReadResult

    /** True when the pad took the whole report. */
    fun writeReport(report: ByteArray): Boolean

    /** Gives back every interface this link claimed and drops its pending request. May throw Android's own errors. */
    fun releaseInterfaces()

    fun close()
}

/** One endpoint as the layout check needs it. */
data class EndpointDesc(val address: Int, val isInterrupt: Boolean, val maxPacketBytes: Int)

/** Null when interface 3 is the HID layout we expect (P2); else what differs, for the screen. */
fun checkHidLayout(isHidClass: Boolean, endpoints: List<EndpointDesc>): String? {
    if (!isHidClass) return "interface ${Limits.USB_HID_INTERFACE_ID} is not HID class"
    for (address in listOf(Limits.USB_HID_IN_ENDPOINT_ADDRESS, Limits.USB_HID_OUT_ENDPOINT_ADDRESS)) {
        val ep = endpoints.firstOrNull { it.address == address } ?: return "no endpoint 0x%02x".format(address)
        if (!ep.isInterrupt) return "endpoint 0x%02x is not interrupt".format(address)
        if (ep.maxPacketBytes != Limits.USB_HID_ENDPOINT_PACKET_BYTES) {
            return "endpoint 0x%02x packet is ${ep.maxPacketBytes} bytes".format(address)
        }
    }
    return null
}
