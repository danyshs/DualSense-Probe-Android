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
enum class ReadoutKind { STICK, TRIGGER, BUTTONS, MOTION, FINGER, TOUCH, TEXT, BYTES, NONE }

private val TEXT_IDS = setOf("in.dpad", "in.sequence", "in.battery", "in.headset")
private val MOTION_IDS = setOf("in.gyro", "in.accel", "android.sensors")

fun readoutKindOf(card: EndpointCard): ReadoutKind {
    val id = card.id
    return when {
        card.rawByte >= 0 -> ReadoutKind.BYTES
        id.startsWith("in.stick.") -> ReadoutKind.STICK
        id.startsWith("in.trigger.") -> ReadoutKind.TRIGGER
        id.startsWith("in.buttons.") || id == "android.keys" -> ReadoutKind.BUTTONS
        id in MOTION_IDS -> ReadoutKind.MOTION
        id.startsWith("in.touch.") -> ReadoutKind.FINGER
        id == "android.touchpad" -> ReadoutKind.TOUCH
        id in TEXT_IDS -> ReadoutKind.TEXT
        else -> ReadoutKind.NONE
    }
}

/** One axis's value now and the lowest and highest seen on this card. */
data class Axis(val value: Float, val min: Float, val max: Float)

/** One button of a group and whether it is down. */
data class ButtonState(val label: String, val isDown: Boolean)

/** The live values a card shows, read from the current pad state, never from trace rows. */
sealed interface Readout {
    /** A stick's two axes and its click. */
    data class Stick(val x: Axis, val y: Axis, val isClicked: Boolean) : Readout

    /** A trigger's travel and its click flag. */
    data class Trigger(val value: Float, val isClicked: Boolean) : Readout

    /** The buttons of a group; Android's key card lists only the held ones. */
    data class Buttons(val items: List<ButtonState>) : Readout

    /** [scale] is the largest magnitude seen on the card, never below its floor. */
    data class Bars(
        val x: Float,
        val y: Float,
        val z: Float,
        val scale: Float,
        val isRawCounts: Boolean,
        /** The gyroscope needles since the card opened; null on the accelerometer. */
        val angles: Angles? = null,
        val isStill: Boolean = false,
    ) : Readout

    /** One finger slot; null when no finger is in it. */
    data class Finger(val touch: TouchPoint?) : Readout

    /** Android's touchpad pointer: every touch the state has. */
    data class Touch(val touches: List<TouchPoint>) : Readout

    data class Lines(val lines: List<String>) : Readout

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

private fun sticksOf(id: String, s: PadState): Pair<Float, Float>? = when (id) {
    "in.stick.left" -> s.leftX to s.leftY
    "in.stick.right" -> s.rightX to s.rightY
    else -> null
}

private fun stickClick(id: String): Button = if (id == "in.stick.left") Button.L3 else Button.R3

private fun buttonOf(fieldId: String): Button? {
    val name = fieldId.removePrefix("in.button.").uppercase()
    return Button.entries.firstOrNull { it.name == name }
}

private fun motionOf(id: String, s: PadState): Motion? = if (id == "in.accel") s.accel else s.gyro

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

private fun lineOf(id: String, s: PadState): String? = when {
    id == "in.dpad" -> s.dpad.name
    id == "in.sequence" -> s.sequence?.toString()
    id == "in.headphones_in" -> s.headphonesIn?.let { if (it) "plugged in" else "not plugged in" }
    id.startsWith("in.battery.") -> batteryText(id, s.battery)
    else -> null
}

/** The finger slot a card ("in.touch.0", "in.touch.1") shows. */
private fun slotOf(card: EndpointCard): Int = card.id.substringAfterLast('.').toInt()

/**
 * Per open card: turns the current pad state (and the newest report, for byte ranges) into the
 * card's [Readout], and keeps what "seen on this card" means: min and max per axis, motion scale,
 * byte changes. Starts over when the card changes. Main thread only.
 */
class ReadoutTracker {
    private var openId: String? = null
    private val ranges = HashMap<String, FloatArray>()
    private var peak = 0f
    private var lastHex: String? = null
    private var changes = 0
    private val gyro = GyroIntegrator()

    /** Zeroes the gyroscope needles; they start from here again. */
    fun resetGyro() = gyro.reset()

    /** [nowMs] is the frame time; only the gyroscope needles use it. */
    fun read(card: EndpointCard, state: PadState, report: ByteArray?, nowMs: Long = 0L): Readout? {
        if (card.id != openId) forget(card.id)
        return when (readoutKindOf(card)) {
            ReadoutKind.STICK -> stick(card.id, state)
            ReadoutKind.TRIGGER -> trigger(card.id, state)
            ReadoutKind.BUTTONS -> buttons(card, state)
            ReadoutKind.MOTION -> motionOf(card.id, state)?.let { bars(card.id, it, nowMs) }
            ReadoutKind.FINGER -> Readout.Finger(state.touches.getOrNull(slotOf(card)))
            ReadoutKind.TOUCH -> Readout.Touch(state.touches)
            ReadoutKind.TEXT -> lines(card, state)
            ReadoutKind.BYTES -> bytes(card, report)
            ReadoutKind.NONE -> null
        }
    }

    private fun forget(id: String) {
        openId = id
        ranges.clear()
        peak = 0f
        lastHex = null
        changes = 0
        gyro.reset()
    }

    private fun stick(id: String, s: PadState): Readout.Stick? {
        val (x, y) = sticksOf(id, s) ?: return null
        return Readout.Stick(axis("x", x), axis("y", y), stickClick(id) in s.held)
    }

    private fun axis(key: String, v: Float): Axis {
        val seen = ranges.getOrPut(key) { floatArrayOf(v, v) }
        seen[0] = min(seen[0], v)
        seen[1] = max(seen[1], v)
        return Axis(v, seen[0], seen[1])
    }

    private fun trigger(id: String, s: PadState): Readout.Trigger? = when (id) {
        "in.trigger.l2" -> Readout.Trigger(s.l2, Button.L2 in s.held)
        "in.trigger.r2" -> Readout.Trigger(s.r2, Button.R2 in s.held)
        else -> null
    }

    private fun buttons(card: EndpointCard, s: PadState): Readout.Buttons {
        if (card.id == "android.keys") {
            return Readout.Buttons(
                s.held.sortedBy {
                    it.ordinal
                }.map { ButtonState(it.name, true) },
            )
        }
        val items = card.fields.mapNotNull { field ->
            buttonOf(field.id)?.let { ButtonState(field.label, it in s.held) }
        }
        return Readout.Buttons(items)
    }

    private fun lines(card: EndpointCard, s: PadState): Readout.Lines? {
        val lines = card.fields.mapNotNull { lineOf(it.id, s) }.distinct()
        return if (lines.isEmpty()) null else Readout.Lines(lines)
    }

    private fun bars(id: String, m: Motion, nowMs: Long): Readout.Bars {
        val isRaw = m.unit == MotionUnit.RAW_COUNTS
        val floor = if (isRaw) {
            AppLimits.MOTION_RAW_SCALE_FLOOR_COUNTS
        } else {
            AppLimits.MOTION_SI_SCALE_FLOOR
        }
        peak = max(peak, max(abs(m.x), max(abs(m.y), abs(m.z))))
        val angles = if (id == "in.accel") null else gyro.step(m, nowMs)
        return Readout.Bars(m.x, m.y, m.z, max(peak, floor), isRaw, angles, isStill(m))
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
