package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import dev.dsprobe.pad.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEnv : RunnerEnv {
    var route: Route? = Route.ANDROID
    var locked = emptySet<Feature>()
    var muteIsWatched = false
    var muteKey: Int? = null
    val sends = ArrayList<OutputRequest>()
    val routeRequests = ArrayList<Route>()
    var answer: ApiAnswer = ApiAnswer.Ok
    var cameUp = true

    override fun currentRoute() = route
    override fun requestRoute(route: Route) {
        routeRequests.add(route)
        if (cameUp) this.route = route
    }
    override fun send(req: OutputRequest): ApiAnswer {
        sends.add(req)
        return answer
    }
    override fun isLocked(feature: Feature) = feature in locked
    override fun setMuteWatched(on: Boolean) {
        muteIsWatched = on
    }
    override fun muteKeyCode() = muteKey
}

private var clock = 0L

private fun input(id: String, feature: Feature? = Feature.BUTTONS, route: Route = Route.ANDROID, companions: Set<InputId> = emptySet()) =
    TestCase(id, route, Kind.INPUT, R.string.prompt_press, id, AppLimits.INPUT_TEST_TIMEOUT_MS, Check.AwaitInput(InputId(id)), companions, feature)

private fun output(id: String, req: OutputRequest, cleanup: OutputRequest? = null, route: Route = Route.ANDROID, feature: Feature? = null) =
    TestCase(id, route, Kind.OUTPUT, R.string.prompt_watch, id, AppLimits.CONFIRM_TAP_TIMEOUT_MS, Check.SendOutput(req, cleanup), feature = feature)

private fun routeStep(id: String, route: Route) =
    TestCase(id, route, Kind.ROUTE, R.string.prompt_route_usb, "", AppLimits.ROUTE_SWITCH_TIMEOUT_MS, Check.AwaitRoute(route))

private fun held(vararg b: Button, dpad: Dpad = Dpad.NONE, l2: Float = 0f, leftX: Float = 0f) =
    FeedEvent(0L, Route.ANDROID, null, "x", "in.x", PadState.EMPTY.copy(held = b.toSet(), dpad = dpad, l2 = l2, leftX = leftX))

private val RED = OutputRequest.Lightbar(Rgb(255u, 0u, 0u))
private val BLACK = OutputRequest.Lightbar(Rgb(0u, 0u, 0u))

class DebugRunnerTest {
    private val env = FakeEnv()

    private fun runner(vararg tests: TestCase): DebugRunner {
        clock = 0L
        return DebugRunner(tests.toList(), env) { clock }
    }

    private fun outcomes(r: DebugRunner) = r.results.map { it.id to it.outcome }

    @Test fun everyTestIsNotProvenUntilTheRunReachesIt() {
        val r = runner(input("in.button.cross"), input("in.button.circle"))
        r.start()
        r.stop()
        assertEquals(listOf("in.button.cross" to Outcome.SKIPPED, "in.button.circle" to Outcome.NOT_PROVEN), outcomes(r))
        assertEquals(Phase.DONE, r.phase)
    }

    @Test fun aButtonPassesOnItsFirstMatchingEvent() {
        val r = runner(input("in.button.cross"), input("in.button.circle"))
        r.start()
        r.onEvents(listOf(held(Button.CROSS)))
        assertEquals(listOf("in.button.cross" to Outcome.PASSED), outcomes(r))
        assertEquals("in.button.circle", r.current?.id)
    }

    @Test fun anotherInputMovingFirstIsCrossTalkNamingTheCulprit() {
        val r = runner(input("in.button.cross"))
        r.start()
        r.onEvents(listOf(held()))
        r.onEvents(listOf(held(Button.CIRCLE)))
        assertEquals(Outcome.CROSS_TALK, r.results.single().outcome)
        assertTrue(r.results.single().detail, r.results.single().detail.startsWith("in.button.circle"))
    }

    @Test fun aCompanionMovingWithTheTargetIsNotCrossTalk() {
        val r = runner(input("in.button.l2", companions = setOf(InputId("in.trigger.l2"))))
        r.start()
        r.onEvents(listOf(held(l2 = 0.5f))) // the analog bar moves first
        assertEquals(emptyList<Pair<String, Outcome>>(), outcomes(r))
        r.onEvents(listOf(held(Button.L2, l2 = 1f)))
        assertEquals(Outcome.PASSED, r.results.single().outcome)
    }

    @Test fun aStickPassesOnlyWhenBothEndsWereReached() {
        val r = runner(input("in.stick.left.x", Feature.STICKS, companions = setOf(InputId("in.stick.left."))))
        r.start()
        r.onEvents(listOf(held(leftX = 1f)))
        assertEquals(emptyList<Pair<String, Outcome>>(), outcomes(r))
        r.onEvents(listOf(held(leftX = -1f)))
        assertEquals(Outcome.PASSED, r.results.single().outcome)
    }

    @Test fun aDpadDirectionPassesOnThatDirectionOnly() {
        val r = runner(input("in.dpad.ne"))
        r.start()
        r.onEvents(listOf(held(dpad = Dpad.N)))
        assertEquals(emptyList<Pair<String, Outcome>>(), outcomes(r))
        r.onEvents(listOf(held(dpad = Dpad.NE)))
        assertEquals(Outcome.PASSED, r.results.single().outcome)
    }

    @Test fun nothingSeenInTimeIsNotSeenAndNotATickEarlier() {
        val r = runner(input("in.button.cross"))
        r.start()
        clock = AppLimits.INPUT_TEST_TIMEOUT_MS - 1
        r.tick()
        assertEquals(Phase.WAITING_INPUT, r.phase)
        clock = AppLimits.INPUT_TEST_TIMEOUT_MS
        r.tick()
        assertEquals(Outcome.NOT_SEEN, r.results.single().outcome)
    }

    @Test fun aFeatureAndroidListsNothingForIsNotOfferedWithoutAPrompt() {
        env.locked = setOf(Feature.STICKS)
        val r = runner(input("in.stick.left.x", Feature.STICKS))
        r.start()
        assertEquals(listOf("in.stick.left.x" to Outcome.NOT_OFFERED), outcomes(r))
        assertEquals(Phase.DONE, r.phase)
    }

    @Test fun muteIsPromptedEvenWhileLockedAndPassesOnTheKeyAndroidSent() {
        env.locked = setOf(Feature.MUTE_BUTTON)
        val r = runner(input("in.button.mute", Feature.MUTE_BUTTON))
        r.start()
        assertEquals(Phase.WAITING_INPUT, r.phase)
        assertTrue("the route is told to watch for the Mute key", env.muteIsWatched)
        env.muteKey = 91
        r.tick()
        assertEquals(Outcome.PASSED, r.results.single().outcome)
        assertTrue(r.results.single().detail, r.results.single().detail.contains("key code 91"))
        assertEquals("the watch ends with the test", false, env.muteIsWatched)
    }

    @Test fun muteNotPressedEndsNotOfferedBecauseAndroidListedNothing() {
        env.locked = setOf(Feature.MUTE_BUTTON)
        val r = runner(input("in.button.mute", Feature.MUTE_BUTTON))
        r.start()
        clock = AppLimits.INPUT_TEST_TIMEOUT_MS
        r.tick()
        assertEquals(Outcome.NOT_OFFERED, r.results.single().outcome)
    }

    @Test fun anOutputThatThrewIsApiRejectedAndStillCleansUp() {
        env.answer = ApiAnswer.Failed("java.lang.IllegalStateException: boom")
        val r = runner(output("out.lightbar.red", RED, BLACK))
        r.start()
        val result = r.results.single()
        assertEquals(Outcome.API_REJECTED, result.outcome)
        assertTrue(result.detail.contains("boom"))
        assertEquals(listOf<OutputRequest>(RED, BLACK), env.sends)
    }

    @Test fun theDevelopersThreeAnswersMapToThreeOutcomes() {
        val cases = listOf(Confirm.YES to Outcome.PASSED, Confirm.NO to Outcome.ACCEPTED_NO_EFFECT, Confirm.CANT_TELL to Outcome.CANT_TELL)
        for ((tap, expected) in cases) {
            env.sends.clear()
            val r = runner(output("out.lightbar.red", RED, BLACK))
            r.start()
            assertEquals(Phase.WAITING_CONFIRM, r.phase)
            r.confirm(tap)
            assertEquals(tap.name, expected, r.results.single().outcome)
            assertEquals("the pad is put back after the answer", listOf<OutputRequest>(RED, BLACK), env.sends)
        }
    }

    @Test fun noTapWithinTheWaitIsCantTell() {
        val r = runner(output("out.lightbar.red", RED, BLACK))
        r.start()
        clock = AppLimits.CONFIRM_TAP_TIMEOUT_MS - 1
        r.tick()
        assertEquals(Phase.WAITING_CONFIRM, r.phase)
        clock = AppLimits.CONFIRM_TAP_TIMEOUT_MS
        r.tick()
        assertEquals(Outcome.CANT_TELL, r.results.single().outcome)
    }

    @Test fun aConfirmOutsideTheConfirmWaitChangesNothing() {
        val r = runner(input("in.button.cross"))
        r.start()
        r.confirm(Confirm.YES)
        assertEquals(emptyList<Pair<String, Outcome>>(), outcomes(r))
    }

    @Test fun theSwitchToDirectUsbAsksOnceAndLaterUsbTestsRunOnceItIsLive() {
        env.cameUp = true
        val r = runner(routeStep("route.direct_usb", Route.DIRECT_USB), input("in.button.cross", route = Route.DIRECT_USB))
        r.start()
        assertEquals(listOf(Route.DIRECT_USB), env.routeRequests)
        assertEquals(listOf("route.direct_usb" to Outcome.PASSED), outcomes(r).take(1))
        assertEquals("in.button.cross", r.current?.id)
    }

    @Test fun aSwitchThatNeverComesUpMakesEveryUsbTestRouteUnavailable() {
        env.cameUp = false
        val r = runner(routeStep("route.direct_usb", Route.DIRECT_USB), input("in.button.cross", route = Route.DIRECT_USB), output("out.lightbar.red", RED, route = Route.DIRECT_USB))
        r.start()
        assertEquals(Phase.WAITING_ROUTE, r.phase)
        clock = AppLimits.ROUTE_SWITCH_TIMEOUT_MS
        r.tick()
        assertEquals(
            listOf("route.direct_usb", "in.button.cross", "out.lightbar.red").map { it to Outcome.ROUTE_UNAVAILABLE },
            outcomes(r),
        )
        assertEquals("nothing was sent to a route that is not live", emptyList<OutputRequest>(), env.sends)
    }

    @Test fun anAndroidTestWhileTheRouteIsNotAndroidIsRouteUnavailable() {
        env.route = null
        val r = runner(input("in.button.cross"))
        r.start()
        assertEquals(Outcome.ROUTE_UNAVAILABLE, r.results.single().outcome)
    }

    @Test fun theWholeRunIsBoundedAndWhatWasNotReachedStaysNotProven() {
        val r = runner(input("in.button.cross"), input("in.button.circle"))
        r.start()
        clock = AppLimits.DEBUG_RUN_MS_MAX + 1
        r.tick()
        assertEquals(Phase.DONE, r.phase)
        assertEquals(listOf("in.button.cross" to Outcome.NOT_PROVEN, "in.button.circle" to Outcome.NOT_PROVEN), outcomes(r))
    }

    @Test fun skipRecordsSkippedAndMovesOn() {
        val r = runner(input("in.button.cross"), input("in.button.circle"))
        r.start()
        r.skip()
        assertEquals(listOf("in.button.cross" to Outcome.SKIPPED), outcomes(r))
        assertEquals("in.button.circle", r.current?.id)
    }

    @Test fun theLastRawReportsAndTheBytesSentAreKeptPerTestWithinTheirLimit() {
        val r = runner(input("in.button.cross"), output("out.lightbar.red", RED, BLACK))
        r.start()
        val raw = ByteArray(64) { 1 }
        r.onEvents((0 until AppLimits.RAW_REPORTS_PER_TEST_MAX + 10).map { FeedEvent(0L, Route.DIRECT_USB, raw.copyOf().also { b -> b[7] = it.toByte() }, null) })
        r.onEvents(listOf(held(Button.CROSS)))
        val first = r.results.single()
        assertEquals(AppLimits.RAW_REPORTS_PER_TEST_MAX, first.rawHex.size)
        assertTrue("the newest report is kept", first.rawHex.last().split(" ")[7] == "%02x".format(AppLimits.RAW_REPORTS_PER_TEST_MAX + 9))
        r.onEvents(listOf(FeedEvent(0L, Route.DIRECT_USB, ByteArray(63) { 2 }, "row", "out.lightbar")))
        assertEquals(1, r.current?.let { 1 } ?: 0)
        r.confirm(Confirm.YES)
        assertEquals(1, r.results[1].sentHex.size)
    }
}
