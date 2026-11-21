package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun tourAt(id: String): EndpointTour {
    val tour = EndpointTour(buildEndpointCards())
    tour.start()
    while (tour.current?.id != id) tour.next()
    return tour
}

class GuidedReactionTest {
    @Test fun theReactionQuestionAppearsOnlyAfterTryIt() {
        val tour = tourAt("out.lightbar")
        assertFalse("not asked before Try it", tour.reactionPending)
        tour.markTried()
        assertTrue("asked after Try it", tour.reactionPending)
    }

    @Test fun answeringEndsTheQuestionAndLeavingTheCardClearsIt() {
        val tour = tourAt("out.lightbar")
        tour.markTried()
        tour.answer(Verdict.NOT_SURE, "")
        assertFalse(tour.reactionPending)
        assertEquals(Verdict.NOT_SURE, tour.verdictNow)
        tour.next()
        assertFalse(tour.reactionPending)
        tour.back()
        assertFalse("an answered card does not ask again", tour.reactionPending)
    }

    @Test fun tryingAgainAfterAnsweringKeepsTheAnswerAndAsksNothingMore() {
        val tour = tourAt("out.lightbar")
        tour.markTried()
        tour.answer(Verdict.DOESNT, "")
        tour.markTried()
        assertFalse(tour.reactionPending)
        assertEquals(Verdict.DOESNT, tour.verdictNow)
    }

    @Test fun aCardNobodyTriedNeverAsksEvenAfterANextAndBack() {
        val tour = tourAt("out.rumble")
        tour.next()
        tour.markTried()
        tour.back()
        assertFalse(tour.reactionPending)
    }

    @Test fun theThreeAnswersAreInTheLogUnderTheirCard() {
        val tour = tourAt("out.mute_led")
        tour.markTried()
        tour.answer(Verdict.WORKS, "")
        val lines = tourLines(tour.answers)
        assertTrue(
            lines.toString(),
            lines.any {
                it.startsWith("endpoint id=out.mute_led") &&
                    "answer=WORKS" in it
            },
        )
        assertEquals(listOf("WORKS", "DOESNT", "NOT_SURE"), Verdict.entries.map { it.name })
    }

    @Test fun theSheetShowsOnlyThisEndpointsRowsNewestFirstUntilAllIsSwitchedOn() {
        val tour = tourAt("out.lightbar")
        val card = tour.current!!
        val rows = listOf(
            TraceRow(
                10L,
                Route.DIRECT_USB,
                "in.button.cross",
                "x",
                "Cross  pressed",
                "report 0x01",
            ),
            TraceRow(30L, Route.DIRECT_USB, "out.lightbar", "x", "Lightbar  newer", "sent ok"),
            TraceRow(20L, Route.DIRECT_USB, "out.lightbar", "x", "Lightbar  older", "sent ok"),
        )
        assertEquals(
            listOf("Lightbar  newer", "Lightbar  older"),
            sheetLines(rows, emptyList(), card, false).map {
                it.headline
            },
        )
        assertEquals(3, sheetLines(rows, emptyList(), card, true).size)
        assertEquals("Lightbar  newer", sheetLines(rows, emptyList(), card, true).first().headline)
    }

    @Test fun aCardWithNoRowsShowsWhatTheTourSawUnderIt() {
        val tour = tourAt(buildEndpointCards().first { it.kind == CardKind.INPUT_RANGE }.id)
        val seen = listOf(RowLines("raw changed", "00 → ff"))
        assertEquals(seen, sheetLines(emptyList(), seen, tour.current!!, false))
    }
}
