package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.EndpointCard
import dev.dsprobe.app.debug.Readout
import dev.dsprobe.app.debug.ReadoutKind
import dev.dsprobe.app.debug.axisText
import dev.dsprobe.app.debug.readoutKindOf
import dev.dsprobe.app.debug.triggerText
import dev.dsprobe.app.live.Frame
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.ui.theme.ChartColors
import dev.dsprobe.app.ui.theme.NumberStyle

private val BIG_NUMBER = NumberStyle.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold)
private const val GAUGE_HEIGHT_DP = 32
private const val BAR_HEIGHT_DP = 16
private const val MARKER_WIDTH_PX = 8f
private const val TRACK_ALPHA = 0.5f
private const val PILL_HEIGHT_DP = 64
private const val CENTRE_RATIO = 0.5f

/** The card's one live readout, or nothing for a card the pad reports nothing live for. */
@Composable
fun ReadoutArea(card: EndpointCard, readout: Readout?, route: Route) {
    val kind = readoutKindOf(card)
    if (kind == ReadoutKind.NONE) return
    Text(
        stringResource(R.string.tour_live_value),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (readout) {
        null -> Text(
            stringResource(
                if (kind ==
                    ReadoutKind.BYTES
                ) {
                    R.string.tour_no_raw
                } else {
                    R.string.readout_none
                },
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is Readout.Axis -> AxisGauge(readout, route)

        is Readout.Trigger -> TriggerFill(readout, route)

        is Readout.Held -> HeldPill(readout.isDown)

        is Readout.Bars -> MotionBars(readout)

        is Readout.Touch -> TouchDot(readout, route)

        is Readout.Words -> Text(readout.text, style = BIG_NUMBER, maxLines = 1, softWrap = false)

        is Readout.Bytes -> BytesView(readout)
    }
}

@Composable
private fun AxisGauge(r: Readout.Axis, route: Route) {
    val c = drawColors()
    Text(axisText(r.value, route), style = BIG_NUMBER, maxLines = 1, softWrap = false)
    Canvas(Modifier.fillMaxWidth().height(GAUGE_HEIGHT_DP.dp)) {
        drawRect(c.surfaceHigh, Offset.Zero, size)
        val seenFrom = (r.min + 1f) / 2f * size.width
        val seenTo = (r.max + 1f) / 2f * size.width
        drawRect(
            c.accent.copy(alpha = TRACK_ALPHA),
            Offset(seenFrom, 0f),
            Size(seenTo - seenFrom, size.height),
        )
        drawRect(c.outline, Offset(size.width * CENTRE_RATIO, 0f), Size(2f, size.height))
        val x = ((r.value + 1f) / 2f * size.width).coerceIn(0f, size.width - MARKER_WIDTH_PX)
        drawRect(c.accent, Offset(x, 0f), Size(MARKER_WIDTH_PX, size.height))
    }
    Text(
        stringResource(R.string.readout_min_max, axisText(r.min, route), axisText(r.max, route)),
        style = NumberStyle,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun TriggerFill(r: Readout.Trigger, route: Route) {
    val c = drawColors()
    Text(triggerText(r.value, route), style = BIG_NUMBER, maxLines = 1, softWrap = false)
    Canvas(Modifier.fillMaxWidth().height(GAUGE_HEIGHT_DP.dp)) {
        drawRect(c.surfaceHigh, Offset.Zero, size)
        drawRect(c.accent, Offset.Zero, Size(size.width * r.value, size.height))
    }
}

@Composable
private fun HeldPill(isDown: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().heightIn(min = PILL_HEIGHT_DP.dp),
        color = if (isDown) scheme.primary else scheme.surfaceContainerHigh,
        contentColor = if (isDown) scheme.onPrimary else scheme.onSurfaceVariant,
        shape = RoundedCornerShape(PILL_HEIGHT_DP.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (isDown) R.string.readout_pressed else R.string.readout_released),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun OneBar(label: String, value: Float, scale: Float, colour: Color) {
    val c = drawColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = NumberStyle, color = c.textDim)
        Canvas(Modifier.weight(1f).height(BAR_HEIGHT_DP.dp)) {
            drawRect(c.surfaceHigh, Offset.Zero, size)
            val half = size.width * CENTRE_RATIO
            val reach = (value / scale).coerceIn(-1f, 1f) * half
            val from = if (reach >= 0f) half else half + reach
            drawRect(colour, Offset(from, 0f), Size(kotlin.math.abs(reach), size.height))
            drawRect(c.outline, Offset(half, 0f), Size(2f, size.height))
        }
    }
}

@Composable
private fun MotionBars(r: Readout.Bars) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OneBar("x", r.x, r.scale, ChartColors.AxisX)
        OneBar("y", r.y, r.scale, ChartColors.AxisY)
        OneBar("z", r.z, r.scale, ChartColors.AxisZ)
        Text(
            stringResource(R.string.motion_values, signed(r.x), signed(r.y), signed(r.z)),
            style = NumberStyle,
            maxLines = 1,
            softWrap = false,
        )
        if (r.isRawCounts) {
            Text(
                stringResource(R.string.label_motion_raw),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TouchDot(r: Readout.Touch, route: Route) {
    val state = PadState.EMPTY.copy(touches = r.touches)
    val ui = LiveUi(Frame(state, emptySet(), emptySet()), FeelFrame.EMPTY)
    TouchpadView(ui, null, route == Route.ANDROID)
}

@Composable
private fun BytesView(r: Readout.Bytes) {
    Text(r.hex, style = BIG_NUMBER, maxLines = 1, softWrap = false)
    Text(
        stringResource(R.string.readout_bytes_changed, r.changes),
        style = NumberStyle,
        maxLines = 1,
        softWrap = false,
    )
}
