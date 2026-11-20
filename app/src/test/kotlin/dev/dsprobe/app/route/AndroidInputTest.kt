package dev.dsprobe.app.route

import android.view.KeyEvent
import dev.dsprobe.app.live.Battery
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val NEUTRAL = AxisReading(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

class AndroidInputTest {
    @Test fun everyPadKeyCodeMapsToItsOwnButton() {
        val expected = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to Button.CROSS, KeyEvent.KEYCODE_BUTTON_B to Button.CIRCLE,
            KeyEvent.KEYCODE_BUTTON_X to Button.SQUARE, KeyEvent.KEYCODE_BUTTON_Y to Button.TRIANGLE,
            KeyEvent.KEYCODE_BUTTON_L1 to Button.L1, KeyEvent.KEYCODE_BUTTON_R1 to Button.R1,
            KeyEvent.KEYCODE_BUTTON_L2 to Button.L2, KeyEvent.KEYCODE_BUTTON_R2 to Button.R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL to Button.L3, KeyEvent.KEYCODE_BUTTON_THUMBR to Button.R3,
            KeyEvent.KEYCODE_BUTTON_SELECT to Button.CREATE, KeyEvent.KEYCODE_BUTTON_START to Button.OPTIONS,
            KeyEvent.KEYCODE_BUTTON_MODE to Button.PS,
        )
        for ((code, button) in expected) {
            val pressed = PadState.EMPTY.withKey(code, down = true)
            assertEquals("code $code", setOf(button), pressed.held)
            assertEquals(emptySet<Button>(), pressed.withKey(code, down = false).held)
        }
        // Mute and the touchpad click have no key, so no key code can ever set them
        assertEquals(Button.entries.size - 2, expected.values.toSet().size)
    }

    @Test fun anUnknownKeyChangesNothing() {
        assertNull(buttonForKeyCode(KeyEvent.KEYCODE_A))
        assertEquals(PadState.EMPTY, PadState.EMPTY.withKey(KeyEvent.KEYCODE_A, down = true))
    }

    @Test fun twoButtonsHeldAtOnceBothStay() {
        val s = PadState.EMPTY.withKey(KeyEvent.KEYCODE_BUTTON_A, true).withKey(KeyEvent.KEYCODE_BUTTON_B, true)
            .withKey(KeyEvent.KEYCODE_BUTTON_A, false)
        assertEquals(setOf(Button.CIRCLE), s.held)
    }

    @Test fun axesLandOnTheRightFields() {
        val s = PadState.EMPTY.withAxes(AxisReading(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0f, 0f))
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f), listOf(s.leftX, s.leftY, s.rightX, s.rightY, s.l2, s.r2))
    }

    @Test fun badAxisValuesAreCleanedNotThrown() {
        val s = PadState.EMPTY.withAxes(AxisReading(Float.NaN, 5f, -5f, Float.POSITIVE_INFINITY, -1f, 2f, 0f, 0f))
        assertEquals(listOf(0f, 1f, -1f, 1f, 0f, 1f), listOf(s.leftX, s.leftY, s.rightX, s.rightY, s.l2, s.r2))
    }

    @Test fun hatMakesAllNineDpadStates() {
        val expected = mapOf(
            (0f to -1f) to Dpad.N, (1f to -1f) to Dpad.NE, (1f to 0f) to Dpad.E, (1f to 1f) to Dpad.SE,
            (0f to 1f) to Dpad.S, (-1f to 1f) to Dpad.SW, (-1f to 0f) to Dpad.W, (-1f to -1f) to Dpad.NW,
            (0f to 0f) to Dpad.NONE,
        )
        for ((hat, dpad) in expected) assertEquals("hat $hat", dpad, PadState.EMPTY.withAxes(NEUTRAL.copy(hatX = hat.first, hatY = hat.second)).dpad)
        assertEquals(Dpad.NONE, dpadFromHat(Float.NaN, 0.3f))
    }

    @Test fun pointerSetsTheDotAndThePrimaryButtonIsTheClick() {
        val down = PadState.EMPTY.withPointer(0.25f, 0.75f, primaryDown = true)
        assertEquals(setOf(Button.TOUCHPAD_CLICK), down.held)
        val dot = down.touches.single()
        assertEquals(listOf(0.25f, 0.75f, 0f), listOf(dot.x, dot.y, dot.id.toFloat()))
        assertTrue(dot.isPointer)
        assertEquals(emptySet<Button>(), down.withPointer(0.3f, 0.7f, primaryDown = false).held)
    }

    @Test fun pointerOutsideTheWindowIsClamped() {
        val dot = PadState.EMPTY.withPointer(-3f, Float.NaN, false).touches.single()
        assertEquals(listOf(0f, 0f), listOf(dot.x, dot.y))
    }

    @Test fun sensorsAreSiAndKeepTheirOwnSlot() {
        val s = PadState.EMPTY.withGyro(1f, 2f, 3f).withAccel(4f, 5f, 6f)
        val gyro = s.gyro!!
        val accel = s.accel!!
        assertEquals(listOf(1f, 2f, 3f), listOf(gyro.x, gyro.y, gyro.z))
        assertEquals(listOf(4f, 5f, 6f), listOf(accel.x, accel.y, accel.z))
        assertEquals(MotionUnit.SI, gyro.unit)
    }

    @Test fun batteryReadsPercentAndShowsAnythingElseAsAndroidsRawAnswer() {
        assertEquals(Battery.Percent(85), batteryFrom(true, 0.85f, 3))
        assertEquals(Battery.Percent(100), batteryFrom(true, 1.7f, 3))
        assertEquals(Battery.AndroidRaw(-0.01f, 1), batteryFrom(true, -0.01f, 1)) // what the USB path reported
        assertEquals(Battery.AndroidRaw(0.5f, 1), batteryFrom(false, 0.5f, 1))
        val nan = batteryFrom(true, Float.NaN, 1) as Battery.AndroidRaw
        assertTrue(nan.capacity.isNaN())
    }

    @Test fun rumbleGoesToTheIdsAndroidReportsLowerIdIsLeft() {
        assertEquals(listOf(0 to 200, 1 to 50), rumblePlan(listOf(0, 1), 200, 50))
        assertEquals(listOf(4 to 200, 9 to 50), rumblePlan(listOf(9, 4), 200, 50))
        assertEquals(listOf(1 to 50), rumblePlan(listOf(0, 1), 0, 50))
        assertEquals(emptyList<Pair<Int, Int>>(), rumblePlan(listOf(0, 1), 0, 0))
    }

    @Test fun oneVibratorGetsTheStrongerMotorAndNoneGetsNothing() {
        assertEquals(listOf(7 to 90), rumblePlan(listOf(7), 90, 10))
        assertEquals(emptyList<Pair<Int, Int>>(), rumblePlan(emptyList(), 255, 255))
        assertEquals(2, rumblePlan(listOf(0, 1, 2), 1, 1).size)
    }

    @Test fun aStrengthPast255IsRefused() {
        assertThrows(IllegalArgumentException::class.java) { rumblePlan(listOf(0, 1), 256, 0) }
        assertThrows(IllegalArgumentException::class.java) { rumblePlan(listOf(0, 1), 0, -1) }
    }
}
