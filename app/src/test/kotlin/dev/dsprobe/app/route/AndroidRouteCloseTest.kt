package dev.dsprobe.app.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AndroidRouteCloseTest {
    @Test
    fun neverClosesASessionWithNoRequest() {
        var closes = 0
        assertNull(closeIfRequested(false) { closes += 1 })
        assertEquals(0, closes)
    }

    @Test
    fun closesOnceAfterARequest() {
        var closes = 0
        assertNull(closeIfRequested(true) { closes += 1 })
        assertEquals(1, closes)
    }

    @Test
    fun handsBackTheSystemFailureInsteadOfThrowing() {
        val boom = NullPointerException("system")
        assertSame(boom, closeIfRequested(true) { throw boom })
    }
}
