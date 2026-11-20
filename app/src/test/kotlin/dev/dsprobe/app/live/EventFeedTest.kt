package dev.dsprobe.app.live

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Test

private fun event(n: Int) = FeedEvent(n.toLong(), Route.ANDROID, null, "e$n")

class EventFeedTest {
    @Test fun publishesNothingWithoutASubscriber() {
        val f = EventFeed()
        f.publish(event(1))
        f.subscribe()
        assertEquals(emptyList<FeedEvent>(), f.drain())
    }

    @Test fun overflowDropsTheOldestAndCountsIt() {
        val f = EventFeed()
        f.subscribe()
        for (i in 0 until AppLimits.FEED_EVENTS_MAX + 3) f.publish(event(i))
        val out = f.drain()
        assertEquals(AppLimits.FEED_EVENTS_MAX, out.size)
        assertEquals(3L, f.overflowCount)
        assertEquals(3L, out.first().timeNs)
    }

    @Test fun lastUnsubscribeEmptiesTheBuffer() {
        val f = EventFeed()
        f.subscribe()
        f.publish(event(1))
        f.unsubscribe()
        f.subscribe()
        assertEquals(emptyList<FeedEvent>(), f.drain())
    }
}
