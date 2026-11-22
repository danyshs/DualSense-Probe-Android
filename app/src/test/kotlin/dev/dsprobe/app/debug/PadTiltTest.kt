package dev.dsprobe.app.debug

import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.ui.tiltOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val DELTA = 0.05f
private const val G = 9.8f
private const val THIRTY_DEG_RAD = 0.5235988f

private fun si(x: Float, y: Float, z: Float) = Motion(x, y, z, MotionUnit.SI)

class PadTiltTest {
    @Test fun aPadTiltedLeftGivesALeftRollOnTheHomePicture() {
        // left edge down lifts the right edge, so x reads positive; left is counter-clockwise
        val left = checkNotNull(tiltOf(si(3f, 0f, 9f)))
        assertTrue("roll ${left.rollDegrees}", left.rollDegrees < 0f)
        assertTrue(checkNotNull(tiltOf(si(-3f, 0f, 9f))).rollDegrees > 0f)
    }

    @Test fun everyAxisHasOneSignOfOne() {
        for (axis in TiltAxis.entries) assertEquals(axis.name, 1f, abs(tiltSign(axis)), 0f)
    }

    @Test fun aFlatPadIsLevel() {
        val a = attitudeOf(si(0f, 0f, G), 0f)
        assertEquals(0f, a.pitchDeg, DELTA)
        assertEquals(0f, a.rollDeg, DELTA)
    }

    @Test fun pitchAndRollComeFromTheGravityVector() {
        val sideways = attitudeOf(si(G * sin(THIRTY_DEG_RAD), 0f, G * cos(THIRTY_DEG_RAD)), 0f)
        assertEquals(tiltSign(TiltAxis.ROLL) * 30f, sideways.rollDeg, DELTA)
        assertEquals(0f, sideways.pitchDeg, DELTA)
        val forward = attitudeOf(si(0f, G * sin(THIRTY_DEG_RAD), G * cos(THIRTY_DEG_RAD)), 0f)
        assertEquals(tiltSign(TiltAxis.PITCH) * 30f, forward.pitchDeg, DELTA)
        assertEquals(0f, forward.rollDeg, DELTA)
    }

    @Test fun rawCountsAndNoiseLeaveThePadLevelButKeepYaw() {
        val raw = attitudeOf(Motion(900f, 0f, 900f, MotionUnit.RAW_COUNTS), 40f)
        assertEquals(0f, raw.rollDeg, 0f)
        assertEquals(tiltSign(TiltAxis.YAW) * 40f, raw.yawDeg, DELTA)
        assertEquals(0f, attitudeOf(si(0.1f, 0.1f, 0.1f), 0f).rollDeg, 0f)
        assertEquals(0f, attitudeOf(null, 0f).pitchDeg, 0f)
    }

    @Test fun yawFollowsTheIntegratorOnTheAccelerometerCardAndResets() {
        val accel = buildEndpointCards().first { it.id == "in.accel" }
        val quarterTurnPerSecond = Math.PI.toFloat() / 2f
        val state =
            PadState.EMPTY.copy(accel = si(0f, 0f, G), gyro = si(0f, 0f, quarterTurnPerSecond))
        val tracker = ReadoutTracker()
        var bars = tracker.read(accel, state, null, 0L) as Readout.Bars
        for (i in 1..10) bars = tracker.read(accel, state, null, i * 100L) as Readout.Bars
        val yaw = attitudeOf(bars.gravity, checkNotNull(bars.angles).z).yawDeg
        assertEquals(tiltSign(TiltAxis.YAW) * 90f, yaw, 0.5f)
        tracker.resetGyro()
        val after = tracker.read(accel, state, null, 2_000L) as Readout.Bars
        assertEquals(0f, attitudeOf(after.gravity, checkNotNull(after.angles).z).yawDeg, 0f)
    }
}
