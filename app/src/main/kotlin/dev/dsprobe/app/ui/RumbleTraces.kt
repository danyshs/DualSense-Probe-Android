package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.route.MotorLevels
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.pad.Limits

private fun DrawScope.motorLine(history: List<MotorLevels>, color: Color, pick: (MotorLevels) -> Int) {
    if (history.size < 2) return
    val step = size.width / (AppLimits.RUMBLE_TRACE_SAMPLES_MAX - 1)
    var prev: Offset? = null
    for ((i, level) in history.withIndex()) {
        val point = Offset(i * step, size.height - pick(level).toFloat() / Limits.MOTOR_STRENGTH_MAX * size.height)
        prev?.let { drawLine(color, it, point, strokeWidth = 3f) }
        prev = point
    }
}

/** The left and right values the route reports sending, newest at the right; 0 at the bottom, 255 at the top. */
@Composable
fun RumbleTraces(history: List<MotorLevels>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.rumble_trace_title), color = DsColors.TextDim)
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            drawRect(DsColors.SurfaceHigh, Offset.Zero, size)
            motorLine(history, DsColors.AxisX) { it.left }
            motorLine(history, DsColors.AxisY) { it.right }
        }
        Text(stringResource(R.string.rumble_trace_key), color = DsColors.TextDim, maxLines = 1, softWrap = false)
    }
}
