package dev.dsprobe.app.debug

import dev.dsprobe.app.R
import dev.dsprobe.app.output.LiveRoute
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.PATTERNS
import dev.dsprobe.app.output.PatternId
import dev.dsprobe.app.output.patternButtons
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.PadRoute
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class Recorder : PadRoute {
    val sent = ArrayList<OutputRequest>()

    override fun send(req: OutputRequest): ApiAnswer {
        sent.add(req)
        return ApiAnswer.Ok
    }

    override fun stop() = Unit
}

private class Rig {
    val route = Recorder()
    val controller = OutputController({ LiveRoute(route, Route.DIRECT_USB) }, { 1_000L }, { })
    val all = drivableOutputs(true, emptyList())

    fun rows() = offRows(controller.shown.value, all)
}

class OutputTableTest {
    @Test fun theRumbleCardOffersEveryPatternAndNoneTwice() {
        val buttons = patternButtons()
        assertEquals(PATTERNS.keys, buttons.toSet())
        assertEquals(PatternId.entries.toSet(), buttons.toSet())
        assertEquals(buttons.size, buttons.distinct().size)
        val card = buildEndpointCards().first { it.id == "out.rumble" }
        assertEquals(TryIt.Action(TryKind.RUMBLE), card.tryIt)
    }

    @Test fun aFreshControllerHasEveryRowOffAndNoneFailed() {
        val rows = Rig().rows()
        assertEquals(OutputKind.entries, rows.map { it.kind })
        assertTrue(rows.none { it.isOn || isFailed(it, true) })
    }

    @Test fun aRowStillOnAfterEverythingOffIsMarkedFailed() {
        val rig = Rig()
        turnEverythingOn(rig.controller, rig.all)
        val rows = rig.rows()
        assertTrue(rows.all { it.isOn })
        // the table before Everything off was pressed marks nothing; after it, every row still on
        assertTrue(rows.none { isFailed(it, false) })
        assertTrue(rows.all { isFailed(it, true) })
        assertFalse(isFailed(rows.first().copy(isDrivable = false), true))
        assertFalse(isFailed(rows.first().copy(isOn = false), true))
    }

    @Test fun everythingOffLeavesEveryRowOff() {
        val rig = Rig()
        turnEverythingOn(rig.controller, rig.all)
        fireChoice(TryChoice.ALL_OFF, rig.controller)
        val rows = rig.rows()
        assertEquals(OutputKind.entries.size, rows.size)
        for (row in rows) {
            assertFalse(row.kind.name, row.isOn)
            assertFalse(row.kind.name, isFailed(row, true))
        }
    }

    @Test fun everythingOnAsksForEachOutputTheRouteCanDrive() {
        val rig = Rig()
        turnEverythingOn(rig.controller, rig.all)
        val sent = rig.route.sent
        assertTrue(sent.any { it is OutputRequest.Lightbar })
        assertTrue(sent.any { it is OutputRequest.PlayerLeds })
        assertTrue(sent.any { it is OutputRequest.SetMuteLed })
        assertTrue(sent.any { it is OutputRequest.Rumble })
        val sides = sent.filterIsInstance<OutputRequest.Trigger>().map { it.isRight }
        assertEquals(setOf(true, false), sides.toSet())
    }

    @Test fun everythingOnSkipsWhatAndroidLocksAndTheTableSaysSo() {
        val rig = Rig()
        val locked = listOf(
            Reduction(Feature.MUTE_LED, R.string.reason_android_offers_nothing),
            Reduction(Feature.ADAPTIVE_TRIGGERS, R.string.reason_android_offers_nothing),
        )
        val drivable = drivableOutputs(true, locked)
        assertEquals(
            setOf(OutputKind.LIGHTBAR, OutputKind.PLAYER_LEDS, OutputKind.RUMBLE),
            drivable,
        )
        turnEverythingOn(rig.controller, drivable)
        val sent = rig.route.sent
        assertTrue(sent.none { it is OutputRequest.SetMuteLed || it is OutputRequest.Trigger })
        val rows = offRows(rig.controller.shown.value, drivable)
        assertEquals(3, rows.count { !it.isDrivable })
        assertEquals(3, rows.count { it.isOn })
    }

    @Test fun withoutALiveRouteNothingIsDrivable() {
        assertTrue(drivableOutputs(false, emptyList()).isEmpty())
    }
}
