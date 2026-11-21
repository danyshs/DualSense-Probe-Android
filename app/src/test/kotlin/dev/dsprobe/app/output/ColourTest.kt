package dev.dsprobe.app.output

import dev.dsprobe.pad.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private val EDGES = listOf(0, 1, 2, 15, 16, 127, 128, 129, 239, 240, 254, 255)

private fun rgb(r: Int, g: Int, b: Int) = Rgb(r.toUByte(), g.toUByte(), b.toUByte())

private fun near(a: Rgb, b: Rgb, by: Int) =
    abs(a.r.toInt() - b.r.toInt()) <= by && abs(a.g.toInt() - b.g.toInt()) <= by &&
        abs(a.b.toInt() - b.b.toInt()) <= by

class ColourTest {
    @Test fun everyOneOfTheSixteenMillionColoursRoundTripsThroughTheHexField() {
        var count = 0
        for (packed in 0..0xffffff) {
            val c = rgb(packed shr 16, (packed shr 8) and 0xff, packed and 0xff)
            val hex = c.toHex()
            check(hex.length == 6)
            if (parseHex(hex) != c) error("hex round trip broke at $hex")
            count += 1
        }
        assertEquals(16_777_216, count)
    }

    @Test fun hexWritesLowercaseAndPaddedAndReadsEitherCaseWithOrWithoutAHash() {
        assertEquals("000000", rgb(0, 0, 0).toHex())
        assertEquals("0000ff", rgb(0, 0, 255).toHex())
        assertEquals("010a0f", rgb(1, 10, 15).toHex())
        assertEquals(rgb(255, 0, 170), parseHex("#FF00aa"))
        assertEquals(rgb(255, 0, 170), parseHex(" ff00AA "))
    }

    @Test fun badHexIsRefusedNotGuessed() {
        val refused = listOf(
            "", "fff", "fffffff", "ff00g0", "+12345", "-12345", "#", "##ffffff", "0x1234", "ff 000",
        )
        for (bad in refused) {
            assertNull("[$bad]", parseHex(bad))
        }
    }

    @Test fun channelsTakeZeroTo255InPlainDigitsOnly() {
        for (v in 0..255) assertEquals(v, parseChannel(v.toString())!!.toInt())
        assertEquals(7, parseChannel("007")!!.toInt())
        for (bad in listOf("", "256", "999", "1000", "-1", "+1", "1.5", "ab", " ", "0x10")) {
            assertNull(
                "[$bad]",
                parseChannel(bad),
            )
        }
    }

    @Test fun hsvCornersAreTheNamedColours() {
        assertEquals(rgb(255, 0, 0), hsvToRgb(Hsv(0f, 1f, 1f)))
        assertEquals(rgb(255, 0, 0), hsvToRgb(Hsv(360f, 1f, 1f)))
        assertEquals(rgb(255, 255, 0), hsvToRgb(Hsv(60f, 1f, 1f)))
        assertEquals(rgb(0, 255, 0), hsvToRgb(Hsv(120f, 1f, 1f)))
        assertEquals(rgb(0, 255, 255), hsvToRgb(Hsv(180f, 1f, 1f)))
        assertEquals(rgb(0, 0, 255), hsvToRgb(Hsv(240f, 1f, 1f)))
        assertEquals(rgb(255, 0, 255), hsvToRgb(Hsv(300f, 1f, 1f)))
        assertEquals(rgb(255, 255, 255), hsvToRgb(Hsv(123f, 0f, 1f)))
        assertEquals(rgb(0, 0, 0), hsvToRgb(Hsv(123f, 1f, 0f)))
    }

    @Test fun rgbToHsvAndBackStaysWithinOneStepOnEveryEdgeCombination() {
        for (r in EDGES) {
            for (g in EDGES) {
                for (b in EDGES) {
                    val c = rgb(r, g, b)
                    val back = hsvToRgb(rgbToHsv(c))
                    assertTrue("$r $g $b came back ${back.toHex()}", near(c, back, 1))
                }
            }
        }
    }

    @Test fun greysHaveNoSaturationAndKeepTheirBrightness() {
        val hsv = rgbToHsv(rgb(128, 128, 128))
        assertEquals(0f, hsv.sat, 0f)
        assertEquals(128 / 255f, hsv.value, 1e-6f)
        assertEquals(0f, rgbToHsv(rgb(0, 0, 0)).value, 0f)
    }

    @Test fun hueStaysInsideTheStripForAnyColour() {
        for (r in EDGES) {
            for (g in EDGES) {
                for (b in EDGES) {
                    val hue = rgbToHsv(rgb(r, g, b)).hue
                    assertTrue("hue $hue", hue >= 0f && hue < HUE_DEGREES_MAX)
                }
            }
        }
    }

    @Test fun aHsvOutsideItsRangeIsRefused() {
        for (bad in listOf({
            Hsv(-1f, 0f, 0f)
        }, { Hsv(361f, 0f, 0f) }, { Hsv(0f, 1.1f, 0f) }, { Hsv(0f, 0f, -0.1f) })) {
            try {
                bad()
                error("expected a refusal")
            } catch (_: IllegalArgumentException) {
                // wanted
            }
        }
    }
}
