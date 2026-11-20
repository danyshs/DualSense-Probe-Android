package dev.dsprobe.app.route

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat

/**
 * The USB permission question: a mutable PendingIntent (the system adds the extras) and a one-shot
 * receiver (P8). An answer that comes after [cancel] finds no receiver and is dropped.
 */
class UsbPermission(private val context: Context, private val log: (String) -> Unit) {
    private var receiver: BroadcastReceiver? = null
    private val action = context.packageName + ".USB_PERMISSION"

    /** False when the request could not be made at all; the caller treats that as a refusal. */
    fun request(manager: UsbManager, device: UsbDevice, onAnswer: (Boolean) -> Unit): Boolean {
        cancel()
        val oneShot = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != action) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                cancel()
                boundary("permission answer", log) { onAnswer(granted) }
            }
        }
        // explicit package: a mutable PendingIntent with an implicit Intent is refused on newer Android
        val intent = Intent(action).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE)
        val asked = boundary("permission request", log) {
            ContextCompat.registerReceiver(context, oneShot, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = oneShot
            manager.requestPermission(device, pending)
        }
        if (!asked) cancel()
        return asked
    }

    fun cancel() {
        val r = receiver ?: return
        receiver = null
        boundary("permission receiver unregister", log) { context.unregisterReceiver(r) }
    }
}
