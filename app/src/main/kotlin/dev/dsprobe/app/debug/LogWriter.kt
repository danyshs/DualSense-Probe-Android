package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.AndroidAnswers
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.answerLines

const val LOG_FORMAT_LINE = "dsprobe-log 1"

/** The command a reporter runs to capture the denials the app cannot read itself (lights and battery on USB). */
const val LOGCAT_COMMAND = "adb logcat -d -b all | grep -E \"avc: +denied.*scontext=u:r:system_server\""

private const val TRUNCATED_LINE = "truncated=true"
private const val ASSUMES_COUNTER_NOTE = "assumes byte 7 is a counter"

/** Byte caps for one log: [bodyBytesMax] for everything but the footer, [totalBytesMax] for all of it. */
data class LogCaps(val totalBytesMax: Int, val footerBytesReserved: Int) {
    init {
        require(footerBytesReserved in 1 until totalBytesMax) { "footer reserve ${footerBytesReserved} of ${totalBytesMax}" }
    }

    val bodyBytesMax: Int get() = totalBytesMax - footerBytesReserved

    companion object {
        val DEFAULT = LogCaps(AppLimits.LOG_BYTES_MAX, AppLimits.LOG_FOOTER_BYTES_RESERVED)
    }
}

/** What the footer reports, counted outside the log. */
data class LogFooter(
    val reportsReceived: Long,
    val reportsDropped: Long,
    val longestGapMs: Long,
    val feedOverflow: Long,
    val axisRowsSkipped: Long,
    val lateEventsDropped: Int,
)

/** Everything one log holds. [header] is key=value pairs in order. */
class LogInput(
    val header: List<Pair<String, String>>,
    val answers: AndroidAnswers?,
    val results: List<TestResult>,
    val trace: List<TraceRow>,
    val crashRecord: String?,
    val footer: LogFooter,
    val tourAnswers: List<CardAnswer> = emptyList(),
)

/**
 * Collects lines under a byte cap. Every line is scrubbed of Bluetooth addresses, kept to one line and
 * cut at the line limit. Past the body cap the lines stop and one truncated line is written in the
 * space reserved for the footer.
 */
class CappedLog(private val caps: LogCaps) {
    private val text = StringBuilder()
    private var bytes = 0
    var truncated = false
        private set

    private fun clean(line: String): String = scrubAddresses(line.replace('\n', ' ').replace('\r', ' ')).take(AppLimits.LOG_LINE_CHARS_MAX)

    /** True when the line fit. After the first one that does not, nothing more is taken. */
    fun line(raw: String): Boolean {
        if (truncated) return false
        val cleaned = clean(raw)
        val size = cleaned.toByteArray(Charsets.UTF_8).size + 1
        if (bytes + size > caps.bodyBytesMax) {
            truncated = true
            return false
        }
        text.append(cleaned).append('\n')
        bytes += size
        return true
    }

    /** Footer lines may use the reserved space; the cap on the whole file holds. */
    fun footerLine(raw: String) {
        val cleaned = clean(raw)
        val size = cleaned.toByteArray(Charsets.UTF_8).size + 1
        check(bytes + size <= caps.totalBytesMax) { "footer over the log cap" }
        text.append(cleaned).append('\n')
        bytes += size
    }

    fun finish(): String = text.toString()

    val byteCount: Int get() = bytes
}

private fun testLines(results: List<TestResult>): List<String> {
    val out = ArrayList<String>()
    for (r in results) {
        out.add("test id=${r.id} route=${routeTag(r.route)} outcome=${r.outcome} detail=${r.detail}")
        for (hex in r.sentHex) out.add("sent test=${r.id} route=${routeTag(r.route)} hex=$hex")
        for (hex in r.rawHex) out.add("raw test=${r.id} route=${routeTag(r.route)} hex=$hex")
    }
    return out
}

private fun footerLines(input: LogInput): List<String> {
    val out = ArrayList<String>()
    for (route in Route.entries) {
        val inRoute = input.results.filter { it.route == route }
        val counts = Outcome.entries.map { o -> o to inRoute.count { it.outcome == o } }.filter { it.second > 0 }
        out.add("outcomes route=${routeTag(route)} " + counts.joinToString(" ") { "${it.first}=${it.second}" }.ifEmpty { "none" })
    }
    val f = input.footer
    out.add("reports_received=${f.reportsReceived}")
    out.add("reports_dropped=${f.reportsDropped} ($ASSUMES_COUNTER_NOTE)")
    out.add("longest_gap_ms=${f.longestGapMs}")
    out.add("feed_events_overflowed=${f.feedOverflow} axis_rows_skipped=${f.axisRowsSkipped} late_android_events_dropped=${f.lateEventsDropped}")
    out.add("logcat_command=$LOGCAT_COMMAND")
    return out
}

/** Header, Android's view, one line per test, the trace, the crash record, then the footer. Never longer than the cap. */
fun renderLog(input: LogInput, caps: LogCaps = LogCaps.DEFAULT): String {
    val log = CappedLog(caps)
    log.line(LOG_FORMAT_LINE)
    for ((key, value) in input.header) log.line("$key=$value")
    val body = ArrayList<String>()
    input.answers?.let { body.addAll(answerLines(it)) } ?: body.add("android.answers=none")
    body.addAll(testLines(input.results))
    body.addAll(tourLines(input.tourAnswers))
    for (row in input.trace.asReversed()) body.add("trace control=${row.control} route=${routeTag(row.route)} line=${row.text}")
    input.crashRecord?.lines()?.forEach { body.add("crash ${it}") }
    for (line in body) if (!log.line(line)) break
    if (log.truncated) log.footerLine(TRUNCATED_LINE)
    for (line in footerLines(input)) log.footerLine(line)
    return log.finish()
}
