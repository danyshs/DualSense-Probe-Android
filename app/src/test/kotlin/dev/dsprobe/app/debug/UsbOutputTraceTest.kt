package dev.dsprobe.app.debug

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.pad.OutputField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val NOT_VALUE_BYTES = setOf(0, 1, 2, 39) // the report id and the three valid-flag bytes

private fun send(report: ByteArray, fields: Set<OutputField>, ok: Boolean = true, isResend: Boolean = false, isFinal: Boolean = false) =
    UsbSend(report, fields, "test request", ok, isFinal, isResend)

class UsbOutputTraceTest {
    @Test fun eachGoldenListsExactlyItsNonZeroValueBytesAndNothingElse() {
        val names = TestFixtures.names("golden")
        assertTrue("golden fixtures found: ${names.size}", names.size >= 30)
        for (name in names) {
            val report = TestFixtures.bytes("golden/$name.hex")
            val expected = report.indices.filter { it !in NOT_VALUE_BYTES && report[it].toInt() != 0 }
            val changes = UsbOutputTrace().diff(report)
            assertEquals(name, expected, changes.map { it.offset })
            for (c in changes) {
                assertEquals(name, 0, c.old)
                assertEquals(name, report[c.offset].toInt() and 0xff, c.new)
            }
            assertEquals("$name has a valid-flag bit no group names", emptyList<String>(), unknownFlagBits(report))
        }
    }

    @Test fun lightbarRedChangesOnlyByteFortyFiveAndSaysWhere() {
        val trace = UsbOutputTrace()
        val row = checkNotNull(trace.row(send(TestFixtures.bytes("golden/lightbar-ff0000.hex"), setOf(OutputField.LIGHTBAR)), 0L))
        assertEquals("out.lightbar", row.control)
        val text = row.text
        assertTrue(text, text.contains("bytes 45: 00 -> ff"))
        assertTrue(text, !text.contains("46:") && !text.contains("47:"))
        assertTrue(text, text.contains("byte 2 (flag1) bit 0x04 lightbar (45 red, 46 green, 47 blue) [P43]"))
        assertTrue(text, text.contains("fields LIGHTBAR") && text.contains("63 bytes sent ok") && text.contains("request test request"))
    }

    @Test fun theNextColourShowsOldToNewFromWhatThePadWasLastTold() {
        val trace = UsbOutputTrace()
        trace.row(send(TestFixtures.bytes("golden/lightbar-ff0000.hex"), setOf(OutputField.LIGHTBAR)), 0L)
        val text = checkNotNull(trace.row(send(TestFixtures.bytes("golden/lightbar-00ff00.hex"), setOf(OutputField.LIGHTBAR)), 1L)).text
        assertTrue(text, text.contains("bytes 45: ff -> 00, 46: 00 -> ff"))
    }

    @Test fun aResendThatChangesNothingIsNotARowButARealChangeAlwaysIs() {
        val trace = UsbOutputTrace()
        val red = TestFixtures.bytes("golden/lightbar-ff0000.hex")
        assertNotNull(trace.row(send(red, setOf(OutputField.LIGHTBAR)), 0L))
        assertNull(trace.row(send(red, setOf(OutputField.LIGHTBAR), isResend = true), 1L))
        val firstResend = UsbOutputTrace().row(send(red, setOf(OutputField.LIGHTBAR), isResend = true), 0L)
        assertNotNull("a resend that does change a byte still shows", firstResend)
    }

    @Test fun aReportThePadDidNotTakeLeavesTheShadowAndSaysSo() {
        val trace = UsbOutputTrace()
        val red = TestFixtures.bytes("golden/lightbar-ff0000.hex")
        val failed = checkNotNull(trace.row(send(red, setOf(OutputField.LIGHTBAR), ok = false), 0L))
        assertTrue(failed.text, failed.text.contains("NOT taken by bulkTransfer"))
        val again = checkNotNull(trace.row(send(red, setOf(OutputField.LIGHTBAR)), 1L))
        assertTrue("the failed send did not count as sent", again.text.contains("45: 00 -> ff"))
    }

    @Test fun rumbleAndTriggerGoldensNameTheirFlagBitsAndFactsRows() {
        val rumble = checkNotNull(UsbOutputTrace().row(send(TestFixtures.bytes("golden/rumble-both.hex"), setOf(OutputField.RUMBLE)), 0L))
        assertEquals("out.rumble", rumble.control)
        assertTrue(rumble.text, rumble.text.contains("byte 1 (flag0) bit 0x02 motors (3 right, 4 left) [P31]"))
        val trigger = checkNotNull(UsbOutputTrace().row(send(TestFixtures.bytes("golden/r2-resistance.hex"), setOf(OutputField.RIGHT_TRIGGER)), 0L))
        assertEquals("out.trigger.r2", trigger.control)
        assertTrue(trigger.text, trigger.text.contains("bytes 11: 00 -> 01, 12: 00 -> 30, 13: 00 -> c0"))
        assertTrue(trigger.text, trigger.text.contains("[P39]"))
    }

    @Test fun theAllOffReportIsTheReleaseRowAndListsNoValueByteFromAZeroShadow() {
        val row = checkNotNull(UsbOutputTrace().row(send(TestFixtures.allOffGolden, setOf(OutputField.LIGHTBAR), isFinal = true), 0L))
        assertEquals("out.release", row.control)
        assertTrue(row.text, row.text.contains("no value byte changed"))
    }

    @Test fun aFlagBitNoGroupNamesIsShownAsUnknownNeverHidden() {
        val report = TestFixtures.bytes("golden/lightbar-ff0000.hex").copyOf()
        report[2] = (report[2].toInt() or 0x08).toByte()
        assertEquals(1, unknownFlagBits(report).size)
        val text = checkNotNull(UsbOutputTrace().row(send(report, setOf(OutputField.LIGHTBAR)), 0L)).text
        assertTrue(text, text.contains("UNKNOWN flag1 byte 2 bits 0x08"))
    }

    @Test fun controlIdsFollowTheFields() {
        assertEquals("out.led.player", controlOfFields(setOf(OutputField.PLAYER_LEDS), false))
        assertEquals("out.led.mute", controlOfFields(setOf(OutputField.MUTE_LED), false))
        assertEquals("out.multi", controlOfFields(setOf(OutputField.LIGHTBAR, OutputField.PLAYER_LEDS), false))
        assertEquals("out.release", controlOfFields(setOf(OutputField.LIGHTBAR), true))
    }

    @Test fun aWrongSizedReportIsRefused() {
        val failed = runCatching { UsbOutputTrace().diff(ByteArray(10)) }.exceptionOrNull()
        assertTrue(failed is IllegalArgumentException)
    }
}
