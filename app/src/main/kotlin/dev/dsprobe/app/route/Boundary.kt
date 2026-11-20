package dev.dsprobe.app.route

/**
 * The one place Android's side of a call is allowed to throw at us: a release, a close, a receiver.
 * Logs and returns false instead of crashing; an interrupt is handed back to the thread.
 */
inline fun boundary(label: String, log: (String) -> Unit, block: () -> Unit): Boolean =
    try {
        block()
        true
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        log("$label interrupted")
        false
    } catch (e: Exception) {
        log("$label failed: $e")
        false
    }
