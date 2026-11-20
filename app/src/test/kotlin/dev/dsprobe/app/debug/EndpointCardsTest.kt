package dev.dsprobe.app.debug

import dev.dsprobe.pad.INPUT_MAP
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// the test JVM runs in app/, FACTS.md is one up
private fun factsConfirmed(): Map<String, String> {
    val file = File("../FACTS.md")
    check(file.isFile) { "FACTS.md not found from ${File(".").absolutePath}" }
    val out = HashMap<String, String>()
    for (line in file.readLines()) {
        if (!line.startsWith("| P")) continue
        val cells = line.trim().trim('|').split("|").map { it.trim() }
        out[cells.first()] = cells.last()
    }
    check(out.size > 40) { "read only ${out.size} FACTS rows" }
    return out
}

class EndpointCardsTest {
    private val cards = buildEndpointCards()

    @Test fun everyInputMapEntryHasACard() {
        val ids = cards.map { it.id }.toSet()
        for (src in INPUT_MAP) assertTrue(src.id, src.id in ids)
    }

    @Test fun everyUnclaimedBitOfReport01IsInOneRangeCardAndNothingElseIs() {
        val claimed = IntArray(Limits.USB_INPUT_REPORT_BYTES)
        for (src in INPUT_MAP) {
            if (src.widthBits == 16) {
                claimed[src.byteOffset] = 0xff
                claimed[src.byteOffset + 1] = 0xff
            } else {
                claimed[src.byteOffset] = claimed[src.byteOffset] or (src.bitMask ?: 0xff)
            }
        }
        val free = IntArray(Limits.USB_INPUT_REPORT_BYTES)
        for (range in unclaimedRanges()) {
            for (b in range.first..range.last) {
                assertEquals("byte $b is in two ranges", 0, free[b])
                free[b] = range.mask
            }
        }
        for (b in 1 until Limits.USB_INPUT_REPORT_BYTES) assertEquals("byte $b", 0xff and claimed[b].inv(), free[b])
        val whole = unclaimedRanges().filter { it.mask == 0xff }
        for ((a, b) in whole.zipWithNext()) assertTrue("ranges ${a.first}-${a.last} and ${b.first}-${b.last} should be one", b.first > a.last + 1)
        assertEquals(0, free[0])
        val rangeCards = cards.filter { it.kind == CardKind.INPUT_RANGE }
        assertEquals(unclaimedRanges().size, rangeCards.size)
        for (card in rangeCards) assertTrue(card.id, card.rawByte >= 1 && card.rawByteCount >= 1)
    }

    @Test fun everyOutputFieldAndTheFirmwareReportHaveACard() {
        val ids = cards.map { it.id }.toSet()
        for (field in OutputField.entries) assertTrue(field.name, "out.${field.name.lowercase()}" in ids)
        assertTrue("feature.0x20" in ids)
        assertTrue(cards.any { it.kind == CardKind.ANDROID })
    }

    @Test fun everyTagMatchesItsFactsConfirmationAndRowlessCardsAreUnknown() {
        val facts = factsConfirmed()
        for (card in cards) {
            val row = card.factsRow
            if (row == null) {
                assertEquals(card.id, Confidence.UNKNOWN, card.confidence)
                assertEquals(UNKNOWN_PROBABLY, card.probably)
                continue
            }
            val confirmed = checkNotNull(facts[row]) { "${card.id} cites $row, not in FACTS.md" }
            assertEquals("${card.id} ($row: $confirmed)", confidenceFromConfirmed(confirmed), card.confidence)
            assertTrue(card.id, card.probably.startsWith("Probably: ") && row in card.probably)
        }
        for ((row, tag) in ROW_CONFIDENCE) assertEquals(row, confidenceFromConfirmed(checkNotNull(facts[row]) { row }), tag)
    }

    @Test fun anUnknownCardAsksToWatchAndSaysWhichByteToShow() {
        val unknown = cards.filter { it.confidence == Confidence.UNKNOWN }
        assertTrue(unknown.isNotEmpty())
        for (card in unknown) assertTrue(card.id, card.rawByte >= 1 && card.probably.startsWith("Unknown."))
    }
}
