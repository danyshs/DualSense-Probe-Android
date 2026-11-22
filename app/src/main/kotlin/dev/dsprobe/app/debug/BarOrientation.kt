package dev.dsprobe.app.debug

/** Which way a value's bar runs on a guided card. */
enum class BarOrientation { HORIZONTAL, VERTICAL }

private val AXIS_ORIENTATION: Map<String, BarOrientation> = mapOf(
    "x" to BarOrientation.HORIZONTAL,
    "y" to BarOrientation.VERTICAL,
    "z" to BarOrientation.VERTICAL,
)

private const val TRIGGER_PREFIX = "in.trigger"

/**
 * The bar for a control follows its motion: sideways (x) runs left to right, up and down and
 * depth (y, z) run up to down, a trigger fills upward. [control] is a control ID such as
 * "in.stick.left.y" or "in.accel.z"; one with no axis in it and no trigger fails here.
 */
fun barOrientation(control: String): BarOrientation {
    if (controlUnder(control, TRIGGER_PREFIX)) return BarOrientation.VERTICAL
    val axis = control.split('.').firstNotNullOfOrNull { AXIS_ORIENTATION[it] }
    return checkNotNull(axis) { "no bar axis in $control" }
}
