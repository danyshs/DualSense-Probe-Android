package dev.dsprobe.app.pad

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.core.content.ContextCompat

enum class Transport { USB, BLUETOOTH }

const val VENDOR_SONY = 0x054c
const val PRODUCT_DUALSENSE = 0x0ce6
const val PRODUCT_DUALSENSE_EDGE = 0x0df2

/** One Android input device, as much of it as matching needs. */
data class SeenInput(val id: Int, val vendorId: Int, val productId: Int)

/** One entry of UsbManager's list; [device] is null only in JVM tests. */
data class SeenUsb(val device: UsbDevice?, val vendorId: Int, val productId: Int)

data class FoundPad(
    val usbDevice: UsbDevice?,
    val inputDeviceIds: List<Int>,
    val productId: Int,
    val transport: Transport,
    val alsoPairedBluetooth: Boolean = false,
) {
    init {
        require(inputDeviceIds.isNotEmpty()) { "a pad with no input devices" }
        require(transport == Transport.USB || (usbDevice == null && !alsoPairedBluetooth)) { "bluetooth pad with USB data" }
    }
}

/**
 * Both sources at once. [usb] is set when UsbManager lists a pad, [inputPad] when Android has
 * input devices for one. A second pad empties both and sets [tooMany], so the machine never sees two.
 */
data class FindResult(val usb: SeenUsb?, val inputPad: FoundPad?, val tooMany: Boolean) {
    companion object {
        val NONE = FindResult(null, null, false)
    }
}

fun isPadProduct(vendorId: Int, productId: Int): Boolean =
    vendorId == VENDOR_SONY && (productId == PRODUCT_DUALSENSE || productId == PRODUCT_DUALSENSE_EDGE)

/**
 * The transport rule: USB if UsbManager lists the pad, otherwise Bluetooth. One USB pad plus up to
 * two input devices is one pad (the USB one, and a paired copy of it); Android has no public field
 * to tell them apart, so two different pads on two transports would be misread as one.
 */
fun classify(inputs: List<SeenInput>, usbs: List<SeenUsb>): FindResult {
    val padInputs = inputs.filter { isPadProduct(it.vendorId, it.productId) }
    val padUsbs = usbs.filter { isPadProduct(it.vendorId, it.productId) }
    val tooMany = padUsbs.size >= 2 || (padUsbs.size == 1 && padInputs.size > 2) || (padUsbs.isEmpty() && padInputs.size >= 2)
    if (tooMany) return FindResult(null, null, true)
    val usb = padUsbs.firstOrNull()
    if (usb != null) {
        val pad = if (padInputs.isEmpty()) null else
            FoundPad(usb.device, padInputs.map { it.id }, usb.productId, Transport.USB, alsoPairedBluetooth = padInputs.size == 2)
        return FindResult(usb, pad, false)
    }
    val only = padInputs.firstOrNull() ?: return FindResult.NONE
    return FindResult(null, FoundPad(null, listOf(only.id), only.productId, Transport.BLUETOOTH), false)
}

/** Watches Android's input devices and the USB attach and detach broadcasts; every change calls [onChange]. Main thread only. */
class PadFinder(private val context: Context) {
    private val inputManager: InputManager = checkNotNull(context.getSystemService(InputManager::class.java)) { "no InputManager" }
    private val usbManager: UsbManager = checkNotNull(context.getSystemService(UsbManager::class.java)) { "no UsbManager" }
    private var onChange: (() -> Unit)? = null

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = changed()
        override fun onInputDeviceRemoved(deviceId: Int) = changed()
        override fun onInputDeviceChanged(deviceId: Int) = changed()
    }

    // the broadcasts only say "look again"; the answer comes from the lists
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = changed()
    }

    private fun changed() {
        onChange?.invoke()
    }

    fun find(): FindResult {
        val inputs = inputManager.inputDeviceIds.toList().mapNotNull { id ->
            InputDevice.getDevice(id)?.let { SeenInput(id, it.vendorId, it.productId) }
        }
        val usbs = usbManager.deviceList.values.map { SeenUsb(it, it.vendorId, it.productId) }
        return classify(inputs, usbs)
    }

    fun start(onChange: () -> Unit) {
        check(this.onChange == null) { "finder already started" }
        this.onChange = onChange
        inputManager.registerInputDeviceListener(inputListener, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        if (onChange == null) return
        inputManager.unregisterInputDeviceListener(inputListener)
        context.unregisterReceiver(usbReceiver)
        onChange = null
    }
}
