package dev.dsprobe.app.output

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.MotorLevels
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.PadRoute
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.totalDurationMs
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb
import dev.dsprobe.pad.RumbleMode
import dev.dsprobe.pad.TriggerEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The route outputs go to right now. */
data class LiveRoute(val route: PadRoute, val kind: Route)

enum class AskKind { LIGHTBAR, PLAYER_LEDS, MUTE_LED, RUMBLE }

/**
 * What Android answered to a light request it may have silently ignored; the user is asked whether
 * anything changed.
 */
data class PendingAsk(val kind: AskKind, val sent: String, val answer: ApiAnswer)

/**
 * One trigger's resistance as the panel shows it: [on] while the effect is live, with [remainingMs]
 * left.
 */
data class TriggerShown(val on: Boolean, val start: Int, val force: Int, val remainingMs: Long) {
    companion object {
        val IDLE =
            TriggerShown(false, AppLimits.TRIGGER_START_DEFAULT, Limits.TRIGGER_FORCE_MAX, 0L)
    }
}

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
    /** The flags the route rumbles with; null where the route has no modes (Android). */
    val rumbleMode: RumbleMode? = null,
    /** The motor values the route reports sending, newest last, for the two-trace strip. */
    val rumbleTrace: List<MotorLevels> = emptyList(),
    val r2: TriggerShown = TriggerShown.IDLE,
    val l2: TriggerShown = TriggerShown.IDLE,
) {
    companion object {
        val IDLE = OutputsShown(Rgb(0u, 0u, 0u), 0, MuteLed.OFF, 0, 0, 0L, null, null)
    }
}

/** [trace] with [level] added: only while something is moving, bounded at the limit. */
internal fun traceWith(trace: List<MotorLevels>, level: MotorLevels): List<MotorLevels> {
    if (level == MotorLevels.OFF && (trace.isEmpty() || trace.last() == MotorLevels.OFF)) {
        return trace
    }
    val longer = trace + level
    return if (longer.size > AppLimits.RUMBLE_TRACE_SAMPLES_MAX) {
        longer.drop(longer.size - AppLimits.RUMBLE_TRACE_SAMPLES_MAX)
    } else {
        longer
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
    private val onAnswered: (PendingAsk, Boolean) -> Unit = { _, _ -> },
) {
    private val mutable = MutableStateFlow(OutputsShown.IDLE)
    val shown: StateFlow<OutputsShown> = mutable.asStateFlow()
    private var rumbleEndsAtMs = 0L
    private var heldColour: Rgb? = null
    private var lastColourSendMs: Long? = null
    private var rightTriggerEndsAtMs = 0L
    private var leftTriggerEndsAtMs = 0L

    /**
     * Strengths 0..255. A press gets one [Limits.RUMBLE_DURATION_MS_MAX] from its first non-zero
     * value:
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
        check(stopped || ends - now in 1..Limits.RUMBLE_DURATION_MS_MAX.toLong()) {
            "rumble time left ${ends - now}"
        }
        val durationMs = if (stopped) 1 else (ends - now).toInt()
        val answer = send(OutputRequest.Rumble(l.toUByte(), r.toUByte(), durationMs))
        rumbleEndsAtMs = ends
        mutable.value =
            mutable.value.copy(
                rumbleLeft = l,
                rumbleRight = r,
                rumbleRemainingMs = remainingMs(now),
                lastAnswer = answer,
            )
    }

    private fun remainingMs(now: Long): Long = (rumbleEndsAtMs - now).coerceAtLeast(0L)

    /**
     * Plays pattern [id] for [durationMs], each motor up to its ceiling; it ends by itself inside
     * the route.
     */
    fun playPattern(id: PatternId, leftMax: Int, rightMax: Int, durationMs: Int, rateHz: Int) {
        val steps =
            buildPattern(
                id,
                leftMax.coerceIn(0, Limits.MOTOR_STRENGTH_MAX),
                rightMax.coerceIn(0, Limits.MOTOR_STRENGTH_MAX),
                durationMs,
                rateHz,
            )
        val totalMs = totalDurationMs(steps)
        val answer = send(OutputRequest.RumblePattern(steps))
        rumbleEndsAtMs = nowMs() + totalMs
        val ask = askFor(AskKind.RUMBLE, "rumble ${id.name.lowercase().replace('_', ' ')}", answer)
        mutable.value = mutable.value.copy(
            rumbleLeft = steps[0].left,
            rumbleRight = steps[0].right,
            rumbleRemainingMs = totalMs.toLong(),
            lastAnswer = answer,
            ask = ask,
        )
    }

    /** Picks the Direct USB rumble flags. A rumble running in the old flags is stopped first. */
    fun setRumbleMode(mode: RumbleMode) {
        if (remainingMs(nowMs()) > 0L) setRumble(0, 0)
        val answer = send(OutputRequest.SetRumbleMode(mode))
        mutable.value =
            mutable.value.copy(
                rumbleMode = if (answer == ApiAnswer.Ok) {
                    mode
                } else {
                    mutable.value.rumbleMode
                },
                lastAnswer = answer,
            )
    }

    // what the route says it sent, once per frame; a pattern shows its moving values here
    private fun sampleRumble(now: Long) {
        val route = live()?.route ?: return
        val levels = route.rumbleSent()
        val current = mutable.value
        var next = current.copy(rumbleMode = route.rumbleMode)
        if (levels != null) {
            next = next.copy(rumbleTrace = traceWith(current.rumbleTrace, levels))
            if (rumbleEndsAtMs > now) {
                next = next.copy(rumbleLeft = levels.left, rumbleRight = levels.right)
            }
        }
        if (next != current) mutable.value = next
    }

    private fun triggerShown(isRight: Boolean): TriggerShown =
        if (isRight) mutable.value.r2 else mutable.value.l2

    private fun triggerEndsAtMs(isRight: Boolean): Long =
        if (isRight) rightTriggerEndsAtMs else leftTriggerEndsAtMs

    private fun setTriggerEnds(isRight: Boolean, endsAtMs: Long) {
        if (isRight) rightTriggerEndsAtMs = endsAtMs else leftTriggerEndsAtMs = endsAtMs
    }

    private fun showTrigger(isRight: Boolean, shown: TriggerShown, answer: ApiAnswer?) {
        val current = mutable.value
        val next = if (isRight) current.copy(r2 = shown) else current.copy(l2 = shown)
        mutable.value = if (answer == null) next else next.copy(lastAnswer = answer)
    }

    /**
     * Moves a trigger's sliders. While its resistance is live the new values go out at once and the
     * 30 s limit keeps counting from the press; otherwise they are only remembered for the next
     * press.
     */
    fun setTriggerLevels(isRight: Boolean, start: Int, force: Int) {
        val s = start.coerceIn(0, AppLimits.TRIGGER_START_MAX)
        val f = force.coerceIn(0, Limits.TRIGGER_FORCE_MAX)
        val now = nowMs()
        val current = triggerShown(isRight)
        if (!current.on || triggerEndsAtMs(isRight) <= now) {
            showTrigger(isRight, current.copy(start = s, force = f), null)
            return
        }
        sendResistance(isRight, s, f, now)
    }

    /**
     * Starts the resistance with the sliders' values: one [Limits.TRIGGER_EFFECT_DURATION_MS_MAX]
     * from this press.
     */
    fun pressTrigger(isRight: Boolean) {
        val now = nowMs()
        val current = triggerShown(isRight)
        setTriggerEnds(isRight, now + Limits.TRIGGER_EFFECT_DURATION_MS_MAX)
        sendResistance(isRight, current.start, current.force, now)
    }

    /** Sends the effect off now. */
    fun clearTrigger(isRight: Boolean) {
        val answer = send(OutputRequest.Trigger(isRight, TriggerEffect.OFF, 1))
        setTriggerEnds(isRight, 0L)
        showTrigger(isRight, triggerShown(isRight).copy(on = false, remainingMs = 0L), answer)
    }

    private fun sendResistance(isRight: Boolean, start: Int, force: Int, now: Long) {
        val remainingMs = triggerEndsAtMs(isRight) - now
        check(remainingMs in 1..Limits.TRIGGER_EFFECT_DURATION_MS_MAX.toLong()) {
            "trigger time left $remainingMs"
        }
        check(start in 0..AppLimits.TRIGGER_START_MAX && force in 0..Limits.TRIGGER_FORCE_MAX) {
            "trigger levels $start $force"
        }
        val effect = TriggerEffect.resistance(start.toUByte(), force.toUByte())
        val answer = send(OutputRequest.Trigger(isRight, effect, remainingMs.toInt()))
        val taken = answer == ApiAnswer.Ok
        if (!taken) setTriggerEnds(isRight, 0L)
        showTrigger(
            isRight,
            TriggerShown(taken, start, force, if (taken) remainingMs else 0L),
            answer,
        )
    }

    // the writer clears an expired effect by itself; this only brings the panel into line
    private fun tickTriggers(now: Long) {
        for (isRight in listOf(true, false)) {
            val current = triggerShown(isRight)
            if (!current.on) continue
            val remainingMs = (triggerEndsAtMs(isRight) - now).coerceAtLeast(0L)
            val next = if (remainingMs == 0L) {
                current.copy(on = false, remainingMs = 0L)
            } else {
                current.copy(remainingMs = remainingMs)
            }
            if (next != current) showTrigger(isRight, next, null)
        }
    }

    /**
     * The colour to show now; sent at once when the interval allows, else held and sent by [tick].
     */
    fun setLightbar(rgb: Rgb) {
        heldColour = rgb
        sendHeldColour(force = false)
    }

    /**
     * A drag ended or a field was committed: the newest colour goes out now, whatever the interval.
     */
    fun flushLightbar() = sendHeldColour(force = true)

    /**
     * Once per frame: sends a held colour whose interval is up and keeps the rumble countdown
     * current.
     */
    fun tick() {
        sendHeldColour(force = false)
        val now = nowMs()
        sampleRumble(now)
        tickTriggers(now)
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
        mutable.value =
            mutable.value.copy(
                lightbar = rgb,
                lastAnswer = answer,
                ask = askFor(AskKind.LIGHTBAR, sent, answer),
            )
    }

    fun setPlayerLeds(mask: Int) {
        val clamped = mask.coerceIn(0, Limits.PLAYER_LED_MASK_MAX)
        val answer = send(OutputRequest.PlayerLeds(clamped))
        val sent = "player LEDs %02x".format(clamped)
        mutable.value =
            mutable.value.copy(
                playerLeds = clamped,
                lastAnswer = answer,
                ask = askFor(AskKind.PLAYER_LEDS, sent, answer),
            )
    }

    fun setMuteLed(mode: MuteLed) {
        val answer = send(OutputRequest.SetMuteLed(mode))
        mutable.value =
            mutable.value.copy(
                muteLed = mode,
                lastAnswer = answer,
                ask = askFor(AskKind.MUTE_LED, "mute LED $mode", answer),
            )
    }

    /** "Not sure": the question goes away and nothing is logged as an answer. */
    fun dismissAsk() {
        if (mutable.value.ask != null) mutable.value = mutable.value.copy(ask = null)
    }

    /**
     * The user's yes or no to "Did it change?": logged next to what Android answered, then the
     * question goes away.
     */
    fun answerChanged(changed: Boolean) {
        val ask = mutable.value.ask ?: return
        log(
            "${ask.sent}: Android answered ${describe(
                ask.answer,
            )}; did it change? ${if (changed) "yes" else "no"}",
        )
        onAnswered(ask, changed)
        mutable.value = mutable.value.copy(ask = null)
    }

    /** Back to nothing sent: a route began or ended, so the old swatch and sliders mean nothing. */
    fun reset() {
        mutable.value = OutputsShown.IDLE
        rumbleEndsAtMs = 0L
        heldColour = null
        lastColourSendMs = null
        rightTriggerEndsAtMs = 0L
        leftTriggerEndsAtMs = 0L
    }

    // Android can't confirm lights, so every light request on it asks the user; Direct USB shows
    // its effect on the pad
    private fun askFor(kind: AskKind, sent: String, answer: ApiAnswer): PendingAsk? =
        if (live()?.kind == Route.ANDROID) PendingAsk(kind, sent, answer) else null

    private fun send(req: OutputRequest): ApiAnswer {
        val route = live() ?: return ApiAnswer.Failed("no pad is live")
        val answer = route.route.send(req)
        if (answer is ApiAnswer.Failed) log("output refused: ${answer.reason}")
        return answer
    }
}
