package dev.dsprobe.app.route

import androidx.annotation.StringRes
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.pad.Button
import java.util.Locale

// Android's raw answers about the pad, as plain data: the query fills them, Capabilities reads
// them, tests fake them.
// A null list or object means that question threw and has no answer.

data class KeyAnswer(val code: Int, val name: String, val button: Button?, val present: Boolean)

data class AxisAnswer(
    val axis: Int,
    val name: String,
    val min: Float,
    val max: Float,
    val flat: Float,
)

data class SensorAnswer(val type: Int, val name: String, val maxRange: Float)

data class LightAnswer(val name: String, val type: Int)

data class BatteryAnswer(val isPresent: Boolean, val capacity: Float, val status: Int)

/**
 * One audio device of the pad: its type and the channel counts and index masks Android lists for
 * it.
 */
data class AudioAnswer(
    val type: Int,
    val channelCounts: List<Int> = emptyList(),
    val channelIndexMasks: List<Int> = emptyList(),
)

data class AndroidAnswers(
    val keys: List<KeyAnswer>?,
    val axes: List<AxisAnswer>?,
    val sources: Int,
    val sensors: List<SensorAnswer>?,
    val vibratorIds: List<Int>?,
    val lights: List<LightAnswer>?,
    val battery: BatteryAnswer?,
    val audioInputs: List<AudioAnswer>?,
    val audioOutputs: List<AudioAnswer>?,
    val touchPointersSeen: Int,
    val failed: List<String>,
    val deviceName: String = "",
    /**
     * What reflection found in Android's controller classes; null when it was not asked or threw.
     */
    val probe: ApiProbe? = null,
    /** The key code that arrived while the Mute prompt was up and maps to no pad button. */
    val muteKeyCode: Int? = null,
) {
    init {
        require(touchPointersSeen >= 0) { "touch pointers $touchPointersSeen" }
    }
}

/** One line of the "What Android reports" sheet: a label from resources and Android's raw value. */
data class AnswerRow(@param:StringRes val labelRes: Int, val value: String)

private const val NO_ANSWER = "no answer"

private fun fixed(v: Float): String = String.format(Locale.ROOT, "%+.2f", v)

private fun <T> rowsOf(items: List<T>?, label: Int, text: (T) -> String): List<AnswerRow> =
    if (items == null) {
        listOf(AnswerRow(label, NO_ANSWER))
    } else {
        items.map { AnswerRow(label, text(it)) }
    }

private fun audioText(a: AudioAnswer): String =
    "type ${a.type} channels ${a.channelCounts} index masks ${a.channelIndexMasks.map {
        "0x%x".format(it)
    }}"

/**
 * The reflection probe: one summary row per class, one row per matching method, one row per
 * may-offer.
 */
private fun probeRows(a: AndroidAnswers): List<AnswerRow> {
    val probe = a.probe ?: return listOf(AnswerRow(R.string.answer_probe, "no answer"))
    val rows = ArrayList<AnswerRow>()
    rows +=
        AnswerRow(
            R.string.answer_probe,
            "API level ${probe.apiLevel}, ${probe.methodCount} public methods probed",
        )
    val matches = probe.matches()
    for (m in matches.take(AppLimits.PROBE_MATCHES_LISTED_MAX)) {
        rows +=
            AnswerRow(
                R.string.answer_probe_match,
                "${m.method} (${m.keyword}), listed, never called",
            )
    }
    for (offer in mayOffer(a)) {
        rows +=
            AnswerRow(R.string.answer_may_offer, "${offer.feature} ${offer.method}")
    }
    return rows
}

/**
 * Every raw answer, one row each, nothing interpreted. The battery's name is never read: it holds
 * the pad's Bluetooth address.
 */
fun answerRows(a: AndroidAnswers): List<AnswerRow> {
    val rows = ArrayList<AnswerRow>()
    rows += AnswerRow(R.string.answer_device, a.deviceName.ifEmpty { "no answer" })
    // every defined code is asked; the sheet lists the ones the pad has and the ones the app maps
    rows +=
        rowsOf(
            a.keys?.filter {
                it.present || it.button != null
            },
            R.string.answer_key,
        ) { "${it.name} (${it.code}) ${it.present}" }
    rows +=
        rowsOf(a.axes, R.string.answer_axis) {
            "${it.name} ${fixed(it.min)}..${fixed(it.max)} flat ${fixed(it.flat)}"
        }
    rows += AnswerRow(R.string.answer_sources, String.format(Locale.ROOT, "0x%08x", a.sources))
    rows +=
        rowsOf(a.sensors, R.string.answer_sensor) {
            "${it.name} type ${it.type} range ${fixed(it.maxRange)}"
        }
    rows +=
        AnswerRow(
            R.string.answer_vibrators,
            a.vibratorIds?.joinToString(", ")?.ifEmpty { "none" } ?: NO_ANSWER,
        )
    rows += rowsOf(a.lights, R.string.answer_light) { "${it.name} type ${it.type}" }
    rows += AnswerRow(
        R.string.answer_battery,
        a.battery?.let {
            "present ${it.isPresent} capacity ${fixed(it.capacity)} status ${it.status}"
        }
            ?: NO_ANSWER,
    )
    rows += rowsOf(a.audioInputs, R.string.answer_audio_in) { audioText(it) }
    rows += rowsOf(a.audioOutputs, R.string.answer_audio_out) { audioText(it) }
    rows += probeRows(a)
    rows += AnswerRow(R.string.answer_pointers, a.touchPointersSeen.toString())
    for (label in a.failed) rows += AnswerRow(R.string.answer_failed, label)
    return rows
}

/**
 * The same answers as plain `key=value` lines for the log: the keys are format tokens, not screen
 * text.
 */
fun answerLines(a: AndroidAnswers): List<String> {
    val lines = ArrayList<String>()
    lines +=
        "android.device name=${a.deviceName.ifEmpty { "no answer" }} sources=" +
        "0x%08x".format(a.sources)
    lines += keyLines(a.keys)
    a.axes?.forEach {
        lines +=
            "android.axis name=${it.name} id=${it.axis} min=${it.min} max=${it.max} flat=${it.flat}"
    }
    a.sensors?.forEach {
        lines +=
            "android.sensor name=${it.name} type=${it.type} range=${it.maxRange}"
    }
    lines += "android.vibrators ids=${a.vibratorIds?.joinToString(",") ?: "no answer"}"
    a.lights?.forEach { lines += "android.light name=${it.name} type=${it.type}" }
    lines +=
        "android.battery ${a.battery?.let {
            "present=${it.isPresent} capacity=${it.capacity} status=${it.status}"
        } ?: "no answer"}"
    a.audioInputs?.forEach { lines += "android.audio.in ${audioText(it)}" }
    a.audioOutputs?.forEach { lines += "android.audio.out ${audioText(it)}" }
    lines += "android.touchpad fingers_seen=${a.touchPointersSeen}"
    lines += "android.mute_key_code=${a.muteKeyCode ?: "none"}"
    lines += probeLines(a.probe)
    mayOffer(a).forEach { lines += "android.may_offer feature=${it.feature} method=${it.method}" }
    a.failed.forEach { lines += "android.failed question=$it" }
    return lines
}

/** What the API probe found; one "none" line when it did not run. */
private fun probeLines(probe: ApiProbe?): List<String> {
    if (probe == null) return listOf("android.probe answer=none")
    val head = "android.probe api_level=${probe.apiLevel} methods=${probe.methodCount}"
    return listOf(head) + probe.matches().map {
        "android.probe.match method=${it.method} keyword=${it.keyword} called=never"
    }
}

/** The keys the pad has or the app maps; one "none" line when Android gave no answer. */
private fun keyLines(keys: List<KeyAnswer>?): List<String> =
    keys?.filter { it.present || it.button != null }?.map {
        "android.key code=${it.code} name=${it.name} present=${it.present} " +
            "button=${it.button ?: "unmapped"}"
    } ?: listOf("android.key answer=none")
