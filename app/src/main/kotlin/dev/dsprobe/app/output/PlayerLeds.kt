package dev.dsprobe.app.output

import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.Limits

/** The five dots as the raw mask: LED 1 is bit 0, LED 5 is bit 4 (0x10). */
fun ledsToMask(lit: List<Boolean>): Int {
    require(lit.size == AppLimits.PLAYER_LED_COUNT) { "led dots ${lit.size}" }
    var mask = 0
    for (i in lit.indices) if (lit[i]) mask = mask or (1 shl i)
    check(mask in 0..Limits.PLAYER_LED_MASK_MAX) { "led mask $mask" }
    return mask
}

/** The mask as five dots, LED 1 first. */
fun maskToLeds(mask: Int): List<Boolean> {
    require(mask in 0..Limits.PLAYER_LED_MASK_MAX) { "led mask $mask" }
    return List(AppLimits.PLAYER_LED_COUNT) { i -> mask and (1 shl i) != 0 }
}

/** The mask with dot [index] (0 = LED 1) flipped. */
fun toggleLed(mask: Int, index: Int): Int {
    require(index in 0 until AppLimits.PLAYER_LED_COUNT) { "led index $index" }
    return mask xor (1 shl index)
}

/** The raw byte for the location line only: binary, hex, and which LEDs it lights ("0b00010000 = 0x10, LED 5"). */
fun ledMaskText(mask: Int): String {
    require(mask in 0..Limits.PLAYER_LED_MASK_MAX) { "led mask $mask" }
    val lit = maskToLeds(mask).withIndex().filter { it.value }.map { it.index + 1 }
    val which = when (lit.size) {
        0 -> "all off"
        1 -> "LED " + lit[0]
        else -> "LEDs " + lit.joinToString(", ")
    }
    return "0b" + Integer.toBinaryString(mask).padStart(Byte.SIZE_BITS, '0') + " = 0x%02x, ".format(mask) + which
}
