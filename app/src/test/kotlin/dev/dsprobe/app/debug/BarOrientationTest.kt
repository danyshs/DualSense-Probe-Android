package dev.dsprobe.app.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BarOrientationTest {
    private fun horizontal(vararg ids: String) {
        for (id in ids) assertEquals(id, BarOrientation.HORIZONTAL, barOrientation(id))
    }

    private fun vertical(vararg ids: String) {
        for (id in ids) assertEquals(id, BarOrientation.VERTICAL, barOrientation(id))
    }

    @Test fun aSticksSidewaysAxisRunsLeftRightAndItsUpDownAxisRunsUpDown() {
        horizontal("in.stick.left.x", "in.stick.right.x")
        vertical("in.stick.left.y", "in.stick.right.y")
    }

    @Test fun motionXIsHorizontalAndYAndZAreVertical() {
        horizontal("in.gyro.x", "in.accel.x")
        vertical("in.gyro.y", "in.gyro.z", "in.accel.y", "in.accel.z")
    }

    @Test fun touchXIsHorizontalAndTouchYIsVertical() {
        horizontal("in.touch.0.x.low8", "in.touch.1.x.high4")
        vertical("in.touch.0.y.high8", "in.touch.1.y.low4")
    }

    @Test fun triggersFillVertically() {
        vertical("in.trigger.l2", "in.trigger.r2", "in.trigger")
    }

    @Test fun aControlWithNoAxisIsRefused() {
        try {
            barOrientation("in.button.cross")
            fail("a button has no bar")
        } catch (expected: IllegalStateException) {
            assertEquals("no bar axis in in.button.cross", expected.message)
        }
    }
}
