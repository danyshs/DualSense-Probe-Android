package dev.dsprobe.app.output

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.PadRoute
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRoute(var answer: ApiAnswer = ApiAnswer.Ok) : PadRoute {
    val sent = ArrayList<OutputRequest>()

    override fun send(req: OutputRequest): ApiAnswer {
        sent.add(req)
        return answer
    }

    override fun stop() = Unit
}

private fun rgb(r: Int, g: Int, b: Int) = Rgb(r.toUByte(), g.toUByte(), b.toUByte())

private class Rig(kind: Route = Route.DIRECT_USB) {
    var now = 10_000L
    val route = FakeRoute()
    var live: LiveRoute? = LiveRoute(route, kind)
    val log = ArrayList<String>()
    val controller = OutputController({ live }, { now }, { log.add(it) })
}

class OutputControllerTest {
    private val rig = Rig()
    private val c get() = rig.controller

    @Test fun rumbleIsClampedAndGivenTheMaximumDuration() {
        c.setRumble(999, -5)
        val sent = rig.route.sent.single() as OutputRequest.Rumble
        assertEquals(255, sent.left.toInt())
        assertEquals(0, sent.right.toInt())
        assertEquals(Limits.RUMBLE_DURATION_MS_MAX, sent.durationMs)
        assertEquals(Limits.RUMBLE_DURATION_MS_MAX.toLong(), c.shown.value.rumbleRemainingMs)
    }

    @Test fun movingTheSliderDoesNotExtendTheFiveSeconds() {
        c.setRumble(100, 0)
        rig.now += 3_000
        c.setRumble(200, 50)
        val second = rig.route.sent.last() as OutputRequest.Rumble
        assertEquals(2_000, second.durationMs)
        assertEquals(2_000L, c.shown.value.rumbleRemainingMs)
    }

    @Test fun aNewPressAfterTheTimeRanOutGetsAFreshFiveSeconds() {
        c.setRumble(100, 0)
        rig.now += Limits.RUMBLE_DURATION_MS_MAX + 1
        c.setRumble(100, 0)
        assertEquals(Limits.RUMBLE_DURATION_MS_MAX, (rig.route.sent.last() as OutputRequest.Rumble).durationMs)
    }

    @Test fun releasingBothSlidersStopsAtOnceAndAFreshPressStartsAFreshClock() {
        c.setRumble(100, 100)
        rig.now += 1_000
        c.setRumble(0, 0)
        val stop = rig.route.sent.last() as OutputRequest.Rumble
        assertEquals(0, stop.left.toInt() + stop.right.toInt())
        assertEquals(0L, c.shown.value.rumbleRemainingMs)
        c.setRumble(10, 0)
        assertEquals(Limits.RUMBLE_DURATION_MS_MAX, (rig.route.sent.last() as OutputRequest.Rumble).durationMs)
    }

    @Test fun theCountdownRunsDownOnTicksAndTheShownMotorsReturnToZeroAtTheEnd() {
        c.setRumble(100, 50)
        rig.now += 2_500
        c.tick()
        assertEquals(2_500L, c.shown.value.rumbleRemainingMs)
        assertEquals(100, c.shown.value.rumbleLeft)
        rig.now += 2_500
        c.tick()
        assertEquals(0L, c.shown.value.rumbleRemainingMs)
        assertEquals(0, c.shown.value.rumbleLeft + c.shown.value.rumbleRight)
    }

    @Test fun aColourGoesOutAtOnceTheFirstTimeAndTheSwatchFollowsWhatWasSent() {
        c.setLightbar(rgb(1, 2, 3))
        assertEquals(listOf<OutputRequest>(OutputRequest.Lightbar(rgb(1, 2, 3))), rig.route.sent)
        assertEquals(rgb(1, 2, 3), c.shown.value.lightbar)
    }

    @Test fun coloursInsideTheIntervalAreHeldAndOnlyTheNewestIsSentWhenItIsUp() {
        c.setLightbar(rgb(1, 0, 0))
        rig.now += AppLimits.COLOUR_SEND_INTERVAL_MS_MIN - 1
        c.setLightbar(rgb(2, 0, 0))
        c.setLightbar(rgb(3, 0, 0))
        assertEquals(1, rig.route.sent.size)
        assertEquals(rgb(1, 0, 0), c.shown.value.lightbar)
        c.tick()
        assertEquals(1, rig.route.sent.size)
        rig.now += 1
        c.tick()
        assertEquals(OutputRequest.Lightbar(rgb(3, 0, 0)), rig.route.sent.last())
        assertEquals(2, rig.route.sent.size)
        rig.now += 1_000
        c.tick()
        assertEquals("nothing is held, nothing is resent", 2, rig.route.sent.size)
    }

    @Test fun theIntervalIsMeasuredFromTheLastSendNotTheLastRequest() {
        c.setLightbar(rgb(1, 0, 0))
        rig.now += AppLimits.COLOUR_SEND_INTERVAL_MS_MIN
        c.setLightbar(rgb(2, 0, 0))
        assertEquals(2, rig.route.sent.size)
    }

    @Test fun aFlushSendsTheNewestColourWhateverTheInterval() {
        c.setLightbar(rgb(1, 0, 0))
        c.setLightbar(rgb(9, 9, 9))
        assertEquals(1, rig.route.sent.size)
        c.flushLightbar()
        assertEquals(OutputRequest.Lightbar(rgb(9, 9, 9)), rig.route.sent.last())
        c.flushLightbar()
        assertEquals("a flush with nothing held sends nothing", 2, rig.route.sent.size)
    }

    @Test fun playerLedsAndMuteLedGoThroughAndAreClamped() {
        c.setPlayerLeds(0x7f)
        assertEquals(OutputRequest.PlayerLeds(0x1f), rig.route.sent.last())
        assertEquals(0x1f, c.shown.value.playerLeds)
        c.setPlayerLeds(-3)
        assertEquals(OutputRequest.PlayerLeds(0), rig.route.sent.last())
        c.setMuteLed(MuteLed.PULSE)
        assertEquals(OutputRequest.SetMuteLed(MuteLed.PULSE), rig.route.sent.last())
        assertEquals(MuteLed.PULSE, c.shown.value.muteLed)
    }

    @Test fun directUsbNeverAsksIfItChanged() {
        c.setLightbar(rgb(1, 1, 1))
        c.setPlayerLeds(1)
        c.setMuteLed(MuteLed.ON)
        assertNull(c.shown.value.ask)
    }

    @Test fun androidAsksIfItChangedForEveryLightAndLogsTheAnswerNextToAndroidsOwn() {
        val android = Rig(Route.ANDROID)
        android.controller.setLightbar(rgb(255, 0, 0))
        val ask = android.controller.shown.value.ask!!
        assertEquals(AskKind.LIGHTBAR, ask.kind)
        assertEquals(ApiAnswer.Ok, ask.answer)
        android.controller.answerChanged(false)
        assertNull(android.controller.shown.value.ask)
        assertEquals(listOf("lightbar ff0000: Android answered Ok; did it change? no"), android.log)
        android.controller.setPlayerLeds(5)
        assertEquals(AskKind.PLAYER_LEDS, android.controller.shown.value.ask!!.kind)
        android.controller.answerChanged(true)
        assertEquals("player LEDs 05: Android answered Ok; did it change? yes", android.log.last())
        android.controller.setMuteLed(MuteLed.ON)
        assertEquals(AskKind.MUTE_LED, android.controller.shown.value.ask!!.kind)
    }

    @Test fun androidRumbleIsNotAskedAbout() {
        val android = Rig(Route.ANDROID)
        android.controller.setRumble(10, 10)
        assertNull(android.controller.shown.value.ask)
    }

    @Test fun aRefusalIsKeptAndLoggedAndTheAskCarriesIt() {
        val android = Rig(Route.ANDROID)
        android.route.answer = ApiAnswer.Failed("no light named indicator")
        android.controller.setLightbar(rgb(0, 255, 0))
        assertEquals(ApiAnswer.Failed("no light named indicator"), android.controller.shown.value.lastAnswer)
        assertTrue(android.log.single().contains("no light named indicator"))
        android.controller.answerChanged(false)
        assertTrue(android.log.last().contains("Failed(no light named indicator)"))
    }

    @Test fun answeringWithNothingAskedChangesNothing() {
        c.answerChanged(true)
        assertTrue(rig.log.isEmpty())
    }

    @Test fun withNoLiveRouteNothingIsSentAndTheAnswerSaysSo() {
        rig.live = null
        c.setLightbar(rgb(1, 2, 3))
        c.setRumble(5, 5)
        assertTrue(rig.route.sent.isEmpty())
        assertEquals(ApiAnswer.Failed("no pad is live"), c.shown.value.lastAnswer)
    }

    @Test fun resetForgetsTheSwatchTheSlidersAndAHeldColour() {
        c.setRumble(100, 100)
        c.setLightbar(rgb(1, 1, 1))
        c.setLightbar(rgb(2, 2, 2))
        c.reset()
        assertEquals(OutputsShown.IDLE, c.shown.value)
        rig.now += 1_000
        c.tick()
        assertEquals("the held colour died with the route", 2, rig.route.sent.size)
        c.setRumble(1, 0)
        assertEquals(Limits.RUMBLE_DURATION_MS_MAX, (rig.route.sent.last() as OutputRequest.Rumble).durationMs)
    }
}
