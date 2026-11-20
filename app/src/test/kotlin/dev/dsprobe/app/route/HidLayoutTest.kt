package dev.dsprobe.app.route

import dev.dsprobe.pad.Limits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HidLayoutTest {
    private val good = listOf(
        EndpointDesc(Limits.USB_HID_IN_ENDPOINT_ADDRESS, true, Limits.USB_HID_ENDPOINT_PACKET_BYTES),
        EndpointDesc(Limits.USB_HID_OUT_ENDPOINT_ADDRESS, true, Limits.USB_HID_ENDPOINT_PACKET_BYTES),
    )

    @Test fun theProvenLayoutPasses() = assertNull(checkHidLayout(true, good))

    @Test fun aNonHidInterfaceIsRefused() = assertNotNull(checkHidLayout(false, good))

    @Test fun aMissingEndpointIsNamed() {
        assertEquals("no endpoint 0x84", checkHidLayout(true, good.drop(1)))
        assertEquals("no endpoint 0x03", checkHidLayout(true, good.take(1)))
    }

    @Test fun aNonInterruptEndpointIsRefused() {
        assertNotNull(checkHidLayout(true, listOf(good[0].copy(isInterrupt = false), good[1])))
    }

    @Test fun aDifferentPacketSizeIsRefused() {
        assertNotNull(checkHidLayout(true, listOf(good[0], good[1].copy(maxPacketBytes = 32))))
    }
}
