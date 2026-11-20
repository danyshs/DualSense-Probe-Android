package dev.dsprobe.app.output

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.PadRoute
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The route outputs go to right now. */
data class LiveRoute(val route: PadRoute, val kind: Route)

enum class AskKind { LIGHTBAR, PLAYER_LEDS, MUTE_LED }

/** What Android answered to a light request it may have silently ignored; the user is asked whether anything changed. */
data class PendingAsk(val kind: AskKind, val sent: String, val answer: ApiAnswer)

/** What the screen shows of the outputs: what was last sent, not something the pad confirmed. */
data class OutputsShown(
    val lightbar: Rgb,
    val playerLeds: Int,
    val muteLed: MuteLed,
    val rumbleLeft: Int,
    val rumbleRight: Int,
    val rumbleRemainingMs: Long,
    val lastAnswer: ApiAnswer?,
    val ask: PendingAsk?,
) {
    companion object {
        val IDLE = OutputsShown(Rgb(0u, 0u, 0u), 0, MuteLed.OFF, 0, 0, 0L, null, null)
    }
}

private fun describe(answer: ApiAnswer): String = when (answer) {
    ApiAnswer.Ok -> "Ok"
    is ApiAnswer.Failed -> "Failed(${answer.reason})"
}

/**
 * Clamps and time-limits every request, then hands it to the live route. Main thread only. A colour
 * is rate-limited and the newest wins: [tick] sends a held-back one once the interval is up.
 */
class OutputController(
    private val live: () -> LiveRoute?,
    private val nowMs: () -> Long,
    private val log: (String) -> Unit,
) {
    private val mutable = MutableStateFlow(OutputsShown.IDLE)
    val shown: StateFlow<OutputsShown> = mutable.asStateFlow()
    private var rumbleEndsAtMs = 0L
    private var heldColour: Rgb? = null
    private var lastColourSendMs: Long? = null

    /**
     * Strengths 0..255. A press gets one [Limits.RUMBLE_DURATION_MS_MAX] from its first non-zero value:
     * moving the slider doesn't extend it. Both at 0 stops now.
     */
    fun setRumble(left: Int, right: Int) {
        val l = left.coerceIn(0, Limits.MOTOR_STRENGTH_MAX)
        val r = right.coerceIn(0, Limits.MOTOR_STRENGTH_MAX)
        val now = nowMs()
        val stopped = l == 0 && r == 0
        val ends = when {
            stopped -> 0L
            rumbleEndsAtMs > now -> rumbleEndsAtMs
            else -> now + Limits.RUMBLE_DURATION_MS_MAX
        }
        check(stopped || ends - now in 1..Limits.RUMBLE_DURATION_MS_MAX.toLong()) { "rumble time left ${ends - now}" }
        val durationMs = if (stopped) 1 else (ends - now).toInt()
        val answer = send(OutputRequest.Rumble(l.toUByte(), r.toUByte(), durationMs))
        rumbleEndsAtMs = ends
        mutable.value = mutable.value.copy(rumbleLeft = l, rumbleRight = r, rumbleRemainingMs = remainingMs(now), lastAnswer = answer)
    }

    private fun remainingMs(now: Long): Long = (rumbleEndsAtMs - now).coerceAtLeast(0L)

    /** The colour to show now; sent at once when the interval allows, else held and sent by [tick]. */
    fun setLightbar(rgb: Rgb) {
        heldColour = rgb
        sendHeldColour(force = false)
    }

    /** A drag ended or a field was committed: the newest colour goes out now, whatever the interval. */
    fun flushLightbar() = sendHeldColour(force = true)

    /** Once per frame: sends a held colour whose interval is up and keeps the rumble countdown current. */
    fun tick() {
        sendHeldColour(force = false)
        val now = nowMs()
        val remaining = remainingMs(now)
        val current = mutable.value
        if (current.rumbleRemainingMs == remaining) return
        val finished = remaining == 0L
        mutable.value = current.copy(
            rumbleRemainingMs = remaining,
            rumbleLeft = if (finished) 0 else current.rumbleLeft,
            rumbleRight = if (finished) 0 else current.rumbleRight,
        )
    }

    private fun sendHeldColour(force: Boolean) {
        val rgb = heldColour ?: return
        val now = nowMs()
        val last = lastColourSendMs
        if (!force && last != null && now - last < AppLimits.COLOUR_SEND_INTERVAL_MS_MIN) return
        heldColour = null
        lastColourSendMs = now
        val answer = send(OutputRequest.Lightbar(rgb))
        val sent = "lightbar %02x%02x%02x".format(rgb.r.toInt(), rgb.g.toInt(), rgb.b.toInt())
        mutable.value = mutable.value.copy(lightbar = rgb, lastAnswer = answer, ask = askFor(AskKind.LIGHTBAR, sent, answer))
    }

    fun setPlayerLeds(mask: Int) {
        val clamped = mask.coerceIn(0, Limits.PLAYER_LED_MASK_MAX)
        val answer = send(OutputRequest.PlayerLeds(clamped))
        val sent = "player LEDs %02x".format(clamped)
        mutable.value = mutable.value.copy(playerLeds = clamped, lastAnswer = answer, ask = askFor(AskKind.PLAYER_LEDS, sent, answer))
    }

    fun setMuteLed(mode: MuteLed) {
        val answer = send(OutputRequest.SetMuteLed(mode))
        mutable.value = mutable.value.copy(muteLed = mode, lastAnswer = answer, ask = askFor(AskKind.MUTE_LED, "mute LED $mode", answer))
    }

    /** The user's yes or no to "Did it change?": logged next to what Android answered, then the question goes away. */
    fun answerChanged(changed: Boolean) {
        val ask = mutable.value.ask ?: return
        log("${ask.sent}: Android answered ${describe(ask.answer)}; did it change? ${if (changed) "yes" else "no"}")
        mutable.value = mutable.value.copy(ask = null)
    }

    /** Back to nothing sent: a route began or ended, so the old swatch and sliders mean nothing. */
    fun reset() {
        mutable.value = OutputsShown.IDLE
        rumbleEndsAtMs = 0L
        heldColour = null
        lastColourSendMs = null
    }

    // Android can't confirm lights, so every light request on it asks the user; Direct USB shows its effect on the pad
    private fun askFor(kind: AskKind, sent: String, answer: ApiAnswer): PendingAsk? =
        if (live()?.kind == Route.ANDROID) PendingAsk(kind, sent, answer) else null

    private fun send(req: OutputRequest): ApiAnswer {
        val route = live() ?: return ApiAnswer.Failed("no pad is live")
        val answer = route.route.send(req)
        if (answer is ApiAnswer.Failed) log("output refused: ${answer.reason}")
        return answer
    }
}
