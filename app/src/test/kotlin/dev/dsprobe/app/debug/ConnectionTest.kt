package dev.dsprobe.app.debug

import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.app.pad.PRODUCT_DUALSENSE
import dev.dsprobe.app.pad.Transport
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// the test JVM runs in app/, FACTS.md is one up
private fun factsRowIds(): Set<String> {
    val file = File("../FACTS.md")
    check(file.isFile) { "FACTS.md not found from ${File(".").absolutePath}" }
    val ids = file.readLines().filter { it.startsWith("| P") }
        .map { it.trim().trim('|').split("|").first().trim() }.toSet()
    check(ids.size > 40) { "read only ${ids.size} FACTS rows" }
    return ids
}

private fun pad(transport: Transport) = FoundPad(null, listOf(7), PRODUCT_DUALSENSE, transport)

class ConnectionTest {
    private val cards = buildEndpointCards()

    @Test fun directUsbIsOurRouteOnAPadInTheUsbList() {
        assertEquals(Connection.DIRECT_USB, detectConnection(Route.DIRECT_USB, true, false))
        assertEquals(Connection.DIRECT_USB, detectConnection(Route.DIRECT_USB, true, true))
        assertEquals(Connection.NONE, detectConnection(Route.DIRECT_USB, false, false))
    }

    @Test fun androidRouteTellsUsbFromBluetoothByTheUsbList() {
        assertEquals(Connection.ANDROID_USB, detectConnection(Route.ANDROID, true, true))
        assertEquals(Connection.ANDROID_USB, detectConnection(Route.ANDROID, true, false))
        assertEquals(Connection.ANDROID_BLUETOOTH, detectConnection(Route.ANDROID, false, true))
        assertEquals(Connection.NONE, detectConnection(Route.ANDROID, false, false))
    }

    @Test fun theLiveStateGivesTheConnection() {
        assertEquals(
            Connection.ANDROID_USB,
            connectionOf(RouteState.AndroidMode(pad(Transport.USB))),
        )
        assertEquals(
            Connection.ANDROID_BLUETOOTH,
            connectionOf(RouteState.AndroidMode(pad(Transport.BLUETOOTH))),
        )
        assertEquals(Connection.DIRECT_USB, connectionOf(RouteState.DirectUsb(pad(Transport.USB))))
        assertEquals(Connection.NONE, connectionOf(RouteState.NoPad()))
        assertEquals(Connection.NONE, connectionOf(RouteState.TooMany))
    }

    @Test fun everyCardHasAnExpectationOrTheNotMeasuredTextOnEachConnection() {
        for (card in cards) {
            for (c in Connection.MEASURABLE) {
                val e = expectationOn(card.id, c)
                assertTrue("${card.id} $c", e.text.isNotBlank())
                if (e.rows.isEmpty()) assertEquals("${card.id} $c", NOT_MEASURED_TEXT, e.text)
                if (e.text == NOT_MEASURED_TEXT) assertTrue(e.rows.isEmpty())
            }
        }
    }

    @Test fun everyTableKeyIsARealCard() {
        val ids = cards.map { it.id }.toSet()
        for (key in EXPECTATIONS.keys) assertTrue(key, key in ids)
    }

    @Test fun everyCitedRowExistsInFacts() {
        val facts = factsRowIds()
        for (card in cards) {
            for (c in Connection.MEASURABLE) {
                for (row in expectationOn(card.id, c).rows) {
                    assertTrue("${card.id} cites $row", row in facts)
                }
            }
        }
    }

    @Test fun lightbarAndBatteryDifferBetweenAndroidUsbAndBluetooth() {
        for (id in listOf("out.lightbar", "in.battery", "out.player_leds")) {
            val usb = expectationOn(id, Connection.ANDROID_USB)
            val bt = expectationOn(id, Connection.ANDROID_BLUETOOTH)
            assertNotEquals(id, usb.text, bt.text)
            assertEquals(id, listOf("P79"), usb.rows)
        }
        assertEquals(
            listOf("P77"),
            expectationOn("out.lightbar", Connection.ANDROID_BLUETOOTH).rows,
        )
        assertEquals(listOf("P78"), expectationOn("in.battery", Connection.ANDROID_BLUETOOTH).rows)
        assertEquals(listOf("P43"), expectationOn("out.lightbar", Connection.DIRECT_USB).rows)
    }

    @Test fun theOtherConnectionsAreTheTwoNotLive() {
        assertEquals(
            listOf(Connection.ANDROID_USB, Connection.ANDROID_BLUETOOTH),
            otherConnections(Connection.DIRECT_USB),
        )
        assertEquals(3, otherConnections(Connection.NONE).size)
    }

    @Test fun theLogLinesCarryTheConnection() {
        val tour = EndpointTour(cards)
        tour.start()
        tour.noteConnection(Connection.ANDROID_BLUETOOTH)
        val lines = tourLines(tour.answers)
        assertTrue(lines.toString(), lines.single().contains("connection=android_bluetooth"))
        tour.observe(
            listOf(TraceRow(1L, Route.ANDROID, tour.current!!.listens.first(), "x", "head", "loc")),
            null,
        )
        val seen = tourLines(tour.answers).filter { it.startsWith("endpoint-seen") }
        assertTrue(
            seen.toString(),
            seen.isNotEmpty() && seen.all { "connection=android_bluetooth" in it },
        )
    }
}
