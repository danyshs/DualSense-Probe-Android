package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun crossRow() =
    TraceRow(0L, Route.DIRECT_USB, "in.button.cross", "x", "Cross  pressed", "report 0x01")

private fun tourAtCross(): EndpointTour {
    val tour = EndpointTour(buildEndpointCards())
    tour.start()
    while (tour.current?.id != "in.button.cross") tour.next()
    return tour
}

class EndpointTourTest {
    @Test fun aMatchingInputMarksTheCardSeenAndDoesNotAdvance() {
        val tour = tourAtCross()
        val at = tour.index
        tour.observe(listOf(crossRow()), null)
        assertEquals(at, tour.index)
        assertEquals("in.button.cross", tour.current?.id)
        assertEquals("Cross  pressed", tour.seenNow.single().headline)
    }

    @Test fun rowsOfOtherControlsAreNotKeptUnderTheCard() {
        val tour = tourAtCross()
        tour.observe(listOf(TraceRow(0L, Route.DIRECT_USB, "in.button.circle", "x")), null)
        assertTrue(tour.seenNow.isEmpty())
    }

    @Test fun nextAndBackMoveExactlyOneCard() {
        val tour = EndpointTour(buildEndpointCards())
        tour.start()
        assertEquals(0, tour.index)
        tour.next()
        assertEquals(1, tour.index)
        tour.next()
        tour.back()
        assertEquals(1, tour.index)
        tour.back()
        tour.back()
        assertEquals("back stops at the first card", 0, tour.index)
    }

    @Test fun nextPastTheLastCardEndsTheTourAndAnswersAreKeptPerCard() {
        val cards = buildEndpointCards().take(2)
        val tour = EndpointTour(cards)
        tour.start()
        tour.answer(Verdict.WORKS, "felt it")
        tour.next()
        tour.next()
        assertNull(tour.current)
        assertEquals(listOf(Verdict.WORKS, null), tour.answers.map { it.verdict })
        assertEquals("felt it", tour.answers.first().note)
    }

    @Test fun whatWasSeenIsInTheLogUnderItsEndpoint() {
        val tour = tourAtCross()
        tour.observe(listOf(crossRow()), null)
        val lines = tourLines(tour.answers)
        assertTrue(
            lines.toString(),
            lines.any {
                it.startsWith("endpoint-seen id=in.button.cross Cross  pressed")
            },
        )
    }

    @Test fun aFreeRangeCardNotesItsRawChange() {
        val tour = EndpointTour(buildEndpointCards())
        tour.start()
        while (tour.current?.kind != CardKind.INPUT_RANGE) tour.next()
        val card = tour.current!!
        val a = ByteArray(64)
        val b = ByteArray(64)
        b[card.rawByte] = card.rawMask.toByte()
        tour.observe(emptyList(), a)
        tour.observe(emptyList(), b)
        assertEquals(1, tour.seenNow.size)
        assertEquals(card.id, tour.current?.id)
    }
}
