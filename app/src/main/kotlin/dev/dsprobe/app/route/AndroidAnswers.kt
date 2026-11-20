package dev.dsprobe.app.route

import androidx.annotation.StringRes
import dev.dsprobe.app.R
import dev.dsprobe.pad.Button
import java.util.Locale

// Android's raw answers about the pad, as plain data: the query fills them, Capabilities reads them, tests fake them.
// A null list or object means that question threw and has no answer.

data class KeyAnswer(val code: Int, val name: String, val button: Button?, val present: Boolean)

data class AxisAnswer(val axis: Int, val name: String, val min: Float, val max: Float, val flat: Float)

data class SensorAnswer(val type: Int, val name: String, val maxRange: Float)

data class LightAnswer(val name: String, val type: Int)

data class BatteryAnswer(val isPresent: Boolean, val capacity: Float, val status: Int)

data class AudioAnswer(val type: Int)

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
) {
    init {
        require(touchPointersSeen >= 0) { "touch pointers ${touchPointersSeen}" }
    }
}

/** One line of the "What Android reports" sheet: a label from resources and Android's raw value. */
data class AnswerRow(@param:StringRes val labelRes: Int, val value: String)

private const val NO_ANSWER = "no answer"

private fun fixed(v: Float): String = String.format(Locale.ROOT, "%+.2f", v)

private fun <T> rowsOf(items: List<T>?, label: Int, text: (T) -> String): List<AnswerRow> =
    if (items == null) listOf(AnswerRow(label, NO_ANSWER)) else items.map { AnswerRow(label, text(it)) }

/** Every raw answer, one row each, nothing interpreted. The battery's name is never read: it holds the pad's Bluetooth address. */
fun answerRows(a: AndroidAnswers): List<AnswerRow> {
    val rows = ArrayList<AnswerRow>()
    rows += rowsOf(a.keys, R.string.answer_key) { "${it.name} ${it.present}" }
    rows += rowsOf(a.axes, R.string.answer_axis) { "${it.name} ${fixed(it.min)}..${fixed(it.max)} flat ${fixed(it.flat)}" }
    rows += AnswerRow(R.string.answer_sources, String.format(Locale.ROOT, "0x%08x", a.sources))
    rows += rowsOf(a.sensors, R.string.answer_sensor) { "${it.name} type ${it.type} range ${fixed(it.maxRange)}" }
    rows += AnswerRow(R.string.answer_vibrators, a.vibratorIds?.joinToString(", ")?.ifEmpty { "none" } ?: NO_ANSWER)
    rows += rowsOf(a.lights, R.string.answer_light) { "${it.name} type ${it.type}" }
    rows += AnswerRow(
        R.string.answer_battery,
        a.battery?.let { "present ${it.isPresent} capacity ${fixed(it.capacity)} status ${it.status}" } ?: NO_ANSWER,
    )
    rows += rowsOf(a.audioInputs, R.string.answer_audio_in) { "type ${it.type}" }
    rows += rowsOf(a.audioOutputs, R.string.answer_audio_out) { "type ${it.type}" }
    rows += AnswerRow(R.string.answer_pointers, a.touchPointersSeen.toString())
    for (label in a.failed) rows += AnswerRow(R.string.answer_failed, label)
    return rows
}
