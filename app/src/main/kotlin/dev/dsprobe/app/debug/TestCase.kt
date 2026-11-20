package dev.dsprobe.app.debug

import androidx.annotation.StringRes
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.Route

enum class Kind { INPUT, OUTPUT, ROUTE }

/** One per test, every test starting at [NOT_PROVEN]. */
enum class Outcome {
    NOT_PROVEN, PASSED, NOT_SEEN, CROSS_TALK, NOT_OFFERED, API_REJECTED, ACCEPTED_NO_EFFECT, CANT_TELL, ROUTE_UNAVAILABLE, SKIPPED,
}

/** The ID of an input control, such as in.button.triangle. */
@JvmInline
value class InputId(val id: String)

sealed interface Check {
    /** Passes on the first event that matches the control's rule (see [InputMatcher]). */
    data class AwaitInput(val id: InputId) : Check

    /** Sends [request]; the developer says whether the pad did it; [cleanup] puts it back. */
    data class SendOutput(val request: OutputRequest, val cleanup: OutputRequest?) : Check

    /** Asks for the route and waits until it is live. */
    data class AwaitRoute(val route: Route) : Check
}

/**
 * One Debug Mode test. [promptRes] is a format string and [promptArg] its one argument.
 * [companions] are input IDs (matched by prefix) that move together with the target and are not cross-talk.
 * [feature] is the Android mode row that locks the test: a locked feature is NOT_OFFERED without a prompt.
 */
data class TestCase(
    val id: String,
    val route: Route,
    val kind: Kind,
    @param:StringRes val promptRes: Int,
    val promptArg: String,
    val timeoutMs: Long,
    val check: Check,
    val companions: Set<InputId> = emptySet(),
    val feature: Feature? = null,
)

/** What the run recorded for one test: the outcome, a one-line detail, and the bytes around it. */
data class TestResult(
    val id: String,
    val route: Route,
    val outcome: Outcome,
    val detail: String,
    val rawHex: List<String> = emptyList(),
    val sentHex: List<String> = emptyList(),
)
