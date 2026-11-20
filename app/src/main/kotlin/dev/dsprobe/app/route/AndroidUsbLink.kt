package dev.dsprobe.app.route

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.Limits
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException

private const val OPEN_FAILED_NO_ERRNO = -1 // the Java API gives no errno

/** A claim of interface 3: one queued [UsbRequest] reads input, [UsbDeviceConnection.bulkTransfer] writes output (P7). */
class AndroidUsbLink(
    private val connection: UsbDeviceConnection,
    hid: UsbInterface,
    private val request: UsbRequest,
    private val outEndpoint: UsbEndpoint,
) : UsbLink {
    private val buffer = ByteBuffer.allocate(Limits.USB_INPUT_REPORT_BYTES)
    private var queued = false
    private val claimed = ArrayList<UsbInterface>()

    init {
        claimed.add(hid)
    }

    override fun readReport(timeoutMs: Int): ReadResult {
        if (!queued) {
            buffer.clear()
            if (!request.queue(buffer)) return ReadResult.Failed("queue refused")
            queued = true
        }
        val done = try {
            connection.requestWait(timeoutMs.toLong())
        } catch (e: TimeoutException) {
            return ReadResult.Timeout // the request stays queued; the next wait picks it up
        }
        queued = false
        if (done == null) return ReadResult.Failed("requestWait gave no request")
        if (done !== request) return ReadResult.Failed("requestWait returned another request")
        // dequeue leaves the position at the bytes transferred
        return ReadResult.Report(buffer.array().copyOf(buffer.position()))
    }

    override fun writeReport(report: ByteArray): Boolean {
        check(report.size == Limits.USB_OUTPUT_REPORT_BYTES) { "output report is ${report.size} bytes" }
        return connection.bulkTransfer(outEndpoint, report, report.size, AppLimits.OUTPUT_WRITE_TIMEOUT_MS) == report.size
    }

    override fun releaseInterfaces() {
        request.cancel()
        request.close()
        for (iface in claimed) connection.releaseInterface(iface)
        claimed.clear()
    }

    override fun close() = connection.close()
}

class OpenOutcome(val result: OpenResult, val link: UsbLink?)

private fun describe(iface: UsbInterface): List<EndpointDesc> = (0 until iface.endpointCount).map {
    val ep = iface.getEndpoint(it)
    EndpointDesc(ep.address, ep.type == UsbConstants.USB_ENDPOINT_XFER_INT, ep.maxPacketSize)
}

private fun findHid(device: UsbDevice): UsbInterface? = (0 until device.interfaceCount)
    .map { device.getInterface(it) }
    .firstOrNull { it.id == Limits.USB_HID_INTERFACE_ID && it.alternateSetting == 0 }

private fun endpointAt(iface: UsbInterface, address: Int): UsbEndpoint =
    checkNotNull((0 until iface.endpointCount).map { iface.getEndpoint(it) }.firstOrNull { it.address == address }) { "no endpoint $address" }

/** Checks permission and layout, opens the device, claims interface 3 (P4). Nothing is claimed unless the layout matches. */
fun claimHidInterface(manager: UsbManager, device: UsbDevice, log: (String) -> Unit): OpenOutcome {
    if (!manager.hasPermission(device)) return OpenOutcome(OpenResult.NoPermission, null)
    val hid = findHid(device) ?: return OpenOutcome(OpenResult.WrongLayout("no interface ${Limits.USB_HID_INTERFACE_ID}"), null)
    checkHidLayout(hid.interfaceClass == UsbConstants.USB_CLASS_HID, describe(hid))?.let {
        return OpenOutcome(OpenResult.WrongLayout(it), null)
    }
    val connection = manager.openDevice(device) ?: return OpenOutcome(OpenResult.ClaimFailed(OPEN_FAILED_NO_ERRNO), null)
    if (!connection.claimInterface(hid, true)) {
        boundary("close after refused claim", log) { connection.close() }
        return OpenOutcome(OpenResult.ClaimFailed(OPEN_FAILED_NO_ERRNO), null)
    }
    val request = UsbRequest()
    if (!request.initialize(connection, endpointAt(hid, Limits.USB_HID_IN_ENDPOINT_ADDRESS))) {
        boundary("release after refused request", log) { connection.releaseInterface(hid) }
        boundary("close after refused request", log) { connection.close() }
        return OpenOutcome(OpenResult.ClaimFailed(OPEN_FAILED_NO_ERRNO), null)
    }
    val link = AndroidUsbLink(connection, hid, request, endpointAt(hid, Limits.USB_HID_OUT_ENDPOINT_ADDRESS))
    return OpenOutcome(OpenResult.Opened, link)
}
