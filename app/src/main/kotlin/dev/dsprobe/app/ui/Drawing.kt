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

@Composable
fun drawColors(): DrawColors {
    val s = MaterialTheme.colorScheme
    return remember(s) {
        DrawColors(
            s.primary,
            s.outlineVariant,
            s.surfaceContainer,
            s.surfaceContainerHigh,
            s.onSurface,
            s.onSurfaceVariant,
            s.tertiary,
            s.outline,
        )
    }
}

/** Base colour towards the accent by [alpha], with a soft halo that grows with it. */
fun DrawScope.glowCircle(c: DrawColors, center: Offset, radius: Float, alpha: Float) {
    if (alpha > 0f) {
        drawCircle(
            c.accent.copy(alpha = alpha * HALO_ALPHA_MAX),
            radius * HALO_RADIUS_RATIO,
            center,
        )
    }
    drawCircle(lerp(c.surfaceHigh, c.accent, alpha), radius, center)
    drawCircle(c.outline, radius, center, style = Stroke(width = RIM_WIDTH_PX))
}

fun DrawScope.glowRect(c: DrawColors, topLeft: Offset, size: Size, alpha: Float) {
    if (alpha > 0f) {
        drawRect(
            c.accent.copy(alpha = alpha * HALO_ALPHA_MAX),
            topLeft - Offset(HALO_PAD_PX, HALO_PAD_PX),
            Size(size.width + 2 * HALO_PAD_PX, size.height + 2 * HALO_PAD_PX),
        )
    }
    drawRect(lerp(c.surfaceHigh, c.accent, alpha), topLeft, size)
    drawRect(c.outline, topLeft, size, style = Stroke(width = RIM_WIDTH_PX))
}

/** A small padlock: body and shackle. */
fun DrawScope.drawLock(c: DrawColors, center: Offset, sizePx: Float, color: Color = c.locked) {
    val body = Size(sizePx, sizePx * LOCK_BODY_HEIGHT)
    val bodyTop =
        Offset(center.x - sizePx / 2f, center.y - body.height / 2f + sizePx * LOCK_BODY_DROP)
    drawRect(color, bodyTop, body)
    val shackle = Size(sizePx * LOCK_SHACKLE_WIDTH, sizePx * LOCK_SHACKLE_HEIGHT)
    drawArc(
        color,
        startAngle = HALF_TURN_DEGREES,
        sweepAngle = HALF_TURN_DEGREES,
        useCenter = false,
        topLeft = Offset(center.x - shackle.width / 2f, bodyTop.y - shackle.height / 2f),
        size = shackle,
        style = Stroke(width = sizePx * LOCK_SHACKLE_STROKE),
    )
}

/** Fixed-width number text: sign and two decimals. */
fun signed(v: Float): String = String.format(Locale.ROOT, "%+.2f", v)

private const val HALO_ALPHA_MAX = 0.35f
private const val HALO_RADIUS_RATIO = 1.5f
private const val HALO_PAD_PX = 6f
private const val RIM_WIDTH_PX = 2f

// padlock parts as fractions of its size
private const val LOCK_BODY_HEIGHT = 0.7f
private const val LOCK_BODY_DROP = 0.15f
private const val LOCK_SHACKLE_WIDTH = 0.6f
private const val LOCK_SHACKLE_HEIGHT = 0.8f
private const val LOCK_SHACKLE_STROKE = 0.14f
private const val HALF_TURN_DEGREES = 180f // the arc's top half, from 9 o'clock over to 3
