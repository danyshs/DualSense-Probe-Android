package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun counts(n: Int) = n / 255f
private fun stick(counts: Int) = counts / 127.5f - 1f

class InputMatchTest {
    @Test fun theThresholdsAreTheDesignsCountsOnTheScaleTheSamplesUse() {
        assertEquals(0.875f, STICK_EDGE_UNITS, 0.001f)
        assertEquals(0.969f, TRIGGER_REACHED_UNITS, 0.001f)
        assertEquals(0.063f, noiseUnits("in.stick.left.x"), 0.001f)
        assertEquals(8 / 255f, noiseUnits("in.trigger.l2"), 0.0001f)
    }

    @Test fun aTriggerPassesAt247CountsAndNotAt246() {
        assertFalse(InputMatcher(InputId("in.trigger.l2")).accept(mapOf("in.trigger.l2" to counts(246))))
        assertTrue(InputMatcher(InputId("in.trigger.l2")).accept(mapOf("in.trigger.l2" to counts(247))))
    }

    @Test fun aStickNeedsBothEndsWithin16CountsAndTheOrderDoesNotMatter() {
        val m = InputMatcher(InputId("in.stick.right.y"))
        assertFalse(m.accept(mapOf("in.stick.right.y" to stick(255))))
        assertFalse(m.accept(mapOf("in.stick.right.y" to stick(100))))
        assertTrue(m.accept(mapOf("in.stick.right.y" to stick(16))))
        val short = InputMatcher(InputId("in.stick.right.y"))
        short.accept(mapOf("in.stick.right.y" to stick(239)))
        assertFalse("17 counts from the low end is short", short.accept(mapOf("in.stick.right.y" to stick(17))))
    }

    @Test fun aMotionSensorPassesOnlyWhenItsValuesVaried() {
        val m = InputMatcher(InputId("in.gyro"))
        for (i in 0 until AppLimits.MOTION_DISTINCT_VALUES_MIN - 1) assertFalse(m.accept(mapOf("in.gyro.x" to i.toFloat())))
        assertFalse("the accelerometer does not count for the gyroscope", m.accept(mapOf("in.accel.x" to 99f)))
        assertTrue(m.accept(mapOf("in.gyro.x" to 99f)))
    }

    @Test fun aTouchPassesOnItsContact() {
        assertFalse(InputMatcher(InputId("in.touch.1")).accept(mapOf("in.touch.0" to 1f, "in.touch.1" to 0f)))
        assertTrue(InputMatcher(InputId("in.touch.1")).accept(mapOf("in.touch.1" to 1f)))
    }

    @Test fun aMissingControlNeverMatches() {
        assertFalse(InputMatcher(InputId("in.button.cross")).accept(emptyMap()))
        assertFalse(InputMatcher(InputId("in.trigger.r2")).accept(emptyMap()))
        assertFalse(InputMatcher(InputId("in.stick.left.x")).accept(emptyMap()))
    }

    @Test fun crossTalkNeedsMoreThanTheNoiseThresholdAndIgnoresTheTargetAndItsCompanions() {
        val rest = mapOf("in.stick.left.x" to 0f, "in.button.cross" to 0f, "in.trigger.l2" to 0f, "in.button.l2" to 0f)
        val target = InputId("in.button.l2")
        val companions = setOf(InputId("in.trigger.l2"))
        assertNull(crossTalkCulprit(rest, rest + ("in.stick.left.x" to 0.06f), target, companions))
        assertEquals("in.stick.left.x", crossTalkCulprit(rest, rest + ("in.stick.left.x" to 0.07f), target, companions))
        assertNull(crossTalkCulprit(rest, rest + ("in.trigger.l2" to 1f), target, companions))
        assertNull(crossTalkCulprit(rest, rest + ("in.button.l2" to 1f), target, companions))
        assertEquals("in.button.cross", crossTalkCulprit(rest, rest + ("in.button.cross" to 1f), target, companions))
    }

    @Test fun releasingSomethingAlreadyHeldAndMotionSensorsAreNeverCrossTalk() {
        val held = mapOf("in.button.circle" to 1f, "in.dpad" to 2f, "in.touch.0" to 1f, "in.gyro.x" to 0f)
        val after = mapOf("in.button.circle" to 0f, "in.dpad" to 8f, "in.touch.0" to 0f, "in.gyro.x" to 5000f)
        assertNull(crossTalkCulprit(held, after, InputId("in.button.cross"), emptySet()))
    }

    @Test fun aDpadPressWhileTestingAButtonIsCrossTalk() {
        val rest = mapOf("in.dpad" to 8f)
        assertEquals("in.dpad", crossTalkCulprit(rest, mapOf("in.dpad" to 2f), InputId("in.button.cross"), emptySet()))
        assertNull("the dpad is the target of its own test", crossTalkCulprit(rest, mapOf("in.dpad" to 2f), InputId("in.dpad.e"), emptySet()))
    }
}
