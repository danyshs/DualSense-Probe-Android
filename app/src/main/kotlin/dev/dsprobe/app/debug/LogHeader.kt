package dev.dsprobe.app.debug

import android.content.Context
import android.os.Build
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.BuildConfig
import dev.dsprobe.app.pad.PRODUCT_DUALSENSE
import dev.dsprobe.app.pad.PRODUCT_DUALSENSE_EDGE
import dev.dsprobe.app.pad.Transport
import dev.dsprobe.app.pad.VENDOR_SONY
import dev.dsprobe.pad.FirmwareInfo
import java.io.File
import java.security.MessageDigest

private const val UNKNOWN = "unknown"
private const val KERNEL_CHARS_MAX = 300

/**
 * The pad's lines of the header. Firmware and hardware come from the Direct USB feature read only;
 * on any other transport they are "unknown". No serial number, no Bluetooth address.
 */
fun padHeaderFields(productId: Int?, transport: Transport?, alsoPairedBluetooth: Boolean, firmware: FirmwareInfo?): List<Pair<String, String>> {
    val name = when (productId) {
        PRODUCT_DUALSENSE -> "DualSense"
        PRODUCT_DUALSENSE_EDGE -> "DualSense Edge"
        else -> UNKNOWN
    }
    return listOf(
        "transport" to (transport?.name?.lowercase() ?: UNKNOWN),
        "also_paired_over_bluetooth" to alsoPairedBluetooth.toString(),
        "pad_vendor" to "0x%04x".format(VENDOR_SONY),
        "pad_product" to (productId?.let { "0x%04x ($name)".format(it) } ?: UNKNOWN),
        "pad_firmware_update_version" to (firmware?.let { "0x%04x".format(it.updateVersion.toInt()) } ?: UNKNOWN),
        "pad_firmware_version" to (firmware?.let { "0x%08x".format(it.firmware.toLong()) } ?: UNKNOWN),
        "pad_hardware_version" to (firmware?.let { "0x%08x".format(it.hardware.toLong()) } ?: UNKNOWN),
        "pad_firmware_build_date" to (firmware?.buildDate ?: UNKNOWN),
    )
}

/** SHA-256 of a file as lowercase hex, read in fixed blocks; the block count is bounded by the file's size. */
fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(AppLimits.APK_HASH_BUFFER_BYTES)
    val blocksMax = file.length() / buffer.size + 2
    file.inputStream().use { input ->
        var blocks = 0L
        while (true) {
            check(blocks++ < blocksMax) { "file grew while it was hashed" }
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun kernelVersion(): String =
    try {
        File("/proc/version").readText().trim().take(KERNEL_CHARS_MAX)
    } catch (e: java.io.IOException) {
        "unreadable: ${e.javaClass.simpleName}"
    }

/** The app, phone and Android lines: commit, APK hash, build fingerprint, kernel, security patch. */
fun phoneHeaderFields(context: Context): List<Pair<String, String>> = listOf(
    "app_version" to BuildConfig.VERSION_NAME,
    "app_version_code" to BuildConfig.VERSION_CODE.toString(),
    "commit" to BuildConfig.GIT_COMMIT,
    "apk_sha256" to sha256Hex(File(context.applicationInfo.sourceDir)),
    "time" to java.time.Instant.now().toString(),
    "phone_model" to Build.MODEL,
    "android_release" to Build.VERSION.RELEASE,
    "android_sdk" to Build.VERSION.SDK_INT.toString(),
    "security_patch" to Build.VERSION.SECURITY_PATCH,
    "build_fingerprint" to Build.FINGERPRINT,
    "kernel" to kernelVersion(),
)
