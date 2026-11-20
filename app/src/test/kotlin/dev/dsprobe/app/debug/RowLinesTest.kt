package dev.dsprobe.app.debug

import android.view.InputDevice
import android.view.KeyEvent
import dev.dsprobe.app.TestFixtures
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.keyTraceEvent
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowLinesTest {
    @Test fun aDirectUsbInputIsTwoLinesAndTheSecondNamesBytePartsAndTheParser() {
        val rows = usbInputRows(TestFixtures.report("idle"), TestFixtures.report("cross"), 5L)
        val lines = rowLines(rows.single { it.control == "in.button.cross" })
        assertEquals("Cross  pressed", lines.headline)
        assertTrue(lines.location, lines.location.contains("report 0x01") && lines.location.contains("byte 8 mask 0x20"))
        assertTrue(lines.location, lines.location.contains("FACTS P13") && lines.location.contains("heldButtons"))
        assertFalse(lines.copyText().lines().size != 2)
    }

    @Test fun aDirectUsbOutputCarriesTheChangedBytesFlagsAndResult() {
        val report = ByteArray(Limits.USB_OUTPUT_REPORT_BYTES)
        report[0] = 0x02
        report[2] = 0x04
        report[45] = 0xff.toByte()
        val send = UsbSend(report, setOf(OutputField.LIGHTBAR), "Lightbar(255,0,0)", ok = true, isFinal = false, isResend = false)
        val row = UsbOutputTrace().row(send, 9L)!!
        val lines = rowLines(row)
        assertTrue(lines.headline, lines.headline.startsWith("out.lightbar"))
        assertTrue(lines.location, lines.location.contains("45: 00 -> ff") && lines.location.contains("P43") && lines.location.contains("sent ok"))
    }

    @Test fun anAndroidKeyNamesTheHookTheConstantTheScanCodeAndTheDevice() {
        val e: FeedEvent = keyTraceEvent(1L, 7, true, KeyEvent.KEYCODE_BUTTON_A, "KEYCODE_BUTTON_A", 304, InputDevice.SOURCE_GAMEPAD, PadState.EMPTY)
        val row = TraceModel().let { m -> m.ingest(listOf(e)).single() }
        val lines = rowLines(row)
        assertEquals("Cross  pressed", lines.headline)
        assertEquals("dispatchKeyEvent · KEYCODE_BUTTON_A=96 · scan 304 · device 7", lines.location)
        assertEquals(Route.ANDROID, row.route)
    }

    @Test fun aRowWithoutLinesFallsBackToItsControlAndTheDetailAndIsScrubbed() {
        val row = TraceRow(0L, Route.ANDROID, "in.key.x", traceLine("in.key.x", Route.ANDROID, "hook h aa:bb:cc:dd:ee:ff end"))
        val lines = rowLines(row)
        assertEquals("in.key.x", lines.headline)
        assertEquals("hook h $BT_ADDRESS_MASK end", lines.location)
    }
}
