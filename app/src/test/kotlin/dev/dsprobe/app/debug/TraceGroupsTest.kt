package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun row(control: String) =
    TraceRow(0L, Route.DIRECT_USB, control, traceLine(control, Route.DIRECT_USB, "x"))

private val SAMPLE_CONTROLS = listOf(
    "in.button.cross" to "buttons", "in.dpad" to "dpad", "in.stick.left.x" to "sticks",
    "in.trigger.l2" to "triggers",
    "in.touch.0.x.low8" to "touchpad", "in.button.touchpad_click" to "touchpad",
    "in.gyro.x" to "motion", "in.accel" to "motion",
    "in.battery.level" to "battery", "in.headphones_in" to "battery",
    "in.key.unmapped.91" to "android_keys",
    "out.lightbar" to "lightbar", "out.led.player" to "player_leds", "out.led.mute" to "mute_led",
    "out.rumble" to "rumble", "out.trigger.r2" to "trigger_effects",
)

class TraceGroupsTest {
    @Test fun everyControlLandsInItsOwnGroup() {
        for ((control, group) in SAMPLE_CONTROLS) assertEquals(control, group, groupOf(control)?.id)
    }

    @Test fun motionStartsUncheckedAndEveryOtherGroupChecked() {
        val checked = defaultCheckedGroups()
        assertFalse(GROUP_MOTION in checked)
        assertEquals(TRACE_GROUPS.size - 1, checked.size)
    }

    @Test fun theFilterKeepsExactlyTheCheckedGroups() {
        val rows = SAMPLE_CONTROLS.map { row(it.first) }
        val kept = filterByGroups(rows, setOf("buttons", "rumble")).map { it.control }
        assertEquals(listOf("in.button.cross", "out.rumble"), kept)
        assertEquals(emptyList<TraceRow>(), filterByGroups(rows, emptySet()))
        assertEquals(rows, filterByGroups(rows, TRACE_GROUPS.map { it.id }.toSet()))
    }

    @Test fun theDefaultSelectionHidesMotionRows() {
        val rows = listOf(row("in.gyro.x"), row("in.button.cross"), row("in.accel.z"))
        assertEquals(
            listOf("in.button.cross"),
            filterByGroups(rows, defaultCheckedGroups()).map {
                it.control
            },
        )
    }

    @Test fun aRowNoGroupNamesIsNeverHidden() {
        assertTrue(filterByGroups(listOf(row("out.release")), emptySet()).isNotEmpty())
    }
}
