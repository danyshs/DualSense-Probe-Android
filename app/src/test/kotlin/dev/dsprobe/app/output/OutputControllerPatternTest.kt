package dev.dsprobe.app.output

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.MotorLevels
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.PadRoute
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.totalDurationMs
import dev.dsprobe.pad.RumbleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class ModeRoute(override val rumbleMode: RumbleMode?, var levels: MotorLevels? = null) : PadRoute {
    val sent = ArrayList<OutputRequest>()

    override fun send(req: OutputRequest): ApiAnswer {
        sent.add(req)
        return ApiAnswer.Ok
    }

    override fun rumbleSent(): MotorLevels? = levels

    override fun stop() = Unit
}

private class PatternRig(kind: Route) {
    var now = 10_000L
    val route = ModeRoute(if (kind == Route.DIRECT_USB) RumbleMode.V2 else null)
    val controller = OutputController({ LiveRoute(route, kind) }, { now }, {})
}

class OutputControllerPatternTest {
    @Test fun aPatternIsBuiltAndSentWithItsWholeLengthAsTheCountdown() {
        val rig = PatternRig(Route.DIRECT_USB)
        rig.controller.playPattern(PatternId.HEARTBEAT, 255, 100, 3_000, 4)
        val req = rig.route.sent.single() as OutputRequest.RumblePattern
        assertEquals(3_000, totalDurationMs(req.steps))
        assertTrue(req.steps.all { it.right <= 100 })
        assertEquals(3_000L, rig.controller.shown.value.rumbleRemainingMs)
    }

    @Test fun onAndroidAPatternAsksWhetherItChangedAndDirectUsbDoesNot() {
        val android = PatternRig(Route.ANDROID)
        android.controller.playPattern(PatternId.IMPACT, 255, 255, 1_000, 4)
        assertEquals(AskKind.RUMBLE, android.controller.shown.value.ask?.kind)
        val usb = PatternRig(Route.DIRECT_USB)
        usb.controller.playPattern(PatternId.IMPACT, 255, 255, 1_000, 4)
        assertNull(usb.controller.shown.value.ask)
    }

    @Test fun changingTheModeStopsARunningRumbleFirstThenSendsTheMode() {
        val rig = PatternRig(Route.DIRECT_USB)
        rig.controller.setRumble(100, 100)
        rig.controller.setRumbleMode(RumbleMode.COMPATIBLE)
        val kinds = rig.route.sent.map { it::class.simpleName }
        assertEquals(listOf("Rumble", "Rumble", "SetRumbleMode"), kinds)
        val stop = rig.route.sent[1] as OutputRequest.Rumble
        assertEquals(0, stop.left.toInt() + stop.right.toInt())
        assertEquals(RumbleMode.COMPATIBLE, (rig.route.sent[2] as OutputRequest.SetRumbleMode).mode)
    }

    @Test fun theModeShownFollowsTheRouteAndAndroidShowsNone() {
        val usb = PatternRig(Route.DIRECT_USB)
        usb.controller.tick()
        assertEquals(RumbleMode.V2, usb.controller.shown.value.rumbleMode)
        val android = PatternRig(Route.ANDROID)
        android.controller.tick()
        assertNull(android.controller.shown.value.rumbleMode)
    }

    @Test fun theTraceRecordsSentLevelsStopsWhenIdleAndStaysBounded() {
        val rig = PatternRig(Route.DIRECT_USB)
        rig.controller.tick()
        assertTrue("idle adds nothing", rig.controller.shown.value.rumbleTrace.isEmpty())
        rig.route.levels = MotorLevels(200, 10)
        rig.controller.tick()
        assertEquals(listOf(MotorLevels(200, 10)), rig.controller.shown.value.rumbleTrace)
        rig.route.levels = MotorLevels.OFF
        rig.controller.tick()
        rig.controller.tick()
        assertEquals(2, rig.controller.shown.value.rumbleTrace.size)
        rig.route.levels = MotorLevels(1, 1)
        repeat(AppLimits.RUMBLE_TRACE_SAMPLES_MAX + 20) { rig.controller.tick() }
        assertEquals(AppLimits.RUMBLE_TRACE_SAMPLES_MAX, rig.controller.shown.value.rumbleTrace.size)
        assertNotNull(rig.controller.shown.value.rumbleTrace.last())
    }
}
