package dev.dsprobe.app.route

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.TestFixtures
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.OutputField
import dev.dsprobe.pad.OutputState
import dev.dsprobe.pad.Rgb
import dev.dsprobe.pad.TriggerEffect
import dev.dsprobe.pad.allOff
import dev.dsprobe.pad.buildUsbOutput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PASSES_MAX = 20

private class Rig {
    var now = 1_000L
    val sent = ArrayList<ByteArray>()
    var accept = true
    val failing = ArrayList<String>()
    val writer = OutputWriter({
        sent.add(it.copyOf())
        accept
    }, { now }, { failing.add(it) }, {})

    /** Passes until the writer is done, a clock tick of the minimum gap between them. */
    fun drain() {
        repeat(PASSES_MAX) {
            if (writer.pumpOnce() == null) return
            now += Limits.OUTPUT_INTERVAL_MS_MIN
        }
        error("writer still going after $PASSES_MAX passes")
    }
}

private val off = OutputState.allOff()
private val red = Rgb(255u, 0u, 0u)

class OutputWriterTest {
    private val rig = Rig()
    private val w get() = rig.writer

    @Test fun twoChangesBetweenSendsBothReachTheReport() {
        w.change(OutputField.RUMBLE) { it.copy(rumbleLeft = 0x80u) }
        w.change(OutputField.LIGHTBAR) { it.copy(lightbar = Rgb(1u, 2u, 3u)) }
        assertEquals(0L, w.pumpOnce())
        assertEquals(1, rig.sent.size)
        val want = off.copy(rumbleLeft = 0x80u, lightbar = Rgb(1u, 2u, 3u))
        assertArrayEquals(
            buildUsbOutput(want, setOf(OutputField.RUMBLE, OutputField.LIGHTBAR)),
            rig.sent[0],
        )
        assertEquals(0x80, rig.sent[0][4].toInt() and 0xff)
        assertEquals(listOf(1, 2, 3), (45..47).map { rig.sent[0][it].toInt() })
    }

    @Test fun aNewerValueOverwritesAnOlderOnePerFieldAndNothingElseGoesOut() {
        w.change(OutputField.RUMBLE) { it.copy(rumbleLeft = 0x10u) }
        w.change(OutputField.RUMBLE) { it.copy(rumbleLeft = 0x20u) }
        w.pumpOnce()
        assertEquals(1, rig.sent.size)
        assertEquals(0x20, rig.sent[0][4].toInt())
        assertEquals(
            "flag 1 stays clear: no other feature was asked for",
            0,
            rig.sent[0][2].toInt(),
        )
    }

    @Test fun sendsAreSpacedByTheMinimumInterval() {
        w.change(OutputField.LIGHTBAR) { it.copy(lightbar = red) }
        w.pumpOnce()
        w.change(OutputField.PLAYER_LEDS) { it.copy(playerLedMask = 1) }
        assertEquals(Limits.OUTPUT_INTERVAL_MS_MIN.toLong(), w.pumpOnce())
        rig.now += Limits.OUTPUT_INTERVAL_MS_MIN - 1
        assertEquals(1L, w.pumpOnce())
        assertEquals(1, rig.sent.size)
        rig.now += 1
        assertEquals(0L, w.pumpOnce())
        assertEquals(2, rig.sent.size)
    }

    @Test fun afterTheFinishTheAllOffReportIsTheLastOneAndNothingFollows() {
        w.change(OutputField.LIGHTBAR) { it.copy(lightbar = red) }
        w.finish()
        rig.drain()
        assertEquals(2, rig.sent.size)
        assertArrayEquals(
            buildUsbOutput(off.copy(lightbar = red), setOf(OutputField.LIGHTBAR)),
            rig.sent[0],
        )
        assertArrayEquals(TestFixtures.allOffGolden, rig.sent.last())
        assertFalse(w.change(OutputField.RUMBLE) { it.copy(rumbleLeft = 9u) })
        assertEquals(1L, w.droppedAfterCloseCount)
        rig.now += 10 * Limits.OUTPUT_RESEND_INTERVAL_MS
        assertNull(w.pumpOnce())
        assertEquals(2, rig.sent.size)
    }

    @Test fun allOffGoesOutEvenWhenNothingWasEverSet() {
        w.finish()
        rig.drain()
        assertEquals(1, rig.sent.size)
        assertArrayEquals(TestFixtures.allOffGolden, rig.sent[0])
    }

    @Test fun theFinalReportReplacesAResendThatWasDue() {
        w.change(OutputField.LIGHTBAR) { it.copy(lightbar = red) }
        w.pumpOnce()
        rig.now += Limits.OUTPUT_RESEND_INTERVAL_MS
        w.finish()
        rig.drain()
        assertEquals(2, rig.sent.size)
        assertArrayEquals(TestFixtures.allOffGolden, rig.sent.last())
    }

    @Test fun resendRepeatsOnlyWhatTheAppSetAtTheResendInterval() {
        w.change(OutputField.LIGHTBAR) { it.copy(lightbar = red) }
        w.pumpOnce()
        rig.now += Limits.OUTPUT_RESEND_INTERVAL_MS - 1
        assertEquals(1L, w.pumpOnce())
        assertEquals(1, rig.sent.size)
        rig.now += 1
        assertEquals(0L, w.pumpOnce())
        assertEquals(2, rig.sent.size)
        assertArrayEquals(rig.sent[0], rig.sent[1])
        assertEquals(
            "rumble was never set, so it is not resent",
            0,
            rig.sent[1][1].toInt() and 0x03,
        )
    }

    @Test fun nothingSetMeansNothingIsEverSent() {
        repeat(PASSES_MAX) {
            w.pumpOnce()
            rig.now += Limits.OUTPUT_RESEND_INTERVAL_MS
        }
        assertTrue(rig.sent.isEmpty())
    }

    @Test fun aRefusedReportIsRetriedAndTheFailureIsToldOnce() {
        rig.accept = false
        w.change(OutputField.LIGHTBAR) { it.copy(lightbar = red) }
        repeat(AppLimits.SEND_FAILURES_IN_A_ROW_MAX + 3) {
            w.pumpOnce()
            rig.now += Limits.OUTPUT_INTERVAL_MS_MIN
        }
        assertEquals(1, rig.failing.size)
        assertTrue(rig.sent.size >= AppLimits.SEND_FAILURES_IN_A_ROW_MAX)
        rig.accept = true
        val before = rig.sent.size
        w.pumpOnce()
        assertEquals(before + 1, rig.sent.size)
        assertArrayEquals(
            buildUsbOutput(off.copy(lightbar = red), setOf(OutputField.LIGHTBAR)),
            rig.sent.last(),
        )
    }

    @Test fun haltStopsEverySendEvenTheFinalOne() {
        w.change(OutputField.LIGHTBAR) { it.copy(lightbar = red) }
        w.halt()
        w.finish()
        assertNull(w.pumpOnce())
        assertTrue(rig.sent.isEmpty())
    }

    @Test fun aDebugCrashThrowsOnTheNextPass() {
        w.crashNow()
        assertThrows(IllegalStateException::class.java) { w.pumpOnce() }
    }

    @Test fun everyReportIsTheRightSize() {
        w.change(OutputField.LIGHTBAR) { it.copy(lightbar = red) }
        w.finish()
        rig.drain()
        assertTrue(rig.sent.isNotEmpty())
        assertTrue(rig.sent.all { it.size == Limits.USB_OUTPUT_REPORT_BYTES })
    }

    @Test fun aTriggerEffectIsSetBackToOffAtItsDeadlineByTheWriterItself() {
        w.setTrigger(true, TriggerEffect.resistance(0x30u, 0xc0u), 100)
        w.pumpOnce()
        assertEquals(0x30, rig.sent[0][12].toInt())
        rig.now += 99
        w.pumpOnce()
        assertEquals("not yet", 1, rig.sent.size)
        rig.now += 1
        w.pumpOnce()
        assertArrayEquals(buildUsbOutput(off, setOf(OutputField.RIGHT_TRIGGER)), rig.sent[1])
    }

    @Test fun theWriterNeverSleepsPastATriggerDeadline() {
        w.setTrigger(false, TriggerEffect.resistance(1u, 2u), 50)
        w.pumpOnce()
        val wait = w.pumpOnce()
        assertTrue("slept $wait ms", wait != null && wait <= 50L)
    }

    @Test fun aTriggerDurationPastTheLimitIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            w.setTrigger(
                true,
                TriggerEffect.resistance(1u, 2u),
                Limits.TRIGGER_EFFECT_DURATION_MS_MAX + 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            w.setTrigger(true, TriggerEffect.OFF, 0)
        }
    }

    @Test fun aTriggerAfterTheFinishIsDroppedAndCounted() {
        w.finish()
        assertFalse(w.setTrigger(true, TriggerEffect.resistance(1u, 2u), 100))
        assertEquals(1L, w.droppedAfterCloseCount)
    }

    @Test fun theFinishAllOffClearsBothTriggers() {
        w.setTrigger(true, TriggerEffect.resistance(1u, 2u), 1_000)
        w.setTrigger(false, TriggerEffect.resistance(3u, 4u), 1_000)
        w.finish()
        rig.drain()
        val last = rig.sent.last()
        assertArrayEquals(w.allOffReport(), last)
        assertTrue((11..32).all { last[it].toInt() == 0 })
    }
}
