package dev.dsprobe.app.ui

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.Frame
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun frame(state: PadState = PadState.EMPTY, pressed: Set<Button> = emptySet(), dpad: Set<Dpad> = emptySet()) =
    Frame(state, pressed, dpad)

class FeelTest {
    @Test fun glowIsFullWhileHeldThenFadesToNothing() {
        assertEquals(1f, glowAlpha(true, null, 1_000), 0f)
        assertEquals(0f, glowAlpha(false, null, 1_000), 0f)
        assertEquals(0.5f, glowAlpha(false, 1_000, 1_000 + AppLimits.PRESS_GLOW_FADE_MS / 2), 0.001f)
        assertEquals(0f, glowAlpha(false, 1_000, 1_000 + AppLimits.PRESS_GLOW_FADE_MS * 2), 0f)
    }

    @Test fun aTapShorterThanAFrameStillGlowsFromTheLatch() {
        val f = advance(FeelFrame.EMPTY, frame(pressed = setOf(Button.CIRCLE)), 500)
        assertEquals(500L, f.buttonActiveMs[Button.CIRCLE])
        assertTrue(glowAlpha(false, f.buttonActiveMs[Button.CIRCLE], 510) > 0.5f)
    }

    @Test fun aReleasedButtonKeepsItsLastActiveMoment() {
        val held = advance(FeelFrame.EMPTY, frame(PadState.EMPTY.copy(held = setOf(Button.L1))), 100)
        val released = advance(held, frame(), 200)
        assertEquals(100L, released.buttonActiveMs[Button.L1])
    }

    @Test fun aDiagonalLightsBothArms() {
        assertEquals(setOf(Dpad.N, Dpad.E), armsOf(Dpad.NE))
        val f = advance(FeelFrame.EMPTY, frame(PadState.EMPTY.copy(dpad = Dpad.SW)), 50)
        assertEquals(setOf(Dpad.S, Dpad.W), f.armActiveMs.keys)
        assertEquals(emptySet<Dpad>(), armsOf(Dpad.NONE))
    }

    @Test fun peakHoldsThenDropsToTheValue() {
        val up = nextPeak(Peak(0f, 0), 1f, 100)
        assertEquals(Peak(1f, 100), up)
        assertEquals(up, nextPeak(up, 0f, 100 + AppLimits.TRIGGER_PEAK_HOLD_MS))
        assertEquals(Peak(0f, 100 + AppLimits.TRIGGER_PEAK_HOLD_MS + 1), nextPeak(up, 0f, 100 + AppLimits.TRIGGER_PEAK_HOLD_MS + 1))
    }

    @Test fun trailsFadeOutAndStayBounded() {
        var f = FeelFrame.EMPTY
        for (i in 0 until 200) f = advance(f, frame(PadState.EMPTY.copy(leftX = 0.8f)), i * 2L)
        assertTrue(f.leftTrail.size <= AppLimits.STICK_TRAIL_POINTS_MAX)
        val idle = advance(f, frame(), 400 + AppLimits.STICK_TRAIL_MS + 1)
        assertEquals(emptyList<TrailPoint>(), idle.leftTrail)
    }

    @Test fun aClickRippleStartsAtTheFingerAndExpires() {
        val finger = PadState.EMPTY.copy(touches = listOf(TouchPoint(0, 0.2f, 0.4f, isPointer = false)))
        val f = advance(FeelFrame.EMPTY, frame(finger, pressed = setOf(Button.TOUCHPAD_CLICK)), 1_000)
        assertEquals(listOf(Ripple(0.2f, 0.4f, 1_000)), f.ripples)
        val later = advance(f, frame(finger), 1_000 + AppLimits.CLICK_RIPPLE_MS + 1)
        assertEquals(emptyList<Ripple>(), later.ripples)
    }

    @Test fun motionHistoryIsBounded() {
        val m = Motion(1f, 2f, 3f, MotionUnit.SI)
        var f = FeelFrame.EMPTY
        for (i in 0 until AppLimits.MOTION_TRACE_SAMPLES_MAX + 20) f = advance(f, frame(PadState.EMPTY.copy(gyro = m)), i.toLong())
        assertEquals(AppLimits.MOTION_TRACE_SAMPLES_MAX, f.gyroHistory.size)
        assertEquals(3f, plotScale(f.gyroHistory, 1f), 0f)
        assertEquals(1f, plotScale(emptyList(), 1f), 0f)
    }

    @Test fun tiltFollowsGravityInSiAndIsOffForRawCounts() {
        val flat = tiltOf(Motion(0f, 0f, 9.8f, MotionUnit.SI))!!
        assertEquals(0f, flat.rollDegrees, 0.001f)
        val sideways = tiltOf(Motion(9.8f, 0f, 0f, MotionUnit.SI))!!
        assertEquals(AppLimits.ACCEL_TILT_DEGREES_MAX, sideways.rollDegrees, 0.001f)
        assertNull(tiltOf(Motion(1000f, 0f, 0f, MotionUnit.RAW_COUNTS)))
        assertNull(tiltOf(null))
        assertNull(tiltOf(Motion(0f, 0f, 0.01f, MotionUnit.SI)))
    }
}
