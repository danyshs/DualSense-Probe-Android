package dev.dsprobe.app.route

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.pad.Limits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val START_MS = 1_000L
private const val PASSES_MAX = 50
private const val HALF = 128 // 0x80, the strength the golden rumble reports use

private class PatternRig {
    var now = START_MS
    val sentAt = ArrayList<Long>()
    val sent = ArrayList<ByteArray>()
    val writer = OutputWriter({ sentAt.add(now); sent.add(it.copyOf()); true }, { now }, {}, {})

    fun golden(name: String) = TestFixtures.bytes("golden/$name.hex")

    /** Runs the writer up to [untilMs] on the fake clock, moving only to the times it asks to wake at. */
    fun runUntil(untilMs: Long) {
        for (pass in 0 until PASSES_MAX) {
            val wait = writer.pumpOnce() ?: return
            if (wait == 0L) continue
            if (now + wait > untilMs) {
                now = untilMs
                return
            }
            now += wait
        }
        error("writer still busy after $PASSES_MAX passes")
    }
}

class OutputWriterPatternTest {
    private val rig = PatternRig()
    private val w get() = rig.writer
    private val threeStep = listOf(RumbleStep(HALF, HALF, 100), RumbleStep(0, 0, 100), RumbleStep(HALF, HALF, 100))

    @Test fun eachStepGoesOutWhenItsTimeComesAndTheEndIsRumbleOff() {
        assertTrue(w.playRumble(threeStep))
        rig.runUntil(START_MS + 350)
        assertEquals(listOf(START_MS, START_MS + 100, START_MS + 200, START_MS + 300), rig.sentAt.take(4))
        assertArrayEquals(rig.golden("rumble-both"), rig.sent[0])
        assertArrayEquals(rig.golden("rumble-off"), rig.sent[1])
        assertArrayEquals(rig.golden("rumble-both"), rig.sent[2])
        assertArrayEquals(rig.golden("rumble-off"), rig.sent[3])
        assertTrue("nothing after the end but zeros", rig.sent.drop(3).all { it.contentEquals(rig.golden("rumble-off")) })
    }

    @Test fun theWriterNeverSleepsPastTheNextStep() {
        w.playRumble(threeStep)
        assertEquals(0L, w.pumpOnce())
        assertEquals(100L, w.pumpOnce())
        rig.now += 40
        assertEquals(60L, w.pumpOnce())
    }

    @Test fun theSentLevelsAreWhatTheLastReportCarried() {
        w.playRumble(listOf(RumbleStep(200, 30, 100), RumbleStep(0, 0, 100)))
        assertEquals(MotorLevels.OFF, w.rumbleSent)
        w.pumpOnce()
        assertEquals(MotorLevels(200, 30), w.rumbleSent)
        rig.runUntil(START_MS + 150)
        assertEquals(MotorLevels.OFF, w.rumbleSent)
    }

    @Test fun aPlainRumbleRequestCancelsTheRestOfThePattern() {
        w.playRumble(threeStep)
        w.pumpOnce()
        rig.now += Limits.OUTPUT_INTERVAL_MS_MIN
        w.setRumble(0u, 0u, 1)
        rig.runUntil(START_MS + 400)
        assertTrue("no rumble-both after the stop", rig.sent.drop(1).none { it.contentEquals(rig.golden("rumble-both")) })
    }

    @Test fun aPatternLongerThanTheCapIsRefusedAndSendsNothing() {
        val tooLong = List(6) { RumbleStep(HALF, HALF, 1_000) }
        assertThrows(IllegalArgumentException::class.java) { w.playRumble(tooLong) }
        assertThrows(IllegalArgumentException::class.java) { w.playRumble(emptyList()) }
        assertTrue(rig.sent.isEmpty())
    }

    @Test fun aPatternExactlyAtTheCapIsAccepted() {
        assertTrue(w.playRumble(List(5) { RumbleStep(HALF, HALF, 1_000) }))
    }

    @Test fun aPatternAfterTheReleaseBeganIsRefusedAndCounted() {
        w.finish()
        assertFalse(w.playRumble(threeStep))
        assertEquals(1L, w.droppedAfterCloseCount)
    }

    @Test fun aReleaseInTheMiddleOfAPatternEndsWithAllOffAndNothingAfter() {
        w.playRumble(threeStep)
        w.pumpOnce()
        rig.now += 100
        w.finish()
        for (pass in 0 until PASSES_MAX) {
            rig.now += Limits.OUTPUT_INTERVAL_MS_MIN
            if (w.pumpOnce() == null) break
        }
        assertArrayEquals(TestFixtures.allOffGolden, rig.sent.last())
        val count = rig.sent.size
        rig.now += 500
        assertNull(w.pumpOnce())
        assertEquals(count, rig.sent.size)
    }
}
