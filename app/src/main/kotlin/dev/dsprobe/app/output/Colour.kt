package dev.dsprobe.app.output

import dev.dsprobe.pad.Rgb
import kotlin.math.abs
import kotlin.math.roundToInt

const val HUE_DEGREES_MAX = 360f
private const val CHANNEL_MAX = 255
private const val HEX_DIGITS = 6
private const val CHANNEL_DIGITS_MAX = 3
private const val DEGREES_PER_SECTOR = 60f
private const val SECTORS = 6
private const val SECTOR_LAST = SECTORS - 1
private const val HEX_RADIX = 16
private const val BITS_PER_CHANNEL = 8
private const val BITS_PER_HEX_DIGIT = 4
private const val HEX_DIGIT_MASK = 0xf

// hue sectors, 60 degrees each, named by the channel that peaks there
private const val SECTOR_GREEN_TOP = 2f
private const val SECTOR_BLUE_TOP = 4f
private const val SECTOR_CYAN = 3
private const val SECTOR_BLUE = 4

private fun channel(v: Float): UByte =
    (v * CHANNEL_MAX).roundToInt().coerceIn(0, CHANNEL_MAX).toUByte()

fun hsvToRgb(hsv: Hsv): Rgb {
    val chroma = hsv.value * hsv.sat
    val sector = hsv.hue / DEGREES_PER_SECTOR
    val second = chroma * (1f - abs(sector % 2f - 1f))
    val base = hsv.value - chroma
    val (r, g, b) = when (sector.toInt().coerceAtMost(SECTOR_LAST)) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        SECTOR_CYAN -> Triple(0f, second, chroma)
        SECTOR_BLUE -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    return Rgb(channel(r + base), channel(g + base), channel(b + base))
}

fun rgbToHsv(rgb: Rgb): Hsv {
    val r = rgb.r.toInt() / CHANNEL_MAX.toFloat()
    val g = rgb.g.toInt() / CHANNEL_MAX.toFloat()
    val b = rgb.b.toInt() / CHANNEL_MAX.toFloat()
    val top = maxOf(r, g, b)
    val spread = top - minOf(r, g, b)
    val sector = when {
        spread == 0f -> 0f
        top == r -> ((g - b) / spread + SECTORS) % SECTORS
        top == g -> (b - r) / spread + SECTOR_GREEN_TOP
        else -> (r - g) / spread + SECTOR_BLUE_TOP
    }
    return Hsv(
        (sector * DEGREES_PER_SECTOR).coerceIn(0f, HUE_DEGREES_MAX),
        if (top == 0f) 0f else spread / top,
        top,
    )
}

private const val HEX_CHARS = "0123456789abcdef"

/**
 * Six lowercase hex digits, no prefix: the format of the hex field and of the golden file names.
 */
fun Rgb.toHex(): String {
    val packed =
        (r.toInt() shl (2 * BITS_PER_CHANNEL)) or (g.toInt() shl BITS_PER_CHANNEL) or b.toInt()
    val out = CharArray(HEX_DIGITS)
    for (i in 0 until HEX_DIGITS) {
        val shift = BITS_PER_HEX_DIGIT * i
        out[HEX_DIGITS - 1 - i] = HEX_CHARS[(packed shr shift) and HEX_DIGIT_MASK]
    }
    return String(out)
}

private fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

/**
 * Six hex digits, with or without a leading '#'; anything else is null (the field keeps what was
 * typed and sends nothing).
 */
fun parseHex(text: String): Rgb? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != HEX_DIGITS || !digits.all(::isHexDigit)) return null
    val packed = digits.toInt(HEX_RADIX)
    return Rgb(
        ((packed shr (2 * BITS_PER_CHANNEL)) and CHANNEL_MAX).toUByte(),
        ((packed shr BITS_PER_CHANNEL) and CHANNEL_MAX).toUByte(),
        (packed and CHANNEL_MAX).toUByte(),
    )
}

/** One of the R, G, B fields: 0..255 in plain digits, else null. */
fun parseChannel(text: String): UByte? {
    val digits = text.trim()
    if (digits.isEmpty() || digits.length > CHANNEL_DIGITS_MAX || !digits.all { it in '0'..'9' }) {
        return null
    }
    return digits.toInt().takeIf { it <= CHANNEL_MAX }?.toUByte()
}
