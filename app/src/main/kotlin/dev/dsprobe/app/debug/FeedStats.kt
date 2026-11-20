package dev.dsprobe.app.debug

import dev.dsprobe.app.live.FeedEvent

private const val NANOS_PER_MS = 1_000_000L
private const val SEQUENCE_BYTE = 7
private const val SEQUENCE_MODULUS = 256

/**
 * The footer's numbers, counted from the feed while a subscriber is open: reports received, reports
 * dropped (from the 8-bit counter in byte 7, modulo 256) and the longest gap between reports.
 */
class FeedStats {
    var reportsReceived = 0L
        private set
    var reportsDropped = 0L
        private set
    var longestGapMs = 0L
        private set
    private var lastSequence = -1
    private var lastNs = 0L

    fun ingest(events: List<FeedEvent>) {
        for (event in events) {
            val raw = event.raw
            if (event.control != null || raw == null || raw.size <= SEQUENCE_BYTE) continue
            count(raw[SEQUENCE_BYTE].toInt() and 0xff, event.timeNs)
        }
    }

    private fun count(sequence: Int, timeNs: Long) {
        reportsReceived += 1
        if (lastSequence >= 0 && sequence != lastSequence) {
            reportsDropped += ((sequence - lastSequence - 1) % SEQUENCE_MODULUS + SEQUENCE_MODULUS) % SEQUENCE_MODULUS
        }
        if (lastNs != 0L) longestGapMs = maxOf(longestGapMs, (timeNs - lastNs) / NANOS_PER_MS)
        lastSequence = sequence
        lastNs = timeNs
    }
}
