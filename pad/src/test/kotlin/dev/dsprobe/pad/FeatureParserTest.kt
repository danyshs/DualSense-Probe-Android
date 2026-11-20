package dev.dsprobe.pad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureParserTest {
    private val reply = Fixtures.bytes("feature/firmware-0x20.hex")

    @Test fun parsesThePadsFirmwareReply() {
        val info = parseFirmwareInfo(reply)
        assertNotNull(info)
        assertEquals(0x00000413u, info!!.hardware)
        assertEquals(0x0110002au, info.firmware)
        assertEquals(0x0630.toUShort(), info.updateVersion)
        assertTrue(info.buildDate, info.buildDate.startsWith("Jul"))
        assertEquals(19, info.buildDate.length)
    }

    @Test fun thisPadUsesRumbleV2() {
        assertTrue(parseFirmwareInfo(reply)!!.supportsRumbleV2())
    }

    @Test fun rumbleV2StartsAtUpdate0215() {
        val base = parseFirmwareInfo(reply)!!
        assertFalse(base.copy(updateVersion = 0x0214.toUShort()).supportsRumbleV2())
        assertTrue(base.copy(updateVersion = 0x0215.toUShort()).supportsRumbleV2())
    }

    @Test fun rejectsWrongIdAndLengthWithoutThrowing() {
        assertNull(parseFirmwareInfo(ByteArray(0)))
        assertNull(parseFirmwareInfo(reply.copyOf(63)))
        assertNull(parseFirmwareInfo(reply.copyOf().also { it[0] = 0x09 }))
    }
}
