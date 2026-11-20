package dev.dsprobe.pad

import org.junit.Assert.assertEquals
import org.junit.Test

class HexTest {
    @Test fun lowercaseSpaceSeparated() {
        assertEquals("00 0a ff 80", byteArrayOf(0, 10, -1, -128).toHex())
        assertEquals("", ByteArray(0).toHex())
    }

    @Test fun roundTripsAFixtureLine() {
        val line = Fixtures::class.java.getResource("/reports/idle.hex")!!.readText().trim()
        assertEquals(line, Fixtures.report("idle").toHex())
    }
}
