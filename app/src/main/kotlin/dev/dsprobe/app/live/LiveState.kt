package dev.dsprobe.app.live

import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** What the screen draws for one frame: the newest state plus every press seen since the last frame. */
data class Frame(val state: PadState, val pressedSinceLast: Set<Button>, val dpadSinceLast: Set<Dpad>) {
    companion object {
        val EMPTY = Frame(PadState.EMPTY, emptySet(), emptySet())
    }
}

private val BUTTONS = Button.entries
private val DPAD_DIRECTIONS = Dpad.entries.filter { it != Dpad.NONE }

// 15 buttons then 8 directions in one int
private const val DPAD_BIT_BASE = 15

private fun bitsOf(s: PadState): Int {
    var bits = 0
    for (b in s.held) bits = bits or (1 shl b.ordinal)
    if (s.dpad != Dpad.NONE) bits = bits or (1 shl (DPAD_BIT_BASE + s.dpad.ordinal))
    return bits
}

/**
 * Newest state wins, plus a latch bit per button and D-pad direction that catches a press shorter
 * than one frame. Nothing queues, so nothing overflows. One writer per route, checked on publish.
 */
class LiveState {
    private val state = AtomicReference(PadState.EMPTY)
    private val latched = AtomicInteger(0)

    @Volatile
    private var writer: Thread? = null

    init {
        check(BUTTONS.size <= DPAD_BIT_BASE) { "button bits overlap the D-pad bits" }
        check(DPAD_BIT_BASE + Dpad.NONE.ordinal <= Int.SIZE_BITS) { "latch does not fit one int" }
    }

    /** The route that is about to publish names its thread; null while no route is live. */
    fun setWriter(thread: Thread?) {
        writer = thread
    }

    fun publish(s: PadState) {
        check(writer === Thread.currentThread()) { "publish from a thread that is not the route's writer" }
        state.set(s)
        latched.getAndUpdate { it or bitsOf(s) }
    }

    fun takeFrame(): Frame {
        val bits = latched.getAndSet(0)
        val current = state.get()
        val buttons = BUTTONS.filterTo(HashSet()) { bits and (1 shl it.ordinal) != 0 }
        val dirs = DPAD_DIRECTIONS.filterTo(HashSet()) { bits and (1 shl (DPAD_BIT_BASE + it.ordinal)) != 0 }
        return Frame(current, buttons, dirs)
    }

    /** Back to an empty pad, for a route change. Any thread. */
    fun reset() {
        state.set(PadState.EMPTY)
        latched.set(0)
    }
}
