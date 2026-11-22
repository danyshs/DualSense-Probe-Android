package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.Axis
import dev.dsprobe.app.debug.BarOrientation
import dev.dsprobe.app.debug.EndpointCard
import dev.dsprobe.app.debug.Readout
import dev.dsprobe.app.debug.ReadoutKind
import dev.dsprobe.app.debug.axisText
import dev.dsprobe.app.debug.barOrientation
import dev.dsprobe.app.debug.readoutKindOf
import dev.dsprobe.app.debug.triggerText
import dev.dsprobe.app.live.Frame
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.ui.theme.ChartColors
import dev.dsprobe.app.ui.theme.NumberStyle
import kotlin.math.abs

private val BIG_NUMBER = NumberStyle.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold)
private val LINE_NUMBER = NumberStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
private const val BAR_THICKNESS_DP = 16
private const val BAR_LENGTH_DP = 120
private const val BAR_WIDTH_DP = 32
private const val CENTRE_LINE_PX = 2f
private const val PILL_HEIGHT_DP = 48
private const val CENTRE_RATIO = 0.5f
private const val TOUCH_CENTRE = 0.5f // a touch is 0..1 across the pad; a bar runs -1..1

/** The card's live readout, or nothing for a card the pad reports nothing live for. */
@Composable
fun ReadoutArea(
    card: EndpointCard,
    readout: Readout?,
    route: Route,
    onResetGyro: () -> Unit,
    compact: Boolean = false,
) {
    val kind = readoutKindOf(card)
    if (kind == ReadoutKind.NONE) return
    if (!compact) {
        Text(
            stringResource(R.string.tour_live_value),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    when (readout) {
        null -> Text(
            stringResource(
                if (kind == ReadoutKind.BYTES) R.string.tour_no_raw else R.string.readout_none,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is Readout.Stick -> StickReadout(readout, route)

        is Readout.Trigger -> TriggerReadout(readout, route)

        is Readout.Buttons -> ButtonsReadout(readout, compact)

        is Readout.Bars -> MotionReadout(readout, card.id == "in.accel", onResetGyro, compact)

        is Readout.Finger -> FingerReadout(readout, route, compact)

        is Readout.Lines -> for (line in readout.lines) {
            Text(line, style = LINE_NUMBER, maxLines = 1, softWrap = false)
        }

        is Readout.Bytes -> BytesView(readout)
    }
}

/** One bar: [control] says which way it runs; a positive value goes right or down. */
private class AxisBar(
    val control: String,
    val label: String,
    val value: Float,
    val scale: Float,
    val colour: Color,
)

private fun DrawScope.centredBar(c: DrawColors, bar: AxisBar, isVertical: Boolean) {
    drawRect(c.surfaceHigh, Offset.Zero, size)
    val length = if (isVertical) size.height else size.width
    val half = length * CENTRE_RATIO
    val reach = (bar.value / bar.scale).coerceIn(-1f, 1f) * half
    val from = if (reach >= 0f) half else half + reach
    if (isVertical) {
        drawRect(bar.colour, Offset(0f, from), Size(size.width, abs(reach)))
        drawRect(c.outline, Offset(0f, half), Size(size.width, CENTRE_LINE_PX))
    } else {
        drawRect(bar.colour, Offset(from, 0f), Size(abs(reach), size.height))
        drawRect(c.outline, Offset(half, 0f), Size(CENTRE_LINE_PX, size.height))
    }
}

@Composable
private fun VerticalBar(bar: AxisBar) {
    val c = drawColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.width(BAR_WIDTH_DP.dp).height(BAR_LENGTH_DP.dp)) {
            centredBar(c, bar, isVertical = true)
        }
        Text(bar.label, style = NumberStyle, color = c.textDim)
    }
}

@Composable
private fun HorizontalBar(bar: AxisBar) {
    val c = drawColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(bar.label, style = NumberStyle, color = c.textDim)
        Canvas(Modifier.weight(1f).height(BAR_THICKNESS_DP.dp)) {
            centredBar(c, bar, isVertical = false)
        }
    }
}

/** The bars of one control: up-and-down ones side by side, left-and-right ones stacked under. */
@Composable
private fun AxesBars(bars: List<AxisBar>, modifier: Modifier = Modifier) {
    val vertical = bars.filter { barOrientation(it.control) == BarOrientation.VERTICAL }
    val horizontal = bars.filter { barOrientation(it.control) == BarOrientation.HORIZONTAL }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vertical.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (bar in vertical) VerticalBar(bar)
            }
        }
        for (bar in horizontal) HorizontalBar(bar)
    }
}

@Composable
private fun AxisRange(label: String, axis: Axis, route: Route) {
    Text(
        stringResource(
            R.string.readout_axis_range,
            label,
            axisText(axis.value, route),
            axisText(axis.min, route),
            axisText(axis.max, route),
        ),
        style = NumberStyle,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun ClickLine(isDown: Boolean) {
    val state = stringResource(if (isDown) R.string.readout_pressed else R.string.readout_released)
    Text(
        stringResource(R.string.readout_click, state),
        style = NumberStyle,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun StickReadout(r: Readout.Stick, route: Route) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StickView("", r.x.value, r.y.value, emptyList(), 0L, Modifier.weight(1f))
        AxesBars(
            listOf(
                AxisBar("in.stick.x", "x", r.x.value, 1f, ChartColors.AxisX),
                AxisBar("in.stick.y", "y", r.y.value, 1f, ChartColors.AxisY),
            ),
            Modifier.weight(1f),
        )
    }
    AxisRange("x", r.x, route)
    AxisRange("y", r.y, route)
    ClickLine(r.isClicked)
}

@Composable
private fun TriggerReadout(r: Readout.Trigger, route: Route) {
    val c = drawColors()
    val isVertical = barOrientation("in.trigger") == BarOrientation.VERTICAL
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        val bar = if (isVertical) {
            Modifier.width(BAR_WIDTH_DP.dp).height(BAR_LENGTH_DP.dp)
        } else {
            Modifier.weight(1f).height(BAR_THICKNESS_DP.dp)
        }
        Canvas(bar) {
            drawRect(c.surfaceHigh, Offset.Zero, size)
            if (isVertical) {
                val fill = size.height * r.value
                drawRect(c.accent, Offset(0f, size.height - fill), Size(size.width, fill))
            } else {
                drawRect(c.accent, Offset.Zero, Size(size.width * r.value, size.height))
            }
        }
        Column {
            Text(triggerText(r.value, route), style = BIG_NUMBER, maxLines = 1, softWrap = false)
            ClickLine(r.isClicked)
        }
    }
}

@Composable
private fun HeldPill(
    label: String?,
    isDown: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val state = stringResource(if (isDown) R.string.readout_pressed else R.string.readout_released)
    Surface(
        modifier.heightIn(min = PILL_HEIGHT_DP.dp),
        color = if (isDown) scheme.primary else scheme.surfaceContainerHigh,
        contentColor = if (isDown) scheme.onPrimary else scheme.onSurfaceVariant,
        shape = RoundedCornerShape(PILL_HEIGHT_DP.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                if (label == null) state else stringResource(R.string.readout_button, label, state),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ButtonsReadout(r: Readout.Buttons, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val full = Modifier.fillMaxWidth()
        if (r.items.isEmpty()) HeldPill(null, false, full)
        if (!compact) {
            for (item in r.items) HeldPill(item.label, item.isDown, full)
            return
        }
        // two pills a row, so a group of six still fits three rows
        for (pair in r.items.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in pair) HeldPill(item.label, item.isDown, Modifier.weight(1f))
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** The visual check first (bubble level or dials), the bars and numbers under it. */
@Composable
private fun MotionReadout(
    r: Readout.Bars,
    isAccel: Boolean,
    onResetGyro: () -> Unit,
    compact: Boolean,
) {
    val angles = r.angles
    val unit = if (r.isRawCounts) MotionUnit.RAW_COUNTS else MotionUnit.SI
    // the compact card keeps only the visual check and one line of numbers; the rest is in Details
    if (!compact) TiltPicture(r.gravity, angles, if (isAccel) onResetGyro else null)
    if (isAccel) {
        BubbleLevel(Motion(r.x, r.y, r.z, unit))
    } else if (angles != null) {
        GyroDials(r, angles, onResetGyro)
    }
    if (compact) {
        Text(
            stringResource(R.string.motion_values, signed(r.x), signed(r.y), signed(r.z)),
            style = NumberStyle,
            maxLines = 1,
            softWrap = false,
        )
        return
    }
    MotionBars(r, if (isAccel) "in.accel" else "in.gyro")
    val hints = if (isAccel) {
        listOf(R.string.accel_hint_flat, R.string.accel_hint_edge)
    } else {
        listOf(R.string.gyro_hint_still, R.string.gyro_hint_turn)
    }
    for (hint in hints) {
        Text(stringResource(hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MotionBars(r: Readout.Bars, base: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AxesBars(
            listOf(
                AxisBar("$base.x", "x", r.x, r.scale, ChartColors.AxisX),
                AxisBar("$base.y", "y", r.y, r.scale, ChartColors.AxisY),
                AxisBar("$base.z", "z", r.z, r.scale, ChartColors.AxisZ),
            ),
        )
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
private fun FingerReadout(r: Readout.Finger, route: Route, compact: Boolean) {
    TouchDot(listOfNotNull(r.touch), route)
    val touch = r.touch ?: return
    if (compact) return
    AxesBars(
        listOf(
            AxisBar(
                "in.touch.x",
                "x",
                (touch.x - TOUCH_CENTRE) / TOUCH_CENTRE,
                1f,
                ChartColors.AxisX,
            ),
            AxisBar(
                "in.touch.y",
                "y",
                (touch.y - TOUCH_CENTRE) / TOUCH_CENTRE,
                1f,
                ChartColors.AxisY,
            ),
        ),
    )
}

@Composable
private fun TouchDot(touches: List<TouchPoint>, route: Route) {
    val state = PadState.EMPTY.copy(touches = touches)
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
