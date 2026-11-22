package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.INPUT_MAP
import dev.dsprobe.pad.Limits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        for (b in 1 until Limits.USB_INPUT_REPORT_BYTES) {
            assertEquals("byte $b", 0xff and claimed[b].inv(), free[b])
        }
        val whole = unclaimedRanges().filter { it.mask == 0xff }
        for ((a, b) in whole.zipWithNext()) {
            assertTrue(
                "ranges ${a.first}-${a.last} and ${b.first}-${b.last} should be one",
                b.first > a.last + 1,
            )
        }
        assertEquals(0, free[0])
        val rangeCards = cards.filter { it.kind == CardKind.INPUT_RANGE }
        assertEquals(unclaimedRanges().size, rangeCards.size)
        for (card in rangeCards) assertTrue(card.id, card.rawByte >= 1 && card.rawByteCount >= 1)
    }

    @Test fun theFirmwareReportAndAndroidCardsExist() {
        val ids = cards.map { it.id }.toSet()
        assertTrue("feature.0x20" in ids)
        assertTrue(cards.any { it.kind == CardKind.ANDROID })
    }

    @Test fun everyTagMatchesItsFactsConfirmationAndRowlessCardsAreUnknown() {
        val facts = factsConfirmed()
        for (card in cards) {
            val row = card.factsRow
            if (row == null) {
                assertEquals(card.id, Confidence.UNKNOWN, card.confidence)
                assertTrue(card.id, card.guess.startsWith("Nobody has decoded"))
                continue
            }
            val tags = row.split(", ").map { cited ->
                val confirmed = checkNotNull(facts[cited]) { "${card.id} cites $cited" }
                confidenceFromConfirmed(confirmed)
            }
            assertEquals(
                "${card.id} ($row)",
                tags.maxBy { it.ordinal },
                card.confidence,
            )
        }
        for ((row, tag) in ROW_CONFIDENCE) {
            assertEquals(row, confidenceFromConfirmed(checkNotNull(facts[row]) { row }), tag)
        }
    }

    @Test fun aCardMixingProvenAndDocumentedRowsIsOnlyDocumented() {
        val battery = cards.single { it.id == "in.battery" }
        assertEquals(Confidence.DOCUMENTED, battery.confidence)
        val sticks = cards.single { it.id == "in.stick.left" }
        assertEquals(Confidence.PROVEN, sticks.confidence)
        val audio = cards.single { it.id == "out.audio" }
        assertEquals("P35, P36", audio.factsRow)
    }

    @Test fun anUnknownCardAsksToWatchAndSaysWhichByteToShow() {
        val unknown = cards.filter { it.confidence == Confidence.UNKNOWN }
        assertTrue(unknown.isNotEmpty())
        for (card in unknown) {
            assertTrue(
                card.id,
                card.rawByte >= 1 &&
                    card.guess.contains(
                        "Try: tilt, press every button, touch the pad, plug headphones. Watch " +
                            "the value.",
                    ),
            )
        }
    }

    @Test fun noTwoCardsShareATitleOrAGuessText() {
        assertEquals("titles repeat", cards.size, cards.map { it.title }.toSet().size)
        val inputs = cards.filter {
            it.kind == CardKind.INPUT_FIELD ||
                it.kind == CardKind.INPUT_RANGE
        }
        assertTrue(inputs.size > 10)
        assertEquals("guess texts repeat", inputs.size, inputs.map { it.guess }.toSet().size)
        for (card in cards) assertTrue(card.id, card.guess.isNotBlank() && card.title.isNotBlank())
    }

    @Test fun everyInputGroupCardHasOneLocationLineAndOneGuessLinePerField() {
        for (card in cards.filter { it.kind == CardKind.INPUT_FIELD }) {
            assertEquals(card.id, card.fields.size, card.where.size)
            for ((field, place) in card.fields.zip(card.where)) {
                assertTrue("${field.id}: $place", place.startsWith(field.label + ": "))
                assertTrue(
                    "${card.id} has no guess line for ${field.label}",
                    card.guess.lines().any { it.startsWith(field.label + ": ") },
                )
            }
            val lines = card.guess.lines()
            assertEquals(card.id, card.fields.size + 2, lines.size)
        }
    }

    @Test fun everyInputFieldGuessNamesHowToMakeItMove() {
        for (card in cards.filter { it.kind == CardKind.INPUT_FIELD }) {
            assertTrue(card.id, card.guess.lines().last().length > 8)
        }
        assertEquals(INPUT_MAP.map { it.id }.toSet(), INPUT_GUESSES.keys)
    }

    @Test fun theStickExampleReadsAsTheSpecSaysItShould() {
        val stick = cards.single { it.id == "in.stick.left" }
        assertEquals("Left stick", stick.title)
        assertTrue(
            stick.guess,
            "x, left↔right: 0 = full left, 128 = centre, 255 = full right." in stick.guess,
        )
        assertTrue(
            stick.guess,
            stick.guess.endsWith("Push the left stick in every direction, then press it down."),
        )
    }

    @Test fun everyCardHasATryItActionOrAStatedReason() {
        for (card in cards) {
            when (val tryIt = card.tryIt) {
                is TryIt.Reason -> assertTrue(
                    "${card.id} has an empty reason",
                    tryIt.text.length > 10,
                )

                is TryIt.Action -> if (tryIt.kind !in
                    setOf(TryKind.MIC, TryKind.SPEAKER, TryKind.HEADPHONES)
                ) {
                    assertTrue(card.id, tryIt.choices.isNotEmpty())
                }
            }
        }
    }

    @Test fun everyOutputAndAudioCardTheAppCanDriveHasAnAction() {
        val driven = mapOf(
            "out.rumble" to TryKind.RUMBLE, "out.triggers" to TryKind.TRIGGERS,
            "out.lightbar" to TryKind.LIGHTBAR, "out.player_leds" to TryKind.PLAYER_LEDS,
            "out.mute_led" to TryKind.MUTE_LED,
            "out.rumble_both_modes" to TryKind.ALL_OFF, "audio.mic" to TryKind.MIC,
            "audio.speaker" to TryKind.SPEAKER, "audio.headphones" to TryKind.HEADPHONES,
            "android.vibrators" to TryKind.RUMBLE,
            "android.light.indicator" to TryKind.LIGHTBAR,
            "android.light.player" to TryKind.PLAYER_LEDS,
        )
        for ((id, kind) in driven) {
            val action = cards.single { it.id == id }.tryIt
            assertEquals(id, kind, (action as TryIt.Action).kind)
        }
    }

    @Test fun theMergedTriggerCardOffersBothTriggersAndOff() {
        val action = cards.single { it.id == "out.triggers" }.tryIt as TryIt.Action
        assertEquals(
            setOf(TryChoice.TRIGGER_R2, TryChoice.TRIGGER_L2, TryChoice.TRIGGER_OFF),
            action.choices.toSet(),
        )
    }

    @Test fun inputsAreStatedAsHandDrivenAndHdHapticsSaysWhyItCannotRun() {
        assertEquals(
            HAND_REASON,
            (cards.single { it.id == "in.stick.left" }.tryIt as TryIt.Reason).text,
        )
        assertTrue(
            (cards.single { it.id == "audio.haptics" }.tryIt as TryIt.Reason).text
                .contains("audio step, not built yet"),
        )
        assertTrue(AUDIO_STEP_REASON.contains("needs the audio step, not built yet"))
    }

    @Test fun theLocationLineOfAPlayerLedCardCarriesTheLiveByte() {
        val leds = cards.single { it.id == "out.player_leds" }
        val lines = locationLines(leds, 0x10, Route.DIRECT_USB, NO_EVENT)
        assertTrue(lines.single().endsWith("0b00010000 = 0x10, LED 5"))
        val bar = cards.single { it.id == "out.lightbar" }
        assertEquals(bar.where, locationLines(bar, 0x10, Route.DIRECT_USB, NO_EVENT))
    }

    private fun lines(id: String, route: Route) =
        locationLines(cards.single { it.id == id }, 0, route, NO_EVENT)

    @Test fun anAndroidModeInputCardNamesTheAndroidSourceNotTheByte() {
        val text = lines("in.stick.left", Route.ANDROID).joinToString("\n")
        assertTrue(text, "AXIS_X" in text)
        assertFalse(text, "byte" in text)
        assertTrue(
            lines("in.buttons.face", Route.ANDROID)
                .contains("Cross: KeyEvent.KEYCODE_BUTTON_A = 96"),
        )
        assertTrue(lines("in.gyro", Route.ANDROID).all { "TYPE_GYROSCOPE" in it })
        assertEquals(3, lines("in.gyro", Route.ANDROID).size)
    }

    @Test fun aDirectUsbInputCardKeepsTheByteLocation() {
        val text = lines("in.stick.left", Route.DIRECT_USB).joinToString("\n")
        assertTrue(text, "x, left↔right: report 0x01 · byte 1" in text)
        assertFalse(text, "AXIS_X" in text)
    }

    @Test fun anAndroidModeFieldWithNoAndroidSourceSaysSo() {
        assertTrue(lines("in.buttons.system", Route.ANDROID).contains("Mute: $NO_EVENT"))
        assertEquals(listOf("counter: $NO_EVENT"), lines("in.sequence", Route.ANDROID))
        assertEquals(listOf(NO_EVENT), lines(unclaimedId(), Route.ANDROID))
    }

    private fun unclaimedId(): String = cards.first { it.kind == CardKind.INPUT_RANGE }.id
}

private const val NO_EVENT = "Android gives no event for this (route: Android mode)"
