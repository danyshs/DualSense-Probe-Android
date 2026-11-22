package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.Battery
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.toHex
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Which live readout a card gets; NONE for cards with nothing the pad reports live. */
enum class ReadoutKind { AXIS, TRIGGER, BUTTON, MOTION, TOUCH, TEXT, BYTES, NONE }

private val TEXT_IDS = setOf(
    "in.dpad",
    "in.sequence",
    "in.battery.level",
    "in.battery.charging",
    "in.headphones_in",
)

fun readoutKindOf(card: EndpointCard): ReadoutKind {
    val id = card.id
    return when {
        card.rawByte >= 0 -> ReadoutKind.BYTES

        id.startsWith("in.stick.") -> ReadoutKind.AXIS

        id.startsWith("in.trigger.") -> ReadoutKind.TRIGGER

        id.startsWith("in.button.") || id == "android.keys" -> ReadoutKind.BUTTON

        id.startsWith("in.gyro.") || id.startsWith("in.accel.") || id == "android.sensors" ->
            ReadoutKind.MOTION

        id.startsWith("in.touch.") || id == "android.touchpad" -> ReadoutKind.TOUCH

        id in TEXT_IDS -> ReadoutKind.TEXT

        else -> ReadoutKind.NONE
    }
}

/** The one value a card shows live, read from the current pad state, never from trace rows. */
sealed interface Readout {
    data class Axis(val value: Float, val min: Float, val max: Float) : Readout

    data class Trigger(val value: Float) : Readout

    data class Held(val isDown: Boolean) : Readout

    /** [scale] is the largest magnitude seen on the card, never below its floor. */
    data class Bars(
        val x: Float,
        val y: Float,
        val z: Float,
        val scale: Float,
        val isRawCounts: Boolean,
    ) : Readout

    data class Touch(val touches: List<TouchPoint>) : Readout

    data class Words(val text: String) : Readout

    data class Bytes(val hex: String, val changes: Int) : Readout
}

/** Stick or trigger value as the live route words it: Direct USB 0..255, Android -1..1 or 0..1. */
fun axisText(value: Float, route: Route): String = if (route == Route.DIRECT_USB) {
    String.format(
        Locale.ROOT,
        "%3d",
        ((value + 1f) / 2f * AppLimits.COUNTS_PER_BYTE_MAX).roundToInt(),
    )
} else {
    String.format(Locale.ROOT, "%+.3f", value)
}

fun triggerText(value: Float, route: Route): String = if (route == Route.DIRECT_USB) {
    String.format(Locale.ROOT, "%3d", (value * AppLimits.COUNTS_PER_BYTE_MAX).roundToInt())
} else {
    String.format(Locale.ROOT, "%.3f", value)
}

private fun axisOf(id: String, s: PadState): Float? = when (id) {
    "in.stick.left.x" -> s.leftX
    "in.stick.left.y" -> s.leftY
    "in.stick.right.x" -> s.rightX
    "in.stick.right.y" -> s.rightY
    else -> null
}

private fun triggerOf(id: String, s: PadState): Float? = when (id) {
    "in.trigger.l2" -> s.l2
    "in.trigger.r2" -> s.r2
    else -> null
}

private fun heldOf(id: String, s: PadState): Boolean? {
    if (id == "android.keys") return s.held.isNotEmpty()
    val name = id.removePrefix("in.button.").uppercase()
    val button = Button.entries.firstOrNull { it.name == name } ?: return null
    return button in s.held
}

private fun motionOf(id: String, s: PadState): Motion? =
    if (id.startsWith("in.accel.")) s.accel else s.gyro

private fun batteryText(id: String, b: Battery?): String? = when (b) {
    null -> null

    is Battery.Percent -> "${b.percent}%"

    is Battery.RawNibble -> if (id.endsWith("charging")) {
        "charging nibble ${b.chargingNibble}"
    } else {
        "level nibble ${b.levelNibble}"
    }

    is Battery.AndroidRaw -> "capacity ${b.capacity}, status ${b.status}"
}

private fun wordsOf(id: String, s: PadState): String? = when (id) {
    "in.dpad" -> s.dpad.name
    "in.sequence" -> s.sequence?.toString()
    "in.headphones_in" -> s.headphonesIn?.let { if (it) "plugged in" else "not plugged in" }
    else -> batteryText(id, s.battery)
}

/**
 * Per open card: turns the current pad state (and the newest report, for byte ranges) into the
 * card's [Readout], and keeps what "seen on this card" means: min and max, motion scale, byte
 * changes. Starts over when the card changes. Main thread only.
 */
class ReadoutTracker {
    private var openId: String? = null
    private var low = 0f
    private var high = 0f
    private var isRanged = false
    private var peak = 0f
    private var lastHex: String? = null
    private var changes = 0

    fun read(card: EndpointCard, state: PadState, report: ByteArray?): Readout? {
        if (card.id != openId) forget(card.id)
        return when (readoutKindOf(card)) {
            ReadoutKind.AXIS -> axisOf(card.id, state)?.let(::axis)
            ReadoutKind.TRIGGER -> triggerOf(card.id, state)?.let { Readout.Trigger(it) }
            ReadoutKind.BUTTON -> heldOf(card.id, state)?.let { Readout.Held(it) }
            ReadoutKind.MOTION -> motionOf(card.id, state)?.let(::bars)
            ReadoutKind.TOUCH -> Readout.Touch(state.touches)
            ReadoutKind.TEXT -> wordsOf(card.id, state)?.let { Readout.Words(it) }
            ReadoutKind.BYTES -> bytes(card, report)
            ReadoutKind.NONE -> null
        }
    }

    private fun forget(id: String) {
        openId = id
        low = 0f
        high = 0f
        isRanged = false
        peak = 0f
        lastHex = null
        changes = 0
    }

    private fun axis(v: Float): Readout.Axis {
        low = if (isRanged) min(low, v) else v
        high = if (isRanged) max(high, v) else v
        isRanged = true
        return Readout.Axis(v, low, high)
    }

    private fun bars(m: Motion): Readout.Bars {
        val isRaw = m.unit == MotionUnit.RAW_COUNTS
        val floor = if (isRaw) {
            AppLimits.MOTION_RAW_SCALE_FLOOR_COUNTS
        } else {
            AppLimits.MOTION_SI_SCALE_FLOOR
        }
        peak = max(peak, max(abs(m.x), max(abs(m.y), abs(m.z))))
        return Readout.Bars(m.x, m.y, m.z, max(peak, floor), isRaw)
    }

    private fun bytes(card: EndpointCard, report: ByteArray?): Readout.Bytes? {
        if (report == null || card.rawByte + card.rawByteCount > report.size) return null
        val hex = rawBytes(card, report).toHex()
        val before = lastHex
        if (before != null && before != hex) changes += 1
        lastHex = hex
        return Readout.Bytes(hex, changes)
    }
}
