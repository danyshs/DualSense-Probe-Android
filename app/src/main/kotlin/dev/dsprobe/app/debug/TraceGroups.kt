package dev.dsprobe.app.debug

import androidx.annotation.StringRes
import dev.dsprobe.app.R

/** One checkbox of the trace checklist: a row belongs to it when its control is one of [prefixes] or sits under one. */
data class TraceGroup(val id: String, @param:StringRes val labelRes: Int, val prefixes: List<String>)

const val GROUP_MOTION = "motion"

// the touchpad comes before buttons so its click is a touchpad row
val TRACE_GROUPS: List<TraceGroup> = listOf(
    TraceGroup("touchpad", R.string.group_touchpad, listOf("in.touch", "in.button.touchpad_click")),
    TraceGroup("buttons", R.string.group_buttons, listOf("in.button")),
    TraceGroup("dpad", R.string.group_dpad, listOf("in.dpad")),
    TraceGroup("sticks", R.string.group_sticks, listOf("in.stick")),
    TraceGroup("triggers", R.string.group_triggers, listOf("in.trigger")),
    TraceGroup(GROUP_MOTION, R.string.group_motion, listOf("in.gyro", "in.accel")),
    TraceGroup("battery", R.string.group_battery, listOf("in.battery", "in.headphones_in", "in.mic_in")),
    TraceGroup("android_keys", R.string.group_android_keys, listOf("in.key")),
    TraceGroup("lightbar", R.string.group_lightbar, listOf("out.lightbar")),
    TraceGroup("player_leds", R.string.group_player_leds, listOf("out.led.player")),
    TraceGroup("mute_led", R.string.group_mute_led, listOf("out.led.mute")),
    TraceGroup("rumble", R.string.group_rumble, listOf("out.rumble")),
    TraceGroup("trigger_effects", R.string.group_trigger_effects, listOf("out.trigger")),
)

/** Everything checked except Motion, which changes every report. */
fun defaultCheckedGroups(): Set<String> = TRACE_GROUPS.map { it.id }.toSet() - GROUP_MOTION

/** True when [control] is [prefix] or sits under it. */
fun controlUnder(control: String, prefix: String): Boolean = control == prefix || control.startsWith("$prefix.")

fun groupOf(control: String): TraceGroup? = TRACE_GROUPS.firstOrNull { g -> g.prefixes.any { controlUnder(control, it) } }

/** The rows of the checked groups. A row no group names (the all-off send, a combined send) always shows, so nothing is hidden unnamed. */
fun filterByGroups(rows: List<TraceRow>, checked: Set<String>): List<TraceRow> =
    rows.filter { row -> groupOf(row.control)?.id?.let { it in checked } ?: true }
