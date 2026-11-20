package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun row(n: Int) = TraceRow(n.toLong(), Route.ANDROID, "in.button.cross", "row $n")

class DebugHubTest {
    @Test fun theFeedPublishesOnlyWhileTheScreenIsOpen() {
        val feed = EventFeed()
        val hub = DebugHub(feed)
        assertFalse(feed.isActive)
        hub.open()
        assertTrue(feed.isActive)
        hub.open()
        hub.close()
        assertFalse("one close ends one open, however often it was opened", feed.isActive)
        hub.close()
    }

    @Test fun pumpDrainsOnceAndFeedsTheTraceTheStatsAndTheRunner() {
        val feed = EventFeed()
        val hub = DebugHub(feed)
        hub.open()
        feed.publish(FeedEvent(5_000_000L, Route.ANDROID, null, traceLine("in.button.cross", Route.ANDROID, "x"), "in.button.cross"))
        hub.pump(null)
        assertEquals(1, hub.trace.rows.size)
        assertEquals(emptyList<FeedEvent>(), feed.drain())
    }

    @Test fun pumpWhileClosedReadsNothing() {
        val feed = EventFeed()
        val hub = DebugHub(feed)
        hub.pump(null)
        assertEquals(emptyList<TraceRow>(), hub.trace.rows)
    }

    @Test fun savedRowsAreBoundedAndEachAppearsOnceInTheLog() {
        val hub = DebugHub(EventFeed())
        hub.save((0 until AppLimits.ROUTE_TRACE_ROWS_MAX + 50).map(::row))
        hub.save(listOf(row(0), row(1)))
        assertEquals(AppLimits.ROUTE_TRACE_ROWS_MAX, hub.logRows().size)
        assertEquals(AppLimits.ROUTE_TRACE_ROWS_MAX - 1L, hub.logRows().first().timeMs)
    }
}
