package dev.dsprobe.app.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRouteCloseTest {
    private val logged = ArrayList<String>()

    @Test
    fun neverClosesASessionWithNoRequest() {
        var closes = 0
        assertTrue(closeLightsIfRequested(false, logged::add) { closes += 1 })
        assertEquals(0, closes)
    }

    @Test
    fun closesOnceAfterARequest() {
        var closes = 0
        assertTrue(closeLightsIfRequested(true, logged::add) { closes += 1 })
        assertEquals(1, closes)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun logsTheSystemFailureInsteadOfThrowing() {
        val boom = NullPointerException("system")
        val closed = closeLightsIfRequested(true, logged::add) { throw boom }
        assertFalse(closed)
        assertEquals(1, logged.size)
        assertTrue(logged.single(), logged.single().contains("system"))
    }
}
