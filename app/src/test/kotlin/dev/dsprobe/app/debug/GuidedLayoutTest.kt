package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NO_EVENT = "no android event"

class GuidedLayoutTest {
    private val cards = buildEndpointCards()

    @Test fun pagerPageAndNextBackAreOneIndex() {
        val tour = EndpointTour(cards)
        tour.start()
        tour.goTo(5)
        assertEquals(5, tour.index)
        assertEquals(cards[5].id, tour.current?.id)
        tour.next()
        assertEquals(6, tour.index)
        tour.back()
        tour.back()
        assertEquals(4, tour.index)
        assertEquals(cards[4].id, tour.current?.id)
        tour.goTo(tour.index)
        assertEquals(4, tour.index)
    }

    @Test fun theSummaryIsTheLastPageAndOutOfRangePagesClamp() {
        val tour = EndpointTour(cards)
        tour.start()
        assertEquals(cards.size + 1, tour.pageCount)
        tour.goTo(cards.size)
        assertNull(tour.current)
        assertFalse(tour.isRunning)
        tour.goTo(cards.size + 40)
        assertEquals(cards.size, tour.index)
        tour.goTo(cards.size - 1)
        assertTrue(tour.isRunning)
        tour.goTo(-7)
        assertEquals(0, tour.index)
    }

    @Test fun chevronsShowOnlyWhereThereAreMoreCardsAndOnlyBeforeAFirstSwipe() {
        val last = cards.size
        assertFalse(chevronBackShown(0, hasSwiped = false))
        assertTrue(chevronBackShown(1, hasSwiped = false))
        assertTrue(chevronNextShown(0, cards.size + 1, hasSwiped = false))
        assertTrue(chevronNextShown(last - 1, cards.size + 1, hasSwiped = false))
        assertFalse(chevronNextShown(last, cards.size + 1, hasSwiped = false))
        assertFalse(chevronBackShown(3, hasSwiped = true))
        assertFalse(chevronNextShown(3, cards.size + 1, hasSwiped = true))
    }

    @Test fun everyCardsMainAreaStaysUnderTheRowLimit() {
        assertTrue("no cards to measure", cards.isNotEmpty())
        var most = 0
        for (card in cards) {
            val rows = mainAreaOf(card).total
            assertTrue(
                "${card.id} spends $rows rows, limit ${AppLimits.GUIDED_MAIN_ROWS_MAX}",
                rows <= AppLimits.GUIDED_MAIN_ROWS_MAX,
            )
            most = maxOf(most, rows)
        }
        println("guided layout: ${cards.size} cards measured, most rows $most of ${AppLimits.GUIDED_MAIN_ROWS_MAX}")
    }

    @Test fun detailsContentIsNotInTheMainArea() {
        for (card in cards) {
            for (route in Route.entries) {
                val details = detailsTextOf(card, route, 0, NO_EVENT)
                assertTrue("${card.id} has no details", details.isNotEmpty())
                val lines = details.flatMap { it.lines() }.filter { it.isNotBlank() }
                for (connection in Connection.MEASURABLE) {
                    val main = mainTextOf(card, connection)
                    for (text in main) {
                        for (line in lines) {
                            assertFalse("${card.id}: \"$line\" is in the main area", text.contains(line))
                        }
                    }
                }
            }
        }
    }
}
