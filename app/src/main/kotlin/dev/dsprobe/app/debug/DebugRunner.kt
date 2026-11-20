package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.toHex

/** What the runner needs from the app; PadSession implements it, tests fake it. */
interface RunnerEnv {
    fun currentRoute(): Route?
    fun requestRoute(route: Route)
    fun send(req: OutputRequest): ApiAnswer

    /** True when Android mode's capability rules lock [feature] right now. */
    fun isLocked(feature: Feature): Boolean

    /** While true, a key event that maps to no pad button is taken as the Mute key. */
    fun setMuteWatched(on: Boolean)

    /** The key code learned while the Mute prompt was up, if any. */
    fun muteKeyCode(): Int?
}

enum class Phase { IDLE, WAITING_INPUT, WAITING_CONFIRM, WAITING_ROUTE, DONE }

enum class Confirm { YES, NO, CANT_TELL }

private const val MUTE_ID = "in.button.mute"

/**
 * Walks the catalogue one test at a time. It never blocks: the screen calls [onEvents] with what
 * the feed delivered and [tick] once per frame. The whole run is bounded by DEBUG_RUN_MS_MAX.
 */
class DebugRunner(
    private val tests: List<TestCase>,
    private val env: RunnerEnv,
    private val nowMs: () -> Long,
) {
    private val done = ArrayList<TestResult>()
    private var index = -1
    private var startedMs = 0L
    private var deadlineMs = 0L
    private var matcher: InputMatcher? = null
    private var baseline: Map<String, Float> = emptyMap()
    private var latest: Map<String, Float> = emptyMap()
    private val raw = ArrayList<String>()
    private val sent = ArrayList<String>()
    private var directUsbFailed = false

    var phase = Phase.IDLE
        private set

    /** Goes up on every change the screen shows, so the screen recomposes only then. */
    var revision = 0L
        private set

    val results: List<TestResult> get() = done.toList()

    val current: TestCase? get() = if (phase == Phase.IDLE || phase == Phase.DONE) null else tests.getOrNull(index)

    val testCount: Int get() = tests.size

    /** Every test starts as NOT_PROVEN; a run walks them. A second start while running changes nothing. */
    fun start() {
        if (phase != Phase.IDLE && phase != Phase.DONE) return
        done.clear()
        index = -1
        directUsbFailed = false
        startedMs = nowMs()
        startNext()
    }

    /** The developer stops the run: every test not reached stays NOT_PROVEN. */
    fun stop() {
        if (phase == Phase.IDLE || phase == Phase.DONE) return
        current?.let { record(it, Outcome.SKIPPED, "run stopped during this test") }
        finishRun()
    }

    /** Skip the test on screen. */
    fun skip() {
        val test = current ?: return
        finishTest(test, Outcome.SKIPPED, "skipped by the developer")
    }

    fun confirm(answer: Confirm) {
        val test = current ?: return
        if (phase != Phase.WAITING_CONFIRM) return
        when (answer) {
            Confirm.YES -> finishTest(test, Outcome.PASSED, "API ok, developer saw or felt it")
            Confirm.NO -> finishTest(test, Outcome.ACCEPTED_NO_EFFECT, "API ok, developer saw or felt nothing")
            Confirm.CANT_TELL -> finishTest(test, Outcome.CANT_TELL, "developer could not tell")
        }
    }

    /** Feed events since the last call: states for matching, bytes for the log. */
    fun onEvents(events: List<FeedEvent>) {
        for (event in events) {
            event.state?.let { latest = samplesOf(it) }
            if (phase == Phase.IDLE || phase == Phase.DONE) continue
            noteBytes(event)
            if (phase == Phase.WAITING_INPUT && event.state != null) judgeInput()
        }
    }

    private fun noteBytes(event: FeedEvent) {
        val bytes = event.raw ?: return
        val isOutput = event.control?.startsWith("out.") == true
        if (isOutput) {
            if (sent.size < AppLimits.RAW_REPORTS_PER_TEST_MAX) sent.add(bytes.toHex())
            return
        }
        // the newest reports are kept, so the report that matched is among them
        if (raw.size >= AppLimits.RAW_REPORTS_PER_TEST_MAX) raw.removeAt(0)
        raw.add(bytes.toHex())
    }

    private fun judgeInput() {
        val test = current ?: return
        val check = test.check as? Check.AwaitInput ?: return
        if (matcher?.accept(latest) == true) {
            finishTest(test, Outcome.PASSED, "matching event seen")
            return
        }
        // a test that began before any state arrived takes its first state as the resting one
        if (baseline.isEmpty()) baseline = latest
        val culprit = crossTalkCulprit(baseline, latest, check.id, test.companions) ?: return
        finishTest(test, Outcome.CROSS_TALK, "$culprit moved first")
    }

    /** Once per frame: timeouts, the route wait, and the Mute watch. */
    fun tick() {
        val test = current ?: return
        val now = nowMs()
        if (now - startedMs > AppLimits.DEBUG_RUN_MS_MAX) {
            finishRun()
            return
        }
        when (phase) {
            Phase.WAITING_INPUT -> tickInput(test, now)
            Phase.WAITING_CONFIRM -> if (now >= deadlineMs) finishTest(test, Outcome.CANT_TELL, "no tap within the wait")
            Phase.WAITING_ROUTE -> tickRoute(test, now)
            Phase.IDLE, Phase.DONE -> Unit
        }
    }

    private fun tickInput(test: TestCase, now: Long) {
        val muteKey = env.muteKeyCode()
        if (test.feature == Feature.MUTE_BUTTON && test.route == Route.ANDROID && muteKey != null) {
            finishTest(test, Outcome.PASSED, "Android sent key code $muteKey while Mute was pressed: Mute is offered")
            return
        }
        if (now < deadlineMs) return
        // a feature Android lists nothing for is not "not seen": nothing was offered to see
        val offered = test.route != Route.ANDROID || test.feature == null || !env.isLocked(test.feature)
        if (offered) finishTest(test, Outcome.NOT_SEEN, "no matching event in time") else finishTest(test, Outcome.NOT_OFFERED, "Android offers no API for this")
    }

    private fun tickRoute(test: TestCase, now: Long) {
        val want = (test.check as Check.AwaitRoute).route
        if (env.currentRoute() == want) {
            finishTest(test, Outcome.PASSED, "route live")
        } else if (now >= deadlineMs) {
            if (want == Route.DIRECT_USB) directUsbFailed = true
            finishTest(test, Outcome.ROUTE_UNAVAILABLE, "route did not come up in time")
        }
    }

    // starts tests until one is left waiting; a loop, so a long run of instant results never nests
    private fun startNext() {
        while (true) {
            index += 1
            if (index >= tests.size) {
                finishRun()
                return
            }
            if (begin(tests[index])) return
        }
    }

    /** True when the test is now waiting; false when it settled on the spot (its result is recorded). */
    private fun begin(test: TestCase): Boolean {
        raw.clear()
        sent.clear()
        val check = test.check
        if (check is Check.AwaitRoute) return beginRoute(test, check.route)
        if (test.route != env.currentRoute() || (test.route == Route.DIRECT_USB && directUsbFailed)) {
            record(test, Outcome.ROUTE_UNAVAILABLE, "route ${test.route} is not live")
            return false
        }
        val locked = test.route == Route.ANDROID && test.feature != null && env.isLocked(test.feature)
        // Mute is still prompted while locked: a key event is how it would unlock
        if (locked && test.id != MUTE_ID) {
            record(test, Outcome.NOT_OFFERED, "Android offers no API for this")
            return false
        }
        return when (check) {
            is Check.AwaitInput -> beginInput(test, check)
            is Check.SendOutput -> beginOutput(test, check)
            is Check.AwaitRoute -> false
        }
    }

    private fun beginRoute(test: TestCase, route: Route): Boolean {
        if (env.currentRoute() == route) {
            record(test, Outcome.PASSED, "route already live")
            return false
        }
        env.requestRoute(route)
        if (env.currentRoute() == route) {
            record(test, Outcome.PASSED, "route came up at once")
            return false
        }
        deadlineMs = nowMs() + (if (route == Route.DIRECT_USB) AppLimits.ROUTE_SWITCH_TIMEOUT_MS else AppLimits.ANDROID_REBIND_TIMEOUT_MS)
        enter(Phase.WAITING_ROUTE)
        return true
    }

    private fun beginInput(test: TestCase, check: Check.AwaitInput): Boolean {
        matcher = InputMatcher(check.id)
        baseline = latest
        deadlineMs = nowMs() + test.timeoutMs
        enter(Phase.WAITING_INPUT)
        if (test.feature == Feature.MUTE_BUTTON) env.setMuteWatched(true)
        return true
    }

    private fun beginOutput(test: TestCase, check: Check.SendOutput): Boolean {
        val answer = env.send(check.request)
        if (answer is ApiAnswer.Failed) {
            check.cleanup?.let { env.send(it) }
            record(test, Outcome.API_REJECTED, answer.reason)
            return false
        }
        deadlineMs = nowMs() + AppLimits.CONFIRM_TAP_TIMEOUT_MS
        enter(Phase.WAITING_CONFIRM)
        return true
    }

    private fun finishTest(test: TestCase, outcome: Outcome, detail: String) {
        (test.check as? Check.SendOutput)?.cleanup?.let { env.send(it) }
        if (test.feature == Feature.MUTE_BUTTON) env.setMuteWatched(false)
        record(test, outcome, detail)
        startNext()
    }

    private fun enter(next: Phase) {
        phase = next
        revision += 1
    }

    private fun record(test: TestCase, outcome: Outcome, detail: String) {
        done.add(TestResult(test.id, test.route, outcome, detail.take(AppLimits.RESULT_DETAIL_CHARS_MAX), raw.toList(), sent.toList()))
        check(done.size <= tests.size) { "more results than tests" }
        matcher = null
        revision += 1
    }

    private fun finishRun() {
        // anything not reached stays NOT_PROVEN
        for (i in (done.size) until tests.size) {
            val t = tests[i]
            done.add(TestResult(t.id, t.route, Outcome.NOT_PROVEN, "never reached", emptyList(), emptyList()))
        }
        env.setMuteWatched(false)
        enter(Phase.DONE)
    }
}
