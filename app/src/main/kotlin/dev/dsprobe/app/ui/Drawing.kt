package dev.dsprobe.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import java.util.Locale

/** The colours the canvases draw with, taken from the Material colour roles once per theme. */
class DrawColors(
    val accent: Color,
    val outline: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val textDim: Color,
    val warn: Color,
    val locked: Color,
)

@Composable
fun drawColors(): DrawColors {
    val s = MaterialTheme.colorScheme
    return remember(s) { DrawColors(s.primary, s.outlineVariant, s.surfaceContainer, s.surfaceContainerHigh, s.onSurface, s.onSurfaceVariant, s.tertiary, s.outline) }
}

/** Base colour towards the accent by [alpha], with a soft halo that grows with it. */
fun DrawScope.glowCircle(c: DrawColors, center: Offset, radius: Float, alpha: Float) {
    if (alpha > 0f) drawCircle(c.accent.copy(alpha = alpha * HALO_ALPHA_MAX), radius * HALO_RADIUS_RATIO, center)
    drawCircle(lerp(c.surfaceHigh, c.accent, alpha), radius, center)
    drawCircle(c.outline, radius, center, style = Stroke(width = RIM_WIDTH_PX))
}

fun DrawScope.glowRect(c: DrawColors, topLeft: Offset, size: Size, alpha: Float) {
    if (alpha > 0f) {
        drawRect(c.accent.copy(alpha = alpha * HALO_ALPHA_MAX), topLeft - Offset(HALO_PAD_PX, HALO_PAD_PX), Size(size.width + 2 * HALO_PAD_PX, size.height + 2 * HALO_PAD_PX))
    }
    drawRect(lerp(c.surfaceHigh, c.accent, alpha), topLeft, size)
    drawRect(c.outline, topLeft, size, style = Stroke(width = RIM_WIDTH_PX))
}

/** A small padlock: body and shackle. */
fun DrawScope.drawLock(c: DrawColors, center: Offset, sizePx: Float, color: Color = c.locked) {
    val body = Size(sizePx, sizePx * 0.7f)
    val bodyTop = Offset(center.x - sizePx / 2f, center.y - body.height / 2f + sizePx * 0.15f)
    drawRect(color, bodyTop, body)
    drawArc(
        color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(center.x - sizePx * 0.3f, bodyTop.y - sizePx * 0.4f), size = Size(sizePx * 0.6f, sizePx * 0.8f),
        style = Stroke(width = sizePx * 0.14f),
    )
}

/** Fixed-width number text: sign and two decimals. */
fun signed(v: Float): String = String.format(Locale.ROOT, "%+.2f", v)

private const val HALO_ALPHA_MAX = 0.35f
private const val HALO_RADIUS_RATIO = 1.5f
private const val HALO_PAD_PX = 6f
private const val RIM_WIDTH_PX = 2f
