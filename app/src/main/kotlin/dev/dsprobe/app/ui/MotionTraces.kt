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
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle

private const val GYRO_SCALE_FLOOR = 1f // rad/s
private const val ACCEL_SCALE_FLOOR = 4f // m/s2

private fun DrawScope.trace(history: List<Motion>, scale: Float, color: Color, pick: (Motion) -> Float) {
    if (history.size < 2) return
    val step = size.width / (AppLimits.MOTION_TRACE_SAMPLES_MAX - 1)
    var prev: Offset? = null
    for ((i, m) in history.withIndex()) {
        val point = Offset(i * step, size.height / 2f - pick(m) / scale * (size.height / 2f))
        prev?.let { drawLine(color, it, point, strokeWidth = 3f) }
        prev = point
    }
}

/** Three axes of one sensor as rolling lines, newest at the right, auto-scaled. */
@Composable
fun MotionTrace(title: String, history: List<Motion>, scaleFloor: Float, modifier: Modifier = Modifier) {
    val scale = plotScale(history, scaleFloor)
    val latest = history.lastOrNull()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = DsColors.TextDim)
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            drawRect(DsColors.SurfaceHigh, Offset.Zero, size)
            trace(history, scale, DsColors.AxisX) { it.x }
            trace(history, scale, DsColors.AxisY) { it.y }
            trace(history, scale, DsColors.AxisZ) { it.z }
        }
        if (latest != null) {
            Text(stringResource(R.string.motion_values, signed(latest.x), signed(latest.y), signed(latest.z)), style = NumberStyle, maxLines = 1, softWrap = false)
            if (latest.unit == MotionUnit.RAW_COUNTS) Text(stringResource(R.string.label_motion_raw), color = DsColors.TextDim)
        } else {
            Text(stringResource(R.string.motion_none), color = DsColors.TextDim)
        }
    }
}

@Composable
fun MotionTraces(ui: LiveUi, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MotionTrace(stringResource(R.string.label_gyro), ui.feel.gyroHistory, GYRO_SCALE_FLOOR)
        MotionTrace(stringResource(R.string.label_accel), ui.feel.accelHistory, ACCEL_SCALE_FLOOR)
    }
}
