package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.PadState
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import kotlin.math.abs

private const val PRESSED_RATIO_MIN = 0.5f
private const val STICK_HALF_RANGE_COUNTS = AppLimits.COUNTS_PER_BYTE_MAX / 2f

/** Stick edge: within 16 counts of the end of 0..255, as a share of the -1..1 range (0.875). */
const val STICK_EDGE_UNITS = 1f - AppLimits.STICK_EDGE_TOLERANCE_COUNTS / STICK_HALF_RANGE_COUNTS

/** Trigger reached: 247 or more counts of 255 (0.97). */
const val TRIGGER_REACHED_UNITS =
    AppLimits.TRIGGER_REACHED_COUNTS_MIN / AppLimits.COUNTS_PER_BYTE_MAX

private fun buttonId(b: Button) = "in.button.${b.name.lowercase()}"

/**
 * Every control's current value, the same scale on both routes: buttons 0 or 1, sticks -1..1,
 * triggers 0..1, D-pad as its index, touch contacts 0 or 1.
 */
fun samplesOf(s: PadState): Map<String, Float> {
    val out = HashMap<String, Float>()
    out["in.stick.left.x"] = s.leftX
    out["in.stick.left.y"] = s.leftY
    out["in.stick.right.x"] = s.rightX
    out["in.stick.right.y"] = s.rightY
    out["in.trigger.l2"] = s.l2
    out["in.trigger.r2"] = s.r2
    out["in.dpad"] = s.dpad.ordinal.toFloat()
    for (b in Button.entries) out[buttonId(b)] = if (b in s.held) 1f else 0f
    out["in.touch.0"] = if (s.touches.isNotEmpty()) 1f else 0f
    out["in.touch.1"] = if (s.touches.size >= 2) 1f else 0f
    s.gyro?.let {
        out["in.gyro.x"] = it.x
        out["in.gyro.y"] = it.y
        out["in.gyro.z"] = it.z
    }
    s.accel?.let {
        out["in.accel.x"] = it.x
        out["in.accel.y"] = it.y
        out["in.accel.z"] = it.z
    }
    return out
}

private fun isMotion(control: String) =
    control.startsWith("in.gyro") || control.startsWith("in.accel")

/**
 * How far a control may move before it counts as touched: 8 counts of 255, on the scale the sample
 * uses.
 */
fun noiseUnits(control: String): Float = when {
    control.startsWith("in.stick.") ->
        AppLimits.CROSS_TALK_THRESHOLD_COUNTS /
            STICK_HALF_RANGE_COUNTS

    control.startsWith("in.trigger.") ->
        AppLimits.CROSS_TALK_THRESHOLD_COUNTS /
            AppLimits.COUNTS_PER_BYTE_MAX

    else -> PRESSED_RATIO_MIN
}

/** The control a test's ID reads: "in.dpad.ne" reads "in.dpad", a motion test reads its sensor. */
fun controlOfTest(id: InputId): String = if (id.id.startsWith("in.dpad.")) "in.dpad" else id.id

/**
 * Judges one input test sample by sample. A button passes on its press, a D-pad on its direction,
 * a stick when both ends were reached, a trigger when it reached the top, a touch on its contact,
 * and a motion sensor when its values varied.
 */
class InputMatcher(private val id: InputId) {
    private var sawLow = false
    private var sawHigh = false
    private val distinct = HashSet<Float>()

    fun accept(samples: Map<String, Float>): Boolean {
        val value = samples[controlOfTest(id)]
        return when {
            id.id.startsWith("in.dpad.") ->
                value == Dpad.valueOf(id.id.removePrefix("in.dpad.").uppercase()).ordinal.toFloat()

            isMotion(id.id) -> varies(samples)

            // every other test reads its own control; no sample is no match
            value == null -> false

            id.id.startsWith("in.stick.") -> sticksBothEnds(value)

            id.id.startsWith("in.trigger.") -> value >= TRIGGER_REACHED_UNITS

            else -> value >= PRESSED_RATIO_MIN
        }
    }

    private fun sticksBothEnds(v: Float): Boolean {
        if (v <= -STICK_EDGE_UNITS) sawLow = true
        if (v >= STICK_EDGE_UNITS) sawHigh = true
        return sawLow && sawHigh
    }

    private fun varies(samples: Map<String, Float>): Boolean {
        for ((control, v) in samples) {
            if (!control.startsWith(id.id)) continue
            // the set stops growing once the answer is known, so it never outgrows the minimum
            if (distinct.size < AppLimits.MOTION_DISTINCT_VALUES_MIN) distinct.add(v)
        }
        return distinct.size >= AppLimits.MOTION_DISTINCT_VALUES_MIN
    }
}

/**
 * The first control, other than the target and its companions, that moved past its noise threshold
 * since [baseline]; null when none did. Motion sensors are always ignored: a pressed button tips
 * the pad.
 */
fun crossTalkCulprit(
    baseline: Map<String, Float>,
    now: Map<String, Float>,
    target: InputId,
    companions: Set<InputId>,
): String? {
    val targetControl = controlOfTest(target)
    fun isOwn(control: String): Boolean =
        isMotion(control) || control == targetControl || control.startsWith(target.id) ||
            companions.any { control.startsWith(it.id) }
    return now.entries.firstOrNull { (control, value) ->
        val before = baseline[control]
        before != null && !isOwn(control) && isPress(control, before, value)
    }?.key
}

/** [control] moved from [before] to [value] by more than noise, and not as a release. */
private fun isPress(control: String, before: Float, value: Float): Boolean {
    val moved = abs(value - before) > noiseUnits(control)
    // a release of something already held is not a press
    val isHoldable = control.startsWith("in.button.") || control.startsWith("in.touch.")
    val isDpadRelease = control == "in.dpad" && value == Dpad.NONE.ordinal.toFloat()
    val isRelease = (isHoldable && value < before) || isDpadRelease
    return moved && !isRelease
}
