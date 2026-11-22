package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.Angles
import dev.dsprobe.app.debug.Readout
import dev.dsprobe.app.debug.bubbleOf
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.ui.theme.ChartColors
import dev.dsprobe.app.ui.theme.NumberStyle
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val LEVEL_SIZE_DP = 180
private const val DIAL_SIZE_DP = 96
private const val STILL_DOT_DP = 16
private const val STROKE_PX = 3f
private const val DOT_RADIUS_RATIO = 0.08f
private const val TRAVEL_RATIO = 0.85f // the dot and needle never leave the ring
private const val ARROW_HEAD_RATIO = 0.12f
private const val ARROW_HEAD_SPREAD_RAD = 0.5f
private const val DEG_TO_RAD = 0.017453292f

/** A line from [from] to [to] with a small head at [to]. */
private fun DrawScope.arrow(colour: Color, from: Offset, to: Offset) {
    drawLine(colour, from, to, STROKE_PX)
    val backX = from.x - to.x
    val backY = from.y - to.y
    if (hypot(backX, backY) == 0f) return
    val head = size.minDimension * ARROW_HEAD_RATIO
    val base = atan2(backY, backX)
    for (side in listOf(-ARROW_HEAD_SPREAD_RAD, ARROW_HEAD_SPREAD_RAD)) {
        val tip = Offset(to.x + head * cos(base + side), to.y + head * sin(base + side))
        drawLine(colour, to, tip, STROKE_PX)
    }
}

private fun DrawScope.ring(c: DrawColors) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    drawCircle(c.surfaceHigh, size.minDimension / 2f, centre)
    drawCircle(c.outline, size.minDimension / 2f, centre, style = Stroke(STROKE_PX))
}

@Composable
private fun totalText(m: Motion, total: Float): String = if (m.unit == MotionUnit.SI) {
    stringResource(R.string.accel_total_si, String.format(Locale.ROOT, "%.2f", total))
} else {
    stringResource(R.string.accel_total_raw, String.format(Locale.ROOT, "%.0f", total))
}

/**
 * A bubble level for the accelerometer: the dot moves to the side the reading points to, the
 * arrow is the opposite way (down), and the total is the size of the whole reading.
 */
@Composable
fun BubbleLevel(m: Motion) {
    val c = drawColors()
    val bubble = bubbleOf(m)
    val description = stringResource(R.string.accel_level_cd)
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(LEVEL_SIZE_DP.dp).semantics { contentDescription = description }) {
            ring(c)
            val centre = Offset(size.width / 2f, size.height / 2f)
            val reach = size.minDimension / 2f * TRAVEL_RATIO
            drawLine(c.outline, Offset(centre.x, 0f), Offset(centre.x, size.height))
            drawLine(c.outline, Offset(0f, centre.y), Offset(size.width, centre.y))
            // the pad's y runs up the screen
            val dot = Offset(centre.x + bubble.dx * reach, centre.y - bubble.dy * reach)
            val down = Offset(centre.x - bubble.dx * reach, centre.y + bubble.dy * reach)
            arrow(c.warn, centre, down)
            drawCircle(c.accent, size.minDimension * DOT_RADIUS_RATIO, dot)
        }
        Column {
            Text(
                stringResource(R.string.accel_down),
                color = c.warn,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(totalText(m, bubble.total), style = NumberStyle)
        }
    }
}

@Composable
private fun Dial(label: String, angleDeg: Float, colour: Color) {
    val c = drawColors()
    val description = stringResource(R.string.gyro_dial_cd, label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(DIAL_SIZE_DP.dp).semantics { contentDescription = description }) {
            ring(c)
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f * TRAVEL_RATIO
            // 0 is straight up, positive turns clockwise
            val rad = angleDeg * DEG_TO_RAD
            val tip = Offset(centre.x + radius * sin(rad), centre.y - radius * cos(rad))
            val markEnd = Offset(centre.x, size.height * DOT_RADIUS_RATIO)
            drawLine(c.outline, Offset(centre.x, 0f), markEnd)
            drawLine(colour, centre, tip, STROKE_PX * 2f)
        }
        Text(
            String.format(Locale.ROOT, "%s %+4.0f°", label, angleDeg),
            style = NumberStyle,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun StillLight(isStill: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(STILL_DOT_DP.dp)) {
            drawCircle(if (isStill) scheme.primary else scheme.outlineVariant)
        }
        Text(
            stringResource(if (isStill) R.string.gyro_still else R.string.gyro_moving),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Three dials turned by the angle each axis has integrated since the card opened, a still light,
 * and Reset.
 */
@Composable
fun GyroDials(r: Readout.Bars, angles: Angles, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Dial("x", angles.x, ChartColors.AxisX)
            Dial("y", angles.y, ChartColors.AxisY)
            Dial("z", angles.z, ChartColors.AxisZ)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StillLight(r.isStill)
            QuietButton(onReset) { OneLine(R.string.gyro_reset) }
        }
        if (r.isRawCounts) {
            Text(
                stringResource(R.string.gyro_raw_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
