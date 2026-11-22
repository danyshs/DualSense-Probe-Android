package dev.dsprobe.app.debug

import dev.dsprobe.app.live.Battery
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
        assertEquals(ReadoutKind.STICK, readoutKindOf(card("in.stick.left")))
        assertEquals(ReadoutKind.TRIGGER, readoutKindOf(card("in.trigger.r2")))
        assertEquals(ReadoutKind.BUTTONS, readoutKindOf(card("in.buttons.face")))
        assertEquals(ReadoutKind.MOTION, readoutKindOf(card("in.gyro")))
        assertEquals(ReadoutKind.MOTION, readoutKindOf(card("in.accel")))
        assertEquals(ReadoutKind.FINGER, readoutKindOf(card("in.touch.1")))
        assertEquals(ReadoutKind.TEXT, readoutKindOf(card("in.battery")))
        val unknown = CARDS.values.first { it.confidence == Confidence.UNKNOWN && it.rawByte >= 0 }
        assertEquals(ReadoutKind.BYTES, readoutKindOf(unknown))
        assertEquals(ReadoutKind.NONE, readoutKindOf(card("out.rumble")))
    }

    // the freeze: the readout came from capped trace rows; it must follow the state every frame
    @Test fun stickReadoutFollowsTwoSuccessiveStates() {
        val t = ReadoutTracker()
        val c = card("in.stick.left")
        assertEquals(-0.067f, (t.read(c, leftX(-0.067f), null) as Readout.Stick).x.value, 0f)
        assertEquals(0.5f, (t.read(c, leftX(0.5f), null) as Readout.Stick).x.value, 0f)
    }

    @Test fun aStickCardReadsBothAxesAndItsOwnClick() {
        val t = ReadoutTracker()
        val state = PadState.EMPTY.copy(
            leftX = 0.25f,
            leftY = -0.5f,
            rightX = 0.75f,
            held = setOf(Button.R3),
        )
        val left = t.read(card("in.stick.left"), state, null) as Readout.Stick
        assertEquals(0.25f, left.x.value, 0f)
        assertEquals(-0.5f, left.y.value, 0f)
        assertEquals(false, left.isClicked)
        val right = t.read(card("in.stick.right"), state, null) as Readout.Stick
        assertEquals(0.75f, right.x.value, 0f)
        assertEquals(true, right.isClicked)
    }

    @Test fun buttonsAndTriggersFollowTwoStates() {
        val t = ReadoutTracker()
        val face = card("in.buttons.face")
        val down = PadState.EMPTY.copy(held = setOf(Button.CROSS))
        val pressed = t.read(face, down, null) as Readout.Buttons
        assertEquals(
            listOf("Square", "Cross", "Circle", "Triangle"),
            pressed.items.map {
                it.label
            },
        )
        assertEquals(listOf(false, true, false, false), pressed.items.map { it.isDown })
        val released = t.read(face, PadState.EMPTY, null) as Readout.Buttons
        assertTrue(released.items.none { it.isDown })
        val r2 = card("in.trigger.r2")
        assertEquals(
            Readout.Trigger(0.25f, false),
            t.read(r2, PadState.EMPTY.copy(r2 = 0.25f), null),
        )
        val full = PadState.EMPTY.copy(r2 = 1f, held = setOf(Button.R2))
        assertEquals(Readout.Trigger(1f, true), t.read(r2, full, null))
    }

    @Test fun minAndMaxTrackThisCardPerAxisAndResetOnTheNext() {
        val t = ReadoutTracker()
        val c = card("in.stick.left")
        t.read(c, leftX(0.2f), null)
        t.read(c, leftX(-0.6f), null)
        val r = t.read(c, leftX(0.4f), null) as Readout.Stick
        assertEquals(-0.6f, r.x.min, 0f)
        assertEquals(0.4f, r.x.max, 0f)
        assertEquals(0f, r.y.min, 0f)
        assertEquals(0f, r.y.max, 0f)
        val other = t.read(card("in.stick.right"), PadState.EMPTY.copy(rightX = 0.1f), null)
        assertEquals(Axis(0.1f, 0.1f, 0.1f), (other as Readout.Stick).x)
    }

    @Test fun motionCardsReadTheirOwnSensor() {
        val t = ReadoutTracker()
        val m = Motion(3f, -2f, 1f, MotionUnit.SI)
        val gyro = PadState.EMPTY.copy(gyro = m)
        val bars = t.read(card("in.gyro"), gyro, null) as Readout.Bars
        assertEquals(3f, bars.scale, 0f)
        assertEquals(-2f, bars.y, 0f)
        assertNull(t.read(card("in.accel"), gyro, null))
        val accel = PadState.EMPTY.copy(accel = Motion(0f, 0f, 9f, MotionUnit.SI))
        assertEquals(9f, (t.read(card("in.accel"), accel, null) as Readout.Bars).z, 0f)
    }

    @Test fun eachFingerCardReadsItsOwnSlot() {
        val t = ReadoutTracker()
        val a = TouchPoint(0, 0.5f, 0.5f, false)
        val b = TouchPoint(1, 0.25f, 0.75f, false)
        val two = PadState.EMPTY.copy(touches = listOf(a, b))
        assertEquals(Readout.Finger(a), t.read(card("in.touch.0"), two, null))
        assertEquals(Readout.Finger(b), t.read(card("in.touch.1"), two, null))
        val one = PadState.EMPTY.copy(touches = listOf(a))
        assertEquals(Readout.Finger(null), t.read(card("in.touch.1"), one, null))
    }

    @Test fun textCardsListEachFieldOnceAndSayNothingWithoutAReading() {
        val t = ReadoutTracker()
        val battery = card("in.battery")
        assertNull(t.read(battery, PadState.EMPTY, null))
        val raw = PadState.EMPTY.copy(battery = Battery.RawNibble(8, 2))
        assertEquals(
            Readout.Lines(listOf("level nibble 8", "charging nibble 2")),
            t.read(battery, raw, null),
        )
        val pct = PadState.EMPTY.copy(battery = Battery.Percent(80))
        assertEquals(Readout.Lines(listOf("80%")), t.read(battery, pct, null))
        val headset = t.read(card("in.headset"), PadState.EMPTY.copy(headphonesIn = true), null)
        assertEquals(Readout.Lines(listOf("plugged in")), headset)
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
        val c = card("in.stick.left")
        val usb = guessText(c, Route.DIRECT_USB)
        val android = guessText(c, Route.ANDROID)
        assertNotEquals(usb, android)
        assertTrue(usb, "255" in usb && "1.0" !in usb)
        assertTrue(android, "+1.0" in android && "255" !in android)
        val r2 = card("in.trigger.r2")
        assertTrue(guessText(r2, Route.ANDROID).contains("1.0 = squeezed"))
        assertEquals(card("in.dpad").guess, guessText(card("in.dpad"), Route.ANDROID))
    }

    @Test fun valueTextUsesTheRoutesUnits() {
        assertEquals("255", axisText(1f, Route.DIRECT_USB))
        assertEquals("+1.000", axisText(1f, Route.ANDROID))
        assertEquals("  0", axisText(-1f, Route.DIRECT_USB))
        assertEquals("128", triggerText(0.5f, Route.DIRECT_USB))
        assertEquals("0.500", triggerText(0.5f, Route.ANDROID))
    }
}
