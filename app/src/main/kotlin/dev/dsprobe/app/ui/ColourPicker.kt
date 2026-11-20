package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.output.HUE_DEGREES_MAX
import dev.dsprobe.app.output.Hsv
import dev.dsprobe.app.output.hsvToRgb
import dev.dsprobe.app.output.parseChannel
import dev.dsprobe.app.output.parseHex
import dev.dsprobe.app.output.rgbToHsv
import dev.dsprobe.app.output.toHex
import dev.dsprobe.app.ui.theme.NumberStyle
import dev.dsprobe.pad.Rgb

private const val SQUARE_ASPECT = 1.6f
private const val STRIP_HEIGHT_DP = 36
private const val MARKER_RADIUS_PX = 14f
private const val HUE_STOPS = 6

private fun Rgb.toColor() = Color(r.toInt(), g.toInt(), b.toInt())

/** A pointer-down-and-drag surface that reports the position as fractions 0..1 of its size. */
@Composable
private fun DragCanvas(
    enabled: Boolean,
    modifier: Modifier,
    onFraction: (Float, Float) -> Unit,
    onEnd: () -> Unit,
    draw: DrawScope.() -> Unit,
) {
    val latestFraction by rememberUpdatedState(onFraction)
    val latestEnd by rememberUpdatedState(onEnd)
    val input = Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            latestFraction((down.position.x / size.width).coerceIn(0f, 1f), (down.position.y / size.height).coerceIn(0f, 1f))
            drag(down.id) { change ->
                latestFraction((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                change.consume()
            }
            latestEnd()
        }
    }
    Canvas(modifier.then(input), onDraw = draw)
}

private fun DrawScope.drawSquare(hsv: Hsv) {
    val hue = hsvToRgb(Hsv(hsv.hue, 1f, 1f)).toColor()
    drawRect(Brush.horizontalGradient(listOf(Color.White, hue)))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
    val at = Offset(hsv.sat * size.width, (1f - hsv.value) * size.height)
    drawCircle(Color.White, MARKER_RADIUS_PX, at, style = Stroke(width = 3f))
    drawCircle(Color.Black, MARKER_RADIUS_PX + 3f, at, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawStrip(hsv: Hsv) {
    val stops = (0..HUE_STOPS).map { hsvToRgb(Hsv(it * HUE_DEGREES_MAX / HUE_STOPS, 1f, 1f)).toColor() }
    drawRect(Brush.horizontalGradient(stops))
    val x = hsv.hue / HUE_DEGREES_MAX * size.width
    drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 5f)
}

@Composable
private fun ValueField(label: String, shown: String, modifier: Modifier, enabled: Boolean, onText: (String) -> Unit) {
    var text by remember(shown) { mutableStateOf(shown) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onText(it)
        },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        textStyle = NumberStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
}

/**
 * A saturation-value square, a hue strip, and R, G, B and hex fields kept in step. Dragging gets near any
 * colour; the fields reach each of the 16.7 million exactly. [onColour] fires on every change (the
 * controller rate-limits), [onCommit] when a drag ends or a field is committed.
 */
@Composable
fun ColourPicker(enabled: Boolean, start: Rgb, onColour: (Rgb) -> Unit, onCommit: () -> Unit) {
    var rgb by remember { mutableStateOf(start) }
    var hsv by remember { mutableStateOf(rgbToHsv(start)) }
    val fromHsv = { next: Hsv ->
        hsv = next
        rgb = hsvToRgb(next)
        onColour(rgb)
    }
    val fromRgb = { next: Rgb ->
        rgb = next
        hsv = rgbToHsv(next)
        onColour(next)
        onCommit()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DragCanvas(
            enabled, Modifier.fillMaxWidth().aspectRatio(SQUARE_ASPECT),
            onFraction = { x, y -> fromHsv(Hsv(hsv.hue, x, 1f - y)) }, onEnd = onCommit,
        ) { drawSquare(hsv) }
        DragCanvas(
            enabled, Modifier.fillMaxWidth().height(STRIP_HEIGHT_DP.dp),
            onFraction = { x, _ -> fromHsv(Hsv(x * HUE_DEGREES_MAX, hsv.sat, hsv.value)) }, onEnd = onCommit,
        ) { drawStrip(hsv) }
        Row(Modifier.fillMaxWidth().background(rgb.toColor()).height(STRIP_HEIGHT_DP.dp)) {}
        ChannelFields(enabled, rgb, fromRgb)
    }
}

@Composable
private fun ChannelFields(enabled: Boolean, rgb: Rgb, onRgb: (Rgb) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val each = Modifier.weight(1f)
        ValueField(stringResource(R.string.field_r), rgb.r.toString(), each, enabled) { t ->
            parseChannel(t)?.let { onRgb(rgb.copy(r = it)) }
        }
        ValueField(stringResource(R.string.field_g), rgb.g.toString(), each, enabled) { t ->
            parseChannel(t)?.let { onRgb(rgb.copy(g = it)) }
        }
        ValueField(stringResource(R.string.field_b), rgb.b.toString(), each, enabled) { t ->
            parseChannel(t)?.let { onRgb(rgb.copy(b = it)) }
        }
        ValueField(stringResource(R.string.field_hex), rgb.toHex(), Modifier.weight(HEX_FIELD_WEIGHT), enabled) { t ->
            parseHex(t)?.let(onRgb)
        }
    }
}

private const val HEX_FIELD_WEIGHT = 1.6f
