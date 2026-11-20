package dev.dsprobe.app.route

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
import dev.dsprobe.pad.RumbleMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HALF: UByte = 0x80u
private const val PASSES_MAX = 20

private class Clock {
    var now = 1_000L
    val sent = ArrayList<ByteArray>()
    val writer = OutputWriter({ sent.add(it.copyOf()); true }, { now }, {}, {})

    fun golden(name: String) = TestFixtures.bytes("golden/$name.hex")
}

class OutputWriterRumbleTest {
    private val clock = Clock()
    private val w get() = clock.writer

    @Test fun rumbleStopsInsideTheWriterAtItsDeadlineWithNoHelpFromTheCaller() {
        assertTrue(w.setRumble(HALF, HALF, 1_000))
        assertEquals(0L, w.pumpOnce())
        assertArrayEquals(clock.golden("rumble-both"), clock.sent[0])
        clock.now += 999
        assertEquals("the resend still rumbles one millisecond before the deadline", 0L, w.pumpOnce())
        assertArrayEquals(clock.golden("rumble-both"), clock.sent[1])
        clock.now += 1
        val wait = w.pumpOnce()
        assertEquals("the zero is due but the minimum gap since the last send holds it", (Limits.OUTPUT_INTERVAL_MS_MIN - 1).toLong(), wait)
        clock.now += Limits.OUTPUT_INTERVAL_MS_MIN
        assertEquals(0L, w.pumpOnce())
        assertEquals(3, clock.sent.size)
        assertArrayEquals(clock.golden("rumble-off"), clock.sent[2])
    }

    @Test fun theWriterNeverSleepsPastTheDeadline() {
        w.setRumble(HALF, 0u, 100)
        w.pumpOnce()
        assertEquals(100L, w.pumpOnce())
        clock.now += 40
        assertEquals(60L, w.pumpOnce())
    }

    @Test fun aStopRequestClearsTheDeadlineSoNothingExpiresTwice() {
        w.setRumble(HALF, HALF, 100)
        w.pumpOnce()
        clock.now += Limits.OUTPUT_INTERVAL_MS_MIN
        w.setRumble(0u, 0u, 1)
        w.pumpOnce()
        assertArrayEquals(clock.golden("rumble-off"), clock.sent.last())
        val count = clock.sent.size
        clock.now += 100
        val wait = w.pumpOnce()
        assertEquals("only the periodic resend is left, never an expiry report", count, clock.sent.size)
        assertEquals(Limits.OUTPUT_RESEND_INTERVAL_MS.toLong() - 100, wait)
    }

    @Test fun theV2ModeChangesTheFlagsAndSettingItSendsNothing() {
        w.setRumbleMode(RumbleMode.V2)
        assertEquals(Limits.OUTPUT_RESEND_INTERVAL_MS.toLong(), w.pumpOnce())
        assertTrue(clock.sent.isEmpty())
        w.setRumble(HALF, HALF, 1_000)
        w.pumpOnce()
        assertArrayEquals(clock.golden("rumble-v2-both"), clock.sent[0])
        clock.now += 1_000
        w.pumpOnce()
        clock.now += Limits.OUTPUT_INTERVAL_MS_MIN
        w.pumpOnce()
        assertArrayEquals(clock.golden("rumble-v2-off"), clock.sent.last())
    }

    @Test fun rumbleAskedForAfterTheReleaseBeganIsRefusedAndCounted() {
        w.finish()
        assertFalse(w.setRumble(HALF, HALF, 100))
        assertEquals(1L, w.droppedAfterCloseCount)
        while (true) {
            clock.now += Limits.OUTPUT_INTERVAL_MS_MIN
            if (w.pumpOnce() == null) break
            check(clock.sent.size < PASSES_MAX) { "writer not finishing" }
        }
        assertEquals(1, clock.sent.size)
        assertArrayEquals(TestFixtures.allOffGolden, clock.sent[0])
    }

    @Test fun aDurationOutsideItsLimitsIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { w.setRumble(HALF, HALF, 0) }
        assertThrows(IllegalArgumentException::class.java) { w.setRumble(HALF, HALF, Limits.RUMBLE_DURATION_MS_MAX + 1) }
        w.setRumble(HALF, HALF, Limits.RUMBLE_DURATION_MS_MAX)
    }

    @Test fun anExpiryDuringTheReleaseStillEndsWithAllOffLast() {
        w.setRumble(HALF, HALF, 100)
        w.pumpOnce()
        clock.now += 100
        w.finish()
        for (i in 0 until PASSES_MAX) {
            clock.now += Limits.OUTPUT_INTERVAL_MS_MIN
            if (w.pumpOnce() == null) break
        }
        assertArrayEquals(TestFixtures.allOffGolden, clock.sent.last())
        assertNull(w.pumpOnce())
        assertTrue(OutputField.RUMBLE_BOTH_MODES in dev.dsprobe.pad.RELEASE_FIELDS)
    }
}
