package dev.dsprobe.app.debug

import androidx.compose.runtime.snapshots.Snapshot
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class QuietEnv(var route: Route? = Route.ANDROID) : RunnerEnv {
    override fun currentRoute() = route
    override fun requestRoute(route: Route) = Unit
    override fun send(req: OutputRequest): ApiAnswer = ApiAnswer.Ok
    override fun isLocked(feature: Feature) = false
    override fun setMuteWatched(on: Boolean) = Unit
    override fun muteKeyCode(): Int? = null
}

private var now = 0L

private fun press(id: String, route: Route) =
    TestCase(id, route, Kind.INPUT, R.string.prompt_press, id, AppLimits.INPUT_TEST_TIMEOUT_MS, Check.AwaitInput(InputId(id)), feature = Feature.BUTTONS)

/** The "Run All Tests" tap showed nothing: the screen could not see the runner change. These read the runner the way a screen does. */
class DebugScreenStateTest {
    @Test fun startingARunChangesStateTheScreenReads() {
        now = 0L
        val runner = DebugRunner(listOf(press("in.button.cross", Route.ANDROID)), QuietEnv()) { now }
        val read = HashSet<Any>()
        val snapshot = Snapshot.takeSnapshot(readObserver = { read.add(it) })
        try {
            snapshot.enter {
                runner.phase
                runner.results
                runner.current
            }
        } finally {
            snapshot.dispose()
        }
        assertTrue("the screen reads no state at all", read.isNotEmpty())
        var changed: Set<Any> = emptySet()
        val handle = Snapshot.registerApplyObserver { set, _ -> changed = changed + set }
        try {
            runner.start()
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        assertEquals(Phase.WAITING_INPUT, runner.phase)
        assertTrue("start() changed nothing the screen reads", read.any { it in changed })
    }

    @Test fun aTestThatCannotRunSaysSoForAMomentThenTheNoteGoes() {
        now = 0L
        val runner = DebugRunner(listOf(press("in.button.cross", Route.DIRECT_USB), press("in.button.circle", Route.ANDROID)), QuietEnv()) { now }
        runner.start()
        val note = runner.notice
        assertNotNull(note)
        assertTrue(note, note!!.contains("in.button.cross"))
        now = AppLimits.NOTICE_SHOW_MS - 1
        runner.tick()
        assertNotNull(runner.notice)
        now = AppLimits.NOTICE_SHOW_MS
        runner.tick()
        assertNull(runner.notice)
    }
}
