package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle
import kotlin.math.sqrt

/** A stick circle: moving dot, fading trail, a rim that lights at full deflection, and the values. */
@Composable
fun StickView(label: String, x: Float, y: Float, trail: List<TrailPoint>, nowMs: Long, modifier: Modifier = Modifier) {
    val atRim = sqrt(x * x + y * y) >= AppLimits.STICK_FULL_DEFLECTION_RATIO_MIN
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = DsColors.TextDim)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val radius = size.minDimension / 2f - RIM_PX
            drawCircle(DsColors.SurfaceHigh, radius, center)
            drawCircle(if (atRim) DsColors.Accent else DsColors.Outline, radius, center, style = Stroke(width = RIM_PX))
            for (p in trail) {
                val age = (nowMs - p.atMs).toFloat() / AppLimits.STICK_TRAIL_MS
                drawCircle(DsColors.Accent.copy(alpha = (1f - age).coerceIn(0f, 1f) * TRAIL_ALPHA_MAX), DOT_PX * TRAIL_DOT_RATIO, center + Offset(p.x, p.y) * radius)
            }
            drawCircle(DsColors.Accent, DOT_PX, center + Offset(x, y) * radius)
        }
        Text(stringResource(R.string.stick_values, signed(x), signed(y)), style = NumberStyle)
    }
}

private const val RIM_PX = 6f
private const val DOT_PX = 14f
private const val TRAIL_DOT_RATIO = 0.7f
private const val TRAIL_ALPHA_MAX = 0.5f
