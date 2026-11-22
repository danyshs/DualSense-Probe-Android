package dev.dsprobe.app.debug

import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val CARDS = buildEndpointCards().associateBy { it.id }

private fun card(id: String): EndpointCard = checkNotNull(CARDS[id]) { id }

private fun leftX(v: Float): PadState = PadState.EMPTY.copy(leftX = v)

class LiveReadoutTest {
    @Test fun kindFollowsTheCardType() {
        assertEquals(ReadoutKind.AXIS, readoutKindOf(card("in.stick.left.x")))
        assertEquals(ReadoutKind.TRIGGER, readoutKindOf(card("in.trigger.r2")))
        assertEquals(ReadoutKind.BUTTON, readoutKindOf(card("in.button.cross")))
        assertEquals(ReadoutKind.MOTION, readoutKindOf(card("in.gyro.x")))
        assertEquals(ReadoutKind.MOTION, readoutKindOf(card("in.accel.z")))
        assertEquals(ReadoutKind.TOUCH, readoutKindOf(card("in.touch.0.x.low8")))
        val unknown = CARDS.values.first { it.confidence == Confidence.UNKNOWN && it.rawByte >= 0 }
        assertEquals(ReadoutKind.BYTES, readoutKindOf(unknown))
        assertEquals(ReadoutKind.NONE, readoutKindOf(card("out.rumble")))
    }

    // the freeze: the readout came from capped trace rows; it must follow the state every frame
    @Test fun axisReadoutFollowsTwoSuccessiveStates() {
        val t = ReadoutTracker()
        val c = card("in.stick.left.x")
        assertEquals(-0.067f, (t.read(c, leftX(-0.067f), null) as Readout.Axis).value, 0f)
        assertEquals(0.5f, (t.read(c, leftX(0.5f), null) as Readout.Axis).value, 0f)
    }

    @Test fun buttonAndTriggerFollowTwoStates() {
        val t = ReadoutTracker()
        val b = card("in.button.cross")
        val down = PadState.EMPTY.copy(held = setOf(Button.CROSS))
        assertEquals(Readout.Held(true), t.read(b, down, null))
        assertEquals(Readout.Held(false), t.read(b, PadState.EMPTY, null))
        val r2 = card("in.trigger.r2")
        assertEquals(Readout.Trigger(0.25f), t.read(r2, PadState.EMPTY.copy(r2 = 0.25f), null))
        assertEquals(Readout.Trigger(1f), t.read(r2, PadState.EMPTY.copy(r2 = 1f), null))
    }

    @Test fun minAndMaxTrackThisCardAndResetOnTheNext() {
        val t = ReadoutTracker()
        val c = card("in.stick.left.x")
        t.read(c, leftX(0.2f), null)
        t.read(c, leftX(-0.6f), null)
        val r = t.read(c, leftX(0.4f), null) as Readout.Axis
        assertEquals(-0.6f, r.min, 0f)
        assertEquals(0.4f, r.max, 0f)
        val other = t.read(card("in.stick.right.x"), PadState.EMPTY.copy(rightX = 0.1f), null)
        assertEquals(Readout.Axis(0.1f, 0.1f, 0.1f), other)
    }

    @Test fun motionAndTouchReadFromTheState() {
        val t = ReadoutTracker()
        val m = Motion(3f, -2f, 1f, MotionUnit.SI)
        val bars = t.read(card("in.gyro.x"), PadState.EMPTY.copy(gyro = m), null) as Readout.Bars
        assertEquals(3f, bars.scale, 0f)
        assertNull(t.read(card("in.gyro.x"), PadState.EMPTY, null))
        val finger = TouchPoint(0, 0.5f, 0.5f, false)
        val touch = t.read(
            card("in.touch.0.x.low8"),
            PadState.EMPTY.copy(touches = listOf(finger)),
            null,
        )
        assertEquals(Readout.Touch(listOf(finger)), touch)
    }

    @Test fun unknownRangeShowsBytesAndCountsChanges() {
        val t = ReadoutTracker()
        val c = CARDS.values.first { it.confidence == Confidence.UNKNOWN && it.rawByte >= 0 }
        val a = ByteArray(64)
        val b = ByteArray(64)
        b[c.rawByte] = c.rawMask.toByte()
        assertEquals(0, (t.read(c, PadState.EMPTY, a) as Readout.Bytes).changes)
        val after = t.read(c, PadState.EMPTY, b) as Readout.Bytes
        assertEquals(1, after.changes)
        assertNotEquals(rawBytes(c, a).toHex(), after.hex)
        assertEquals(1, (t.read(c, PadState.EMPTY, b) as Readout.Bytes).changes)
        assertNull(t.read(c, PadState.EMPTY, null))
    }

    @Test fun stickGuessUnitsDifferByRoute() {
        val c = card("in.stick.left.x")
        val usb = guessText(c, Route.DIRECT_USB)
        val android = guessText(c, Route.ANDROID)
        assertNotEquals(usb, android)
        assertTrue(usb, "255" in usb && "1.0" !in usb)
        assertTrue(android, "+1.0" in android && "255" !in android)
        val r2 = card("in.trigger.r2")
        assertTrue(guessText(r2, Route.ANDROID).contains("1.0 = squeezed"))
    }

    @Test fun valueTextUsesTheRoutesUnits() {
        assertEquals("255", axisText(1f, Route.DIRECT_USB))
        assertEquals("+1.000", axisText(1f, Route.ANDROID))
        assertEquals("  0", axisText(-1f, Route.DIRECT_USB))
        assertEquals("128", triggerText(0.5f, Route.DIRECT_USB))
        assertEquals("0.500", triggerText(0.5f, Route.ANDROID))
    }
}
