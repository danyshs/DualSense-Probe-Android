package dev.dsprobe.app.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.dsprobe.app.AppLimits
import dev.dsprobe.pad.toHex

/** The answer to "Did the pad react?": Yes, No, Not sure. */
enum class Verdict { WORKS, DOESNT, NOT_SURE }

/**
 * One visited card: the answer (null verdict = none yet), the note, and every row seen while it was
 * open.
 */
data class CardAnswer(
    val cardId: String,
    val confidence: Confidence,
    val verdict: Verdict?,
    val note: String,
    val seen: List<RowLines> = emptyList(),
)

private class Entry(val card: EndpointCard) {
    var verdict: Verdict? = null
    var tried = false
    var note = ""
    val seen = ArrayList<RowLines>()
}

/**
 * Walks [cards] one at a time and never moves by itself: only [next] and [back] change the card.
 * While a
 * card is open it collects the trace rows of the controls it listens to ([observe]); a match marks
 * the card
 * seen and nothing more. Compose state, so the screen redraws on every change. Main thread only.
 */
class EndpointTour(val cards: List<EndpointCard>) {
    private var position by mutableIntStateOf(NOT_STARTED)
    private var version by mutableIntStateOf(0)
    private val entries = LinkedHashMap<String, Entry>()
    private var rangeBefore: ByteArray? = null

    val isRunning: Boolean get() = position in cards.indices

    val index: Int get() = position

    val current: EndpointCard? get() = cards.getOrNull(position)

    val number: Int get() = position + 1

    /** Everything visited so far, in card order, for the log. */
    val answers: List<CardAnswer> get() = entries.values.map {
        CardAnswer(it.card.id, it.card.confidence, it.verdict, it.note, it.seen.toList())
    }

    /** Rows seen on the open card, newest last. Reading it subscribes the screen. */
    val seenNow: List<RowLines> get() = entryOf(current)?.seen?.toList().orEmpty().also {
        check(version >= 0) { "version" }
    }

    val verdictNow: Verdict? get() = entryOf(current)?.verdict.also {
        check(version >= 0) { "version" }
    }

    /** True once Try it was pressed on the open card. Reading it subscribes the screen. */
    val triedNow: Boolean get() = (entryOf(current)?.tried ?: false).also {
        check(version >= 0) { "version" }
    }

    /** The "Did the pad react?" question shows only after Try it, and only until it is answered. */
    val reactionPending: Boolean get() = triedNow && verdictNow == null

    /**
     * Records that Try it was pressed on the open card, which is what lets the reaction question
     * appear.
     */
    fun markTried() {
        val entry = entryOf(current) ?: return
        if (entry.tried) return
        entry.tried = true
        version += 1
    }

    fun start() {
        entries.clear()
        open(0)
    }

    fun next() {
        if (isRunning) open(position + 1)
    }

    fun back() {
        if (isRunning && position > 0) open(position - 1)
    }

    fun stop() {
        position = cards.size
    }

    /** Records the answer on the open card. The tour stays on it. */
    fun answer(verdict: Verdict?, note: String) {
        val entry = entryOf(current) ?: return
        entry.verdict = verdict
        entry.note = note.take(AppLimits.TOUR_NOTE_CHARS_MAX)
        version += 1
    }

    /**
     * New trace rows and the newest input report: whatever matches the open card is kept under it.
     */
    fun observe(rows: List<TraceRow>, report: ByteArray?) {
        val card = current ?: return
        val entry = entryOf(card) ?: return
        var changed = false
        for (row in rows) {
            if (card.listens.none { controlUnder(row.control, it) }) continue
            if (entry.seen.size < AppLimits.TOUR_ROWS_PER_CARD_MAX) entry.seen.add(rowLines(row))
            changed = true
        }
        if (card.rawByte >= 0 && report != null && noteRange(card, entry, report)) changed = true
        if (changed) version += 1
    }

    // a free report range has no rows: its change is the raw bytes before and after
    private fun noteRange(card: EndpointCard, entry: Entry, report: ByteArray): Boolean {
        if (card.rawByte + card.rawByteCount > report.size) return false
        val now = rawBytes(card, report)
        val before = rangeBefore
        rangeBefore = now
        val full = entry.seen.size >= AppLimits.TOUR_ROWS_PER_CARD_MAX
        if (before == null || before.contentEquals(now) || full) {
            return false
        }
        entry.seen.add(
            RowLines(card.id + "  changed", "raw " + before.toHex() + " → " + now.toHex()),
        )
        return true
    }

    private fun open(target: Int) {
        position = target
        rangeBefore = null
        val card = current
        if (card != null && card.id !in entries) entries[card.id] = Entry(card)
        version += 1
        check(entries.size <= cards.size) { "more entries than cards" }
    }

    private fun entryOf(card: EndpointCard?): Entry? = card?.let { entries[it.id] }

    private companion object {
        const val NOT_STARTED = -1
    }
}

/** The masked bytes of a card's raw range in [report]. */
fun rawBytes(card: EndpointCard, report: ByteArray): ByteArray = ByteArray(card.rawByteCount) { i ->
    (report[card.rawByte + i].toInt() and card.rawMask).toByte()
}

/** The log's lines for the tour: one per visited card, then one per row seen under it. */
fun tourLines(answers: List<CardAnswer>): List<String> {
    val out = ArrayList<String>()
    for (a in answers) {
        out.add(
            "endpoint id=${a.cardId} confidence=${a.confidence} answer=${a.verdict ?: "none"} " +
                "seen=${a.seen.size} note=${a.note.ifEmpty { "-" }}",
        )
        for (row in a.seen) {
            out.add(
                "endpoint-seen id=${a.cardId} ${row.headline} | ${row.location}",
            )
        }
    }
    return out
}
