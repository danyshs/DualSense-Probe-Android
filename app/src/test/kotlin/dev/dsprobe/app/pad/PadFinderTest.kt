package dev.dsprobe.app.pad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun input(id: Int, product: Int = PRODUCT_DUALSENSE, vendor: Int = VENDOR_SONY) =
    SeenInput(id, vendor, product)

private fun usb(product: Int = PRODUCT_DUALSENSE, vendor: Int = VENDOR_SONY) =
    SeenUsb(null, vendor, product)

class PadFinderTest {
    @Test fun nothingSeenIsNone() {
        assertEquals(FindResult.NONE, classify(emptyList(), emptyList()))
    }

    @Test fun otherControllersAreNotPads() {
        assertEquals(
            FindResult.NONE,
            classify(
                listOf(input(1, product = 0x09cc), input(2, vendor = 0x045e)),
                listOf(usb(product = 0x09cc)),
            ),
        )
    }

    @Test fun oneInputDeviceAndNoUsbIsBluetooth() {
        val r = classify(listOf(input(4)), emptyList())
        assertEquals(Transport.BLUETOOTH, r.inputPad?.transport)
        assertNull(r.usb)
        assertFalse(r.tooMany)
    }

    @Test fun usbListedWithItsMergedDeviceIsUsb() {
        val r = classify(listOf(input(4)), listOf(usb()))
        assertEquals(Transport.USB, r.inputPad?.transport)
        assertEquals(listOf(4), r.inputPad?.inputDeviceIds)
        assertFalse(r.inputPad!!.alsoPairedBluetooth)
    }

    @Test fun usbListedWithoutInputDevicesHasNoInputPad() {
        val r = classify(emptyList(), listOf(usb()))
        assertNull(r.inputPad)
        assertTrue(r.usb != null)
        assertFalse(r.tooMany)
    }

    @Test fun usbPlusOnePairedCopyIsOnePadAlsoPaired() {
        val r = classify(listOf(input(4), input(5)), listOf(usb()))
        assertEquals(Transport.USB, r.inputPad?.transport)
        assertTrue(r.inputPad!!.alsoPairedBluetooth)
        assertEquals(listOf(4, 5), r.inputPad.inputDeviceIds)
    }

    @Test fun theEdgeIsRecognisedAndKeepsItsProductId() {
        val r =
            classify(listOf(input(4, PRODUCT_DUALSENSE_EDGE)), listOf(usb(PRODUCT_DUALSENSE_EDGE)))
        assertEquals(PRODUCT_DUALSENSE_EDGE, r.inputPad?.productId)
    }

    @Test fun aSecondPadIsTooManyOnEveryTransportMix() {
        val many = FindResult(null, null, true)
        assertEquals(many, classify(listOf(input(1), input(2)), emptyList())) // two paired
        // two cables
        assertEquals(many, classify(emptyList(), listOf(usb(), usb(PRODUCT_DUALSENSE_EDGE))))
        // cable plus two others
        assertEquals(many, classify(listOf(input(1), input(2), input(3)), listOf(usb())))
    }
}
