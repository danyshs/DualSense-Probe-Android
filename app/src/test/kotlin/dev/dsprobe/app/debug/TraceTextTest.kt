package dev.dsprobe.app.debug

import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceTextTest {
    @Test fun addressesWithColonsOrDashesInEitherCaseAreMasked() {
        assertEquals("a $BT_ADDRESS_MASK b", scrubAddresses("a 9c:ab:12:34:56:78 b"))
        assertEquals("a $BT_ADDRESS_MASK b", scrubAddresses("a 9C-AB-12-34-56-78 b"))
        assertEquals(
            "pad-$BT_ADDRESS_MASK-battery",
            scrubAddresses("pad-9c:ab:12:34:56:78-battery"),
        )
        assertEquals(
            "$BT_ADDRESS_MASK and $BT_ADDRESS_MASK",
            scrubAddresses("00:11:22:33:44:55 and aa:bb:cc:dd:ee:ff"),
        )
    }

    @Test fun nothingElseIsTouched() {
        for (safe in listOf(
            "01 7f 80 80 00 ff",
            "10:51:30",
            "report 0x01 byte 8 mask 0x20",
            "0x054c",
            "ab:cd:ef",
            "9c:ab:12:34:56",
        )) {
            assertEquals(safe, scrubAddresses(safe))
        }
    }

    @Test fun aTraceLineIsOneScrubbedLineWithTheControlAndRouteFirst() {
        val line = traceLine("in.button.cross", Route.DIRECT_USB, "device 9c:ab:12:34:56:78")
        assertTrue(line, line.startsWith("in.button.cross"))
        assertTrue(line.contains(" usb "))
        assertFalse(line.contains("9c:ab"))
        assertFalse(line.contains('\n'))
    }

    @Test fun aLineBreakInTheDetailIsRefused() {
        assertTrue(
            runCatching {
                traceLine("in.x", Route.ANDROID, "a\nb")
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test fun onlyTheFloodingControlsAreAxes() {
        for (axis in listOf(
            "in.stick.left.x",
            "in.trigger.r2",
            "in.gyro",
            "in.gyro.x",
            "in.accel.z",
            "in.touch.0.x.low8",
            "in.touch.0.pointer",
        )) {
            assertTrue(axis, isAxisControl(axis))
        }
        for (other in listOf(
            "in.button.cross",
            "in.dpad",
            "in.touch.0.id",
            "in.touch.1.active",
            "out.lightbar",
            "in.key.unmapped.91",
        )) {
            assertFalse(other, isAxisControl(other))
        }
    }
}
