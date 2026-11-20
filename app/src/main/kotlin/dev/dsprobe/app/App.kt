package dev.dsprobe.app

import android.app.Application
import android.util.Log
import dev.dsprobe.app.route.PadSession
import java.io.File

class App : Application() {
    private var built: PadSession? = null

    /** Built on first use, on the main thread; the activity and nothing else reaches the pad through it. */
    val session: PadSession
        get() = built ?: PadSession(this).also { built = it }

    override fun onCreate() {
        super.onCreate()
        // never builds a session from a crashing thread: no session yet means no route to name
        CrashHandler.install(File(filesDir, CrashHandler.RECORD_FILE), { built?.describeRoute() ?: "no session" }) { Log.w("CrashHandler", it) }
    }
}
