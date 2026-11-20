package dev.dsprobe.app.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidRouteLateDropTest {
    @Test
    fun countsUpByOne() {
        assertEquals(1, nextLateDropCount(0))
        assertEquals(42, nextLateDropCount(41))
    }

    @Test
    fun stopsAtTheLimitInsteadOfWrapping() {
        val max = 1_000_000
        assertEquals(max, nextLateDropCount(max - 1))
        assertEquals(max, nextLateDropCount(max))
    }

    @Test
    fun refusesACountOutsideTheRange() {
        assertThrows(IllegalArgumentException::class.java) { nextLateDropCount(-1) }
        assertThrows(IllegalArgumentException::class.java) { nextLateDropCount(1_000_001) }
    }
}
