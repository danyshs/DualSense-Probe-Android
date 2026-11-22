package dev.dsprobe.app.debug

import dev.dsprobe.app.output.ledMaskText
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.INPUT_MAP
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
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
        for (b in 1 until Limits.USB_INPUT_REPORT_BYTES) {
            assertEquals(
                "byte $b",
                0xff and claimed[b].inv(),
                free[b],
            )
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

    @Test fun everyOutputFieldAndTheFirmwareReportHaveACard() {
        val ids = cards.map { it.id }.toSet()
        for (field in OutputField.entries) {
            assertTrue(
                field.name,
                "out.${field.name.lowercase()}" in ids,
            )
        }
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
            val confirmed = checkNotNull(facts[row]) { "${card.id} cites $row, not in FACTS.md" }
            assertEquals(
                "${card.id} ($row: $confirmed)",
                confidenceFromConfirmed(confirmed),
                card.confidence,
            )
        }
        for ((row, tag) in ROW_CONFIDENCE) {
            assertEquals(
                row,
                confidenceFromConfirmed(
                    checkNotNull(facts[row]) {
                        row
                    },
                ),
                tag,
            )
        }
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

    @Test fun everyInputCardHasItsOwnGuessAndNoTwoShareOneText() {
        val inputs = cards.filter {
            it.kind == CardKind.INPUT_FIELD ||
                it.kind == CardKind.INPUT_RANGE
        }
        assertTrue(inputs.size > 50)
        assertEquals("guess texts repeat", inputs.size, inputs.map { it.guess }.toSet().size)
        assertEquals("titles repeat", inputs.size, inputs.map { it.title }.toSet().size)
        for (card in inputs) assertTrue(card.id, card.guess.isNotBlank() && card.title.isNotBlank())
    }

    @Test fun everyInputFieldGuessNamesHowToMakeItMove() {
        for (src in INPUT_MAP) {
            val guess = checkNotNull(INPUT_GUESSES[src.id]) { src.id }
            assertTrue("${src.id} has no move text", guess.move.length > 8)
            val card = cards.single { it.id == src.id }
            assertTrue(
                "${src.id}: the card text must end with how to move it",
                card.guess.endsWith(guess.move),
            )
        }
        assertEquals(INPUT_MAP.map { it.id }.toSet(), INPUT_GUESSES.keys)
    }

    @Test fun theStickAndLedExamplesReadAsTheSpecSaysTheyShould() {
        val stick = cards.single { it.id == "in.stick.left.x" }
        assertEquals("Left stick, left↔right", stick.title)
        assertTrue(stick.guess, "0 = full left, 128 = centre, 255 = full right." in stick.guess)
        assertTrue(stick.guess, stick.guess.endsWith("Push the left stick sideways."))
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

    @Test fun everyNonButtonOutputAndAudioCardTheAppCanDriveHasAnAction() {
        val driven = mapOf(
            "out.rumble" to TryKind.RUMBLE, "out.right_trigger" to TryKind.TRIGGERS,
            "out.left_trigger" to TryKind.TRIGGERS,
            "out.lightbar" to TryKind.LIGHTBAR, "out.player_leds" to TryKind.PLAYER_LEDS,
            "out.mute_led" to TryKind.MUTE_LED,
            "out.rumble_both_modes" to TryKind.ALL_OFF, "audio.mic" to TryKind.MIC,
            "audio.speaker" to TryKind.SPEAKER, "audio.headphones" to TryKind.HEADPHONES,
            "android.vibrator.0" to TryKind.RUMBLE, "android.vibrator.1" to TryKind.RUMBLE,
            "android.light.indicator" to TryKind.LIGHTBAR,
            "android.light.player" to TryKind.PLAYER_LEDS,
        )
        for ((id, kind) in driven) {
            val action = cards.single { it.id == id }.tryIt
            assertEquals(id, kind, (action as TryIt.Action).kind)
        }
        for (field in OutputField.entries) {
            assertTrue(
                field.name,
                cards.any {
                    it.id ==
                        "out.${field.name.lowercase()}"
                },
            )
        }
    }

    @Test fun inputsAreStatedAsHandDrivenAndHdHapticsSaysWhyItCannotRun() {
        assertEquals(
            HAND_REASON,
            (
                cards.single {
                    it.id == "in.stick.left.x"
                }.tryIt as TryIt.Reason
                ).text,
        )
        assertTrue(
            (
                cards.single {
                    it.id == "audio.haptics"
                }.tryIt as TryIt.Reason
                ).text.contains("audio step, not built yet"),
        )
        assertTrue(AUDIO_STEP_REASON.contains("needs the audio step, not built yet"))
    }

    @Test fun theLocationLineOfAPlayerLedCardCarriesTheLiveByte() {
        val leds = cards.single { it.id == "out.player_leds" }
        assertTrue(locationLine(leds, 0x10, Route.DIRECT_USB, NO_EVENT).endsWith("0b00010000 = 0x10, LED 5"))
        assertEquals(
            cards.single { it.id == "out.lightbar" }.where,
            locationLine(
                cards.single {
                    it.id ==
                        "out.lightbar"
                },
                0x10,
                Route.DIRECT_USB,
                NO_EVENT,
            ),
        )
    }

    private fun line(id: String, route: Route) =
        locationLine(cards.single { it.id == id }, 0, route, NO_EVENT)

    @Test fun anAndroidModeInputCardNamesTheAndroidSourceNotTheByte() {
        val text = line("in.stick.left.x", Route.ANDROID)
        assertTrue(text, "AXIS_X" in text)
        assertFalse(text, "byte" in text)
        assertEquals("KeyEvent.KEYCODE_BUTTON_A = 96", line("in.button.cross", Route.ANDROID))
        assertTrue(line("in.gyro.x", Route.ANDROID).contains("TYPE_GYROSCOPE"))
    }

    @Test fun aDirectUsbInputCardKeepsTheByteLocation() {
        val text = line("in.stick.left.x", Route.DIRECT_USB)
        assertTrue(text, "byte 1" in text)
        assertFalse(text, "AXIS_X" in text)
    }

    @Test fun anAndroidModeFieldWithNoAndroidSourceSaysSo() {
        assertEquals(NO_EVENT, line("in.button.mute", Route.ANDROID))
        assertEquals(NO_EVENT, line("in.sequence", Route.ANDROID))
        assertEquals(NO_EVENT, line(unclaimedId(), Route.ANDROID))
    }

    private fun unclaimedId(): String = cards.first { it.kind == CardKind.INPUT_RANGE }.id
}

private const val NO_EVENT = "Android gives no event for this (route: Android mode)"
