package dev.dsprobe.app.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLedsTest {
    @Test fun everyMaskRoundTripsThroughTheFiveDots() {
        for (mask in 0..31) assertEquals(mask, ledsToMask(maskToLeds(mask)))
    }

    @Test fun ledFiveIsTheTopBitAndLedOneIsTheBottomBit() {
        assertEquals(0x10, ledsToMask(listOf(false, false, false, false, true)))
        assertEquals(0x01, ledsToMask(listOf(true, false, false, false, false)))
        assertTrue(maskToLeds(0x10)[4])
        assertFalse(maskToLeds(0x10)[0])
    }

    @Test fun tappingADotFlipsOnlyItsOwnBit() {
        assertEquals(0x10, toggleLed(0, 4))
        assertEquals(0x0f, toggleLed(0x1f, 4))
        assertEquals(0x1e, toggleLed(0x1f, 0))
    }

    @Test fun theLocationLineShowsBinaryHexAndWhichLeds() {
        assertEquals("0b00010000 = 0x10, LED 5", ledMaskText(0x10))
        assertEquals("0b00000101 = 0x05, LEDs 1, 3", ledMaskText(0x05))
        assertEquals("0b00000000 = 0x00, all off", ledMaskText(0))
        assertEquals("0b00011111 = 0x1f, LEDs 1, 2, 3, 4, 5", ledMaskText(0x1f))
    }

    @Test(expected = IllegalArgumentException::class) fun aMaskPastFiveBitsIsRefused() {
        maskToLeds(0x20)
    }

    @Test(expected = IllegalArgumentException::class) fun aSixthDotIsRefused() {
        ledsToMask(List(6) { false })
    }
}
