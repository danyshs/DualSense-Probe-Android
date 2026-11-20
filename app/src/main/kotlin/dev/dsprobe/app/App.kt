package dev.dsprobe.app

import android.app.Application
import dev.dsprobe.app.route.PadSession

class App : Application() {
    /** Built on first use, on the main thread; the activity and nothing else reaches the pad through it. */
    val session: PadSession by lazy { PadSession(this) }
}
