package dev.dsprobe.app.debug

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.pad.INPUT_MAP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One fixture, the control it must name, and the place the row must give. */
private class Expect(val fixture: String, val control: String, val place: String, val facts: String, val reader: String)

private val BUTTON_FIXTURES = listOf(
    Expect("square", "in.button.square", "byte 8 mask 0x10", "P13", "heldButtons"),
    Expect("cross", "in.button.cross", "byte 8 mask 0x20", "P13", "heldButtons"),
    Expect("circle", "in.button.circle", "byte 8 mask 0x40", "P13", "heldButtons"),
    Expect("triangle", "in.button.triangle", "byte 8 mask 0x80", "P13", "heldButtons"),
    Expect("l1", "in.button.l1", "byte 9 mask 0x01", "P14", "heldButtons"),
    Expect("r1", "in.button.r1", "byte 9 mask 0x02", "P14", "heldButtons"),
    Expect("l2-button", "in.button.l2", "byte 9 mask 0x04", "P14", "heldButtons"),
    Expect("r2-button", "in.button.r2", "byte 9 mask 0x08", "P14", "heldButtons"),
    Expect("create", "in.button.create", "byte 9 mask 0x10", "P14", "heldButtons"),
    Expect("options", "in.button.options", "byte 9 mask 0x20", "P14", "heldButtons"),
    Expect("l3", "in.button.l3", "byte 9 mask 0x40", "P14", "heldButtons"),
    Expect("r3", "in.button.r3", "byte 9 mask 0x80", "P14", "heldButtons"),
    Expect("ps", "in.button.ps", "byte 10 mask 0x01", "P15", "heldButtons"),
    Expect("touchpad-click", "in.button.touchpad_click", "byte 10 mask 0x02", "P15", "heldButtons"),
    Expect("mute", "in.button.mute", "byte 10 mask 0x04", "P15", "heldButtons"),
)

private val ANALOG_FIXTURES = listOf(
    Expect("l2-full", "in.trigger.l2", "byte 5", "P11", "parseUsbInput l2, r2"),
    Expect("r2-full", "in.trigger.r2", "byte 6", "P11", "parseUsbInput l2, r2"),
    Expect("left-x-max", "in.stick.left.x", "byte 1", "P10", "parseUsbInput leftX..rightY"),
    Expect("left-y-max", "in.stick.left.y", "byte 2", "P10", "parseUsbInput leftX..rightY"),
    Expect("right-x-max", "in.stick.right.x", "byte 3", "P10", "parseUsbInput leftX..rightY"),
    Expect("right-y-max", "in.stick.right.y", "byte 4", "P10", "parseUsbInput leftX..rightY"),
    Expect("touch-0", "in.touch.0.active", "byte 33 mask 0x80", "P17", "touchAt, touchXPx, touchYPx"),
    Expect("touch-1", "in.touch.1.active", "byte 37 mask 0x80", "P17", "touchAt, touchXPx, touchYPx"),
)

private fun rowsFrom(idle: String, fixture: String) =
    usbInputRows(TestFixtures.report(idle), TestFixtures.report(fixture), 0L)

class UsbInputTraceTest {
    private fun assertRow(e: Expect) {
        val rows = rowsFrom("idle", e.fixture).filter { it.control == e.control }
        assertEquals("${e.fixture} should change ${e.control} exactly once", 1, rows.size)
        val text = rows.single().text
        assertTrue("${e.fixture}: place ${e.place} in: $text", text.contains("report 0x01 ${e.place}  raw"))
        assertTrue("${e.fixture}: FACTS ${e.facts} in: $text", text.contains("FACTS ${e.facts}  "))
        assertTrue("${e.fixture}: reader ${e.reader} in: $text", text.endsWith("read by InputParser.kt ${e.reader}"))
        assertTrue(text.contains("usb"))
    }

    @Test fun everyButtonFixtureNamesItsByteMaskAndFactsRow() {
        for (e in BUTTON_FIXTURES) assertRow(e)
    }

    @Test fun sticksTriggersAndTouchNameTheirByteAndFactsRow() {
        for (e in ANALOG_FIXTURES) assertRow(e)
    }

    @Test fun aButtonRowShowsRawAndDecodedBeforeAndAfter() {
        val text = rowsFrom("idle", "cross").single { it.control == "in.button.cross" }.text
        assertTrue(text, text.contains("raw 0x00 -> 0x20  decoded 0 -> 1"))
    }

    @Test fun theDpadRowNamesTheDirection() {
        val expected = mapOf("dpad-n" to "0 (N)", "dpad-ne" to "1 (NE)", "dpad-e" to "2 (E)", "dpad-se" to "3 (SE)", "dpad-s" to "4 (S)", "dpad-sw" to "5 (SW)", "dpad-w" to "6 (W)", "dpad-nw" to "7 (NW)")
        for ((fixture, direction) in expected) {
            val text = rowsFrom("idle", fixture).single { it.control == "in.dpad" }.text
            assertTrue("$fixture: $text", text.contains("report 0x01 byte 8 mask 0x0f"))
            assertTrue("$fixture: $text", text.contains("decoded 8 (NONE) -> $direction"))
            assertTrue(text.contains("FACTS P13  read by InputParser.kt dpadOf"))
        }
    }

    @Test fun aSixteenBitFieldGivesBothBytesAndASignedValue() {
        val prev = TestFixtures.report("idle").copyOf()
        val cur = prev.copyOf()
        cur[16] = 0x00
        cur[17] = 0x80.toByte() // 0x8000 as int16 little-endian is -32768
        val text = usbInputRows(prev, cur, 0L).single { it.control == "in.gyro.x" }.text
        assertTrue(text, text.contains("bytes 16-17"))
        assertTrue(text, text.contains("decoded") && text.contains("-32768"))
        assertTrue(text.contains("FACTS P16"))
    }

    @Test fun theSequenceCounterIsNotARowAndAnUnchangedReportGivesNone() {
        val a = TestFixtures.report("idle")
        val b = a.copyOf().also { it[7] = (it[7] + 1).toByte() }
        assertEquals(emptyList<TraceRow>(), usbInputRows(a, b, 0L))
        assertEquals(emptyList<TraceRow>(), usbInputRows(a, a.copyOf(), 0L))
    }

    @Test fun everyInputFieldHasAParserFunctionNamed() {
        for (src in INPUT_MAP) assertTrue(src.id, parserReaderOf(src.id).startsWith("InputParser.kt "))
        assertFalse(INPUT_MAP.isEmpty())
    }

    @Test fun anIdTheTableDoesNotCoverIsRefused() {
        val failed = runCatching { parserReaderOf("in.unknown.thing") }.exceptionOrNull()
        assertTrue(failed is IllegalStateException)
    }
}
