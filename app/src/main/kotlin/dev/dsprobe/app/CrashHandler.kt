package dev.dsprobe.app

import dev.dsprobe.app.route.DirectUsbRoute
import dev.dsprobe.app.route.boundary
import java.io.File
import java.util.Date

/**
 * A crash never strands the pad: hand the claim back first, write what happened, then let the
 * previous handler crash the app as usual. Never joins a thread (the crashing one may be the writer).
 */
object CrashHandler {
    const val RECORD_FILE = "crash-record.txt"

    fun install(recordFile: File, describeRoute: () -> String, log: (String) -> Unit) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            boundary("crash release", log) { DirectUsbRoute.current?.emergencyRelease() }
            boundary("crash record", log) { recordFile.writeBytes(record(thread, error, describeRoute())) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Stack trace and route state, cut to the limit. */
    internal fun record(thread: Thread, error: Throwable, route: String): ByteArray {
        val text = "${Date()}\nthread ${thread.name}\nroute $route\n${error.stackTraceToString()}"
        val bytes = text.toByteArray()
        return if (bytes.size <= AppLimits.CRASH_RECORD_BYTES_MAX) bytes else bytes.copyOf(AppLimits.CRASH_RECORD_BYTES_MAX)
    }
}
