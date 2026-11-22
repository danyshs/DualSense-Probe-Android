package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DELTA = 0.01f

private fun raw(x: Float, y: Float, z: Float) = Motion(x, y, z, MotionUnit.RAW_COUNTS)

private fun si(x: Float, y: Float, z: Float) = Motion(x, y, z, MotionUnit.SI)

class MotionVisualsTest {
    @Test fun aConstantRateTimesTimeIsTheAngle() {
        var angle = 0f
        repeat(10) { angle = integrateDegrees(angle, 90f, 100L) }
        assertEquals(90f, angle, DELTA)
        assertEquals(-9f, integrateDegrees(0f, -90f, 100L), DELTA)
    }

    @Test fun anAngleWrapsIntoMinus180ExclusiveToPlus180Inclusive() {
        assertEquals(-170f, wrapDegrees(190f), DELTA)
        assertEquals(170f, wrapDegrees(-190f), DELTA)
        assertEquals(180f, wrapDegrees(180f), DELTA)
        assertEquals(180f, wrapDegrees(-180f), DELTA)
        assertEquals(180f, wrapDegrees(540f), DELTA)
        assertEquals(-170f, integrateDegrees(170f, 200f, 100L), DELTA)
    }

    @Test fun aLongFrameGapAndAHugeRateAreCutToTheirLimits() {
        val gap = integrateDegrees(0f, 100f, AppLimits.GYRO_STEP_MS_MAX * 50)
        assertEquals(100f * AppLimits.GYRO_STEP_MS_MAX / 1_000f, gap, DELTA)
        val fast = integrateDegrees(0f, 1e9f, 100L)
        // 2000 deg/s for 0.1 s is 200 degrees, wrapped
        assertEquals(-160f, fast, DELTA)
    }

    @Test fun theIntegratorTurnsRadPerSecondIntoDegreesAndResetStartsOver() {
        val g = GyroIntegrator()
        val quarterTurnPerSecond = si((Math.PI / 2).toFloat(), 0f, 0f)
        assertEquals(Angles.ZERO, g.step(quarterTurnPerSecond, 1_000L))
        for (i in 1..10) g.step(quarterTurnPerSecond, 1_000L + i * 100L)
        assertEquals(90f, g.angles.x, 0.1f)
        assertEquals(0f, g.angles.y, 0f)
        g.reset()
        assertEquals(Angles.ZERO, g.angles)
        // the first step after a reset has no earlier frame to measure from
        assertEquals(Angles.ZERO, g.step(quarterTurnPerSecond, 50_000L))
    }

    @Test fun rawCountsGoThroughTheUntestedScale() {
        val g = GyroIntegrator()
        g.step(raw(1_000f, 0f, 0f), 0L)
        g.step(raw(1_000f, 0f, 0f), 100L)
        assertEquals(1_000f * AppLimits.GYRO_RAW_DEG_PER_S_PER_COUNT * 0.1f, g.angles.x, DELTA)
    }

    @Test fun theTrackerKeepsNeedlesPerCardAndResets() {
        val card = buildEndpointCards().first { it.id == "in.gyro" }
        val state = PadState.EMPTY.copy(gyro = si(1f, 0f, 0f))
        val t = ReadoutTracker()
        t.read(card, state, null, 0L)
        val after = t.read(card, state, null, 100L) as Readout.Bars
        assertTrue(checkNotNull(after.angles).x > 0f)
        t.resetGyro()
        val reset = t.read(card, state, null, 200L) as Readout.Bars
        assertEquals(Angles.ZERO, reset.angles)
        val accel = buildEndpointCards().first { it.id == "in.accel" }
        val bars = t.read(accel, PadState.EMPTY.copy(accel = si(0f, 0f, 9.8f)), null, 300L)
        assertNull((bars as Readout.Bars).angles)
    }

    @Test fun aFlatPadPutsTheDotInTheCentreAndTheTotalAtRest() {
        val b = bubbleOf(si(0f, 0f, 9.8f))
        assertEquals(0f, b.dx, DELTA)
        assertEquals(0f, b.dy, DELTA)
        assertEquals(AppLimits.ACCEL_REST_M_PER_S2, b.total, DELTA)
    }

    @Test fun aPadOnItsEdgeRunsTheDotToThatEdge() {
        val b = bubbleOf(si(9.8f, 0f, 0f))
        assertEquals(1f, b.dx, DELTA)
        assertEquals(0f, b.dy, DELTA)
        val tilted = bubbleOf(raw(300f, -400f, 0f))
        assertEquals(0.6f, tilted.dx, DELTA)
        assertEquals(-0.8f, tilted.dy, DELTA)
        assertEquals(500f, tilted.total, DELTA)
        assertEquals(Bubble(0f, 0f, 0f), bubbleOf(si(0f, 0f, 0f)))
    }

    @Test fun theStillLightFollowsThePerUnitThreshold() {
        val rad = AppLimits.GYRO_STILL_RAD_PER_S
        assertTrue(isStill(si(0f, 0f, 0f)))
        assertTrue(isStill(si(rad * 0.9f, -rad * 0.9f, 0f)))
        assertFalse(isStill(si(rad, 0f, 0f)))
        assertFalse(isStill(si(0f, rad, 0f)))
        assertFalse(isStill(si(0f, 0f, -rad * 2f)))
        val counts = AppLimits.GYRO_STILL_RAW_COUNTS
        assertTrue(isStill(raw(counts * 0.9f, 0f, 0f)))
        assertFalse(isStill(raw(0f, 0f, counts)))
        // a raw reading that is still would be a large Android one: the unit decides
        assertFalse(isStill(si(counts * 0.9f, 0f, 0f)))
    }
}
