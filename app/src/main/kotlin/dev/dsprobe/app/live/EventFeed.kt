package dev.dsprobe.app.live

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.Route

/** One raw report (Direct USB) or one described event (Android mode). */
data class FeedEvent(val timeNs: Long, val route: Route, val raw: ByteArray?, val android: String?) {
    init {
        require((raw == null) != (android == null)) { "a feed event is raw or described, not both or neither" }
    }
}

/**
 * Per-event feed for Debug Mode's runner and Route trace. Publishes nothing while nobody is
 * subscribed; bounded, and an overflow drops the oldest and is counted.
 */
class EventFeed {
    private val lock = Any()
    private val buffer = ArrayDeque<FeedEvent>()
    private var subscribers = 0

    @Volatile
    var overflowCount = 0L
        private set

    val isActive: Boolean get() = synchronized(lock) { subscribers > 0 }

    fun subscribe() {
        synchronized(lock) { subscribers += 1 }
    }

    fun unsubscribe() {
        synchronized(lock) {
            check(subscribers > 0) { "unsubscribe without subscribe" }
            subscribers -= 1
            if (subscribers == 0) buffer.clear()
        }
    }

    fun publish(event: FeedEvent) {
        synchronized(lock) {
            if (subscribers == 0) return
            if (buffer.size >= AppLimits.FEED_EVENTS_MAX) {
                buffer.removeFirst()
                overflowCount += 1
            }
            buffer.addLast(event)
            check(buffer.size <= AppLimits.FEED_EVENTS_MAX) { "feed over its bound" }
        }
    }

    /** Everything published since the last drain, oldest first. */
    fun drain(): List<FeedEvent> = synchronized(lock) {
        val out = buffer.toList()
        buffer.clear()
        out
    }
}
