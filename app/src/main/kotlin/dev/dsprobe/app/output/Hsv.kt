package dev.dsprobe.app.output

/** Hue in degrees 0..360, saturation and value 0..1: the picker's square and strip. */
data class Hsv(val hue: Float, val sat: Float, val value: Float) {
    init {
        require(hue in 0f..HUE_DEGREES_MAX && sat in 0f..1f && value in 0f..1f) {
            "hsv $hue $sat $value"
        }
    }
}
