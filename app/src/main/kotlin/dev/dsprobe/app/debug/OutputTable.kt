package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb

/** One row of the Everything off table. */
enum class OutputKind(val feature: Feature) {
    LIGHTBAR(Feature.LIGHTBAR),
    PLAYER_LEDS(Feature.PLAYER_LEDS),
    MUTE_LED(Feature.MUTE_LED),
    RUMBLE(Feature.RUMBLE),
    TRIGGER_R2(Feature.ADAPTIVE_TRIGGERS),
    TRIGGER_L2(Feature.ADAPTIVE_TRIGGERS),
}

/**
 * [sent] is what was last sent, as digits or a mode word; [isOn] is whether that leaves the
 * output on. A row the route cannot drive has [isDrivable] false.
 */
data class OffRow(
    val kind: OutputKind,
    val sent: String,
    val isOn: Boolean,
    val isDrivable: Boolean,
)

private val BLACK = Rgb(0u, 0u, 0u)

/** The outputs this route can drive now: none without a live route, else all but the locked. */
fun drivableOutputs(canSend: Boolean, locked: List<Reduction>): Set<OutputKind> {
    if (!canSend) return emptySet()
    val lockedFeatures = locked.map { it.feature }.toSet()
    return OutputKind.entries.filter { it.feature !in lockedFeatures }.toSet()
}

private fun rowOf(kind: OutputKind, s: OutputsShown): Pair<String, Boolean> = when (kind) {
    OutputKind.LIGHTBAR -> {
        val c = s.lightbar
        "%02x%02x%02x".format(c.r.toInt(), c.g.toInt(), c.b.toInt()) to (c != BLACK)
    }

    OutputKind.PLAYER_LEDS -> "%02x".format(s.playerLeds) to (s.playerLeds != 0)

    OutputKind.MUTE_LED -> s.muteLed.name.lowercase() to (s.muteLed != MuteLed.OFF)

    OutputKind.RUMBLE ->
        "${s.rumbleLeft} / ${s.rumbleRight}" to (s.rumbleLeft != 0 || s.rumbleRight != 0)

    OutputKind.TRIGGER_R2 -> triggerRow(s.r2.on, s.r2.start, s.r2.force)

    OutputKind.TRIGGER_L2 -> triggerRow(s.l2.on, s.l2.start, s.l2.force)
}

private fun triggerRow(on: Boolean, start: Int, force: Int): Pair<String, Boolean> =
    (if (on) "start $start force $force" else "0") to on

/** One row per output, in [OutputKind] order, from what the controller last sent. */
fun offRows(shown: OutputsShown, drivable: Set<OutputKind>): List<OffRow> =
    OutputKind.entries.map { kind ->
        val (sent, isOn) = rowOf(kind, shown)
        OffRow(kind, sent, isOn, kind in drivable)
    }

/** A drivable row still on after Everything off was pressed. */
fun isFailed(row: OffRow, offPressed: Boolean): Boolean =
    offPressed && row.isDrivable && row.isOn

/**
 * Asks every output in [drivable] to come on: lightbar white, all five player LEDs, mute LED on,
 * both motors moderate (the controller limits the time), both trigger resistances.
 */
fun turnEverythingOn(outputs: OutputController, drivable: Set<OutputKind>) {
    require(drivable.isNotEmpty()) { "nothing to turn on" }
    if (OutputKind.LIGHTBAR in drivable) {
        outputs.setLightbar(Rgb(255u, 255u, 255u))
        outputs.flushLightbar()
    }
    if (OutputKind.PLAYER_LEDS in drivable) outputs.setPlayerLeds(Limits.PLAYER_LED_MASK_MAX)
    if (OutputKind.MUTE_LED in drivable) outputs.setMuteLed(MuteLed.ON)
    if (OutputKind.RUMBLE in drivable) {
        outputs.setRumble(AppLimits.ALL_ON_RUMBLE_INTENSITY, AppLimits.ALL_ON_RUMBLE_INTENSITY)
    }
    if (OutputKind.TRIGGER_R2 in drivable) outputs.pressTrigger(true)
    if (OutputKind.TRIGGER_L2 in drivable) outputs.pressTrigger(false)
}
