package dev.dsprobe.app.debug

import dev.dsprobe.pad.INPUT_MAP
import dev.dsprobe.pad.OutputField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardCoverageTest {
    private val cards = buildEndpointCards()

    private fun owners(fieldId: String) = cards.filter { c -> c.fields.any { it.id == fieldId } }

    private fun cardsUnder(prefix: String) =
        cards.filter { c -> c.fields.any { controlUnder(it.id, prefix) } }

    @Test fun everyInputMapEntryIsOnExactlyOneCard() {
        for (src in INPUT_MAP) assertEquals(src.id, 1, owners(src.id).size)
    }

    @Test fun everyOutputFieldIsOnExactlyOneCard() {
        for (field in OutputField.entries) {
            assertEquals(field.name, 1, owners("out.${field.name.lowercase()}").size)
        }
    }

    @Test fun everyUnclaimedRangeIsOnExactlyOneCard() {
        val ranges = unclaimedRanges()
        for (r in ranges) {
            val own = cards.filter {
                it.kind == CardKind.INPUT_RANGE && it.rawByte == r.first &&
                    it.rawByteCount == r.last - r.first + 1 && it.rawMask == r.mask
            }
            assertEquals("$r", 1, own.size)
        }
        assertEquals(ranges.size, cards.count { it.kind == CardKind.INPUT_RANGE })
    }

    @Test fun noFieldIsOnTwoCardsAndNoCardIsEmpty() {
        val all = cards.flatMap { c -> c.fields.map { it.id } }
        assertEquals(all.toSet().size, all.size)
        for (card in cards) assertTrue(card.id, card.fields.isNotEmpty())
    }

    @Test fun oneCardPerFingerGyroAccelAndStickHoldingOnlyItsOwnFields() {
        val expected = mapOf(
            "in.touch.0" to 6,
            "in.touch.1" to 6,
            "in.gyro" to 3,
            "in.accel" to 3,
            "in.stick.left" to 2,
            "in.stick.right" to 2,
        )
        for ((prefix, count) in expected) {
            val own = cardsUnder(prefix)
            assertEquals(prefix, 1, own.size)
            val under = own.single().fields.count { controlUnder(it.id, prefix) }
            assertEquals(prefix, count, under)
        }
        // a stick card also owns that stick's click, and nothing else
        for ((side, click) in mapOf("left" to "in.button.l3", "right" to "in.button.r3")) {
            val ids = cards.single { it.id == "in.stick.$side" }.fields.map { it.id }.toSet()
            assertEquals(setOf("in.stick.$side.x", "in.stick.$side.y", click), ids)
        }
        for (id in listOf("in.touch.0", "in.touch.1", "in.gyro", "in.accel")) {
            val card = cards.single { it.id == id }
            assertTrue(id, card.fields.all { controlUnder(it.id, id) })
        }
    }

    @Test fun theMergedCardsAreFarFewerThanTheFields() {
        assertTrue("${cards.size} cards", cards.size <= 48)
        assertTrue(cards.size < INPUT_MAP.size)
    }
}
