package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CARD_COUNT = 33

// card id to a phrase of the fact that moved onto it
private val MOVED: List<Pair<String, String>> = listOf(
    "in.buttons.face" to "down then up",
    "in.buttons.system" to "Mute sends no key event",
    "in.gyro" to "range 2048",
    "in.gyro" to "rad/s",
    "in.accel" to "range 4",
    "in.accel" to "m/s squared",
    "in.battery" to "getBatteryState()",
    "in.battery" to "capacity -0.01 and status unknown",
    "out.rumble" to "vibrator id 0",
    "out.rumble" to "id 1",
    "out.lightbar" to "'indicator'",
    "out.lightbar" to "'indicator'. On USB the request returns normally and nothing changes",
    "out.player_leds" to "'player-1' to 'player-5'",
    "out.player_leds" to "brightness only. On USB the request returns normally and nothing changes",
    "audio.mic" to "USB headset input",
    "audio.speaker" to "no output device",
)

class AndroidCardsTest {
    private val cards = buildEndpointCards()

    private fun card(id: String) = cards.single { it.id == id }

    private fun overlap(a: String, b: String) = controlUnder(a, b) || controlUnder(b, a)

    @Test fun noTwoCardsShareAListenedControlAFieldAnOutputFieldOrAnAndroidApi() {
        for ((i, a) in cards.withIndex()) {
            for (b in cards.drop(i + 1)) {
                val listens = a.listens.flatMap { x -> b.listens.filter { overlap(x, it) } }
                assertTrue("${a.id} and ${b.id} both listen to $listens", listens.isEmpty())
                val fields = a.fields.map { it.id }.intersect(b.fields.map { it.id }.toSet())
                assertTrue("${a.id} and ${b.id} share $fields", fields.isEmpty())
                val apis = a.androidApis.intersect(b.androidApis.toSet())
                assertTrue("${a.id} and ${b.id} share $apis", apis.isEmpty())
            }
            assertEquals(a.id, a.androidApis.size, a.androidApis.toSet().size)
        }
        val outputFields = cards.filter { it.kind == CardKind.OUTPUT }.flatMap { c -> c.fields }
        assertEquals(outputFields.size, outputFields.map { it.id }.toSet().size)
    }

    @Test fun eachMovedFactShowsOnItsCardInAndroidModeAndNotInDirectUsb() {
        for ((id, phrase) in MOVED) {
            val c = card(id)
            assertTrue("$id android: $phrase", phrase in guessText(c, Route.ANDROID))
            assertFalse("$id usb: $phrase", phrase in guessText(c, Route.DIRECT_USB))
            assertFalse("$id guess: $phrase", phrase in c.guess)
        }
    }

    @Test fun aMovedFactShowsOnNoOtherCard() {
        for ((id, phrase) in MOVED) {
            val others = cards.filter { it.id != id && phrase in guessText(it, Route.ANDROID) }
            assertTrue("$phrase also on ${others.map { it.id }}", others.isEmpty())
        }
    }

    @Test fun thereIsNoStandaloneAndroidCardAndTheCountMatches() {
        assertTrue(cards.none { it.id.startsWith("android.") })
        val expected = inputGroupCards().size + rangeCards().size + outputCards().size +
            audioCards().size + 1
        assertEquals(expected, cards.size)
        assertEquals(CARD_COUNT, cards.size)
    }

    @Test fun theFactsLeaveEveryOtherCardsAndroidTextAlone() {
        val touched = MOVED.map { it.first }.toSet()
        for (c in cards.filter { it.id !in touched }) assertTrue(c.id, c.androidApis.isEmpty())
        assertEquals(touched, cards.filter { it.androidApis.isNotEmpty() }.map { it.id }.toSet())
    }
}
