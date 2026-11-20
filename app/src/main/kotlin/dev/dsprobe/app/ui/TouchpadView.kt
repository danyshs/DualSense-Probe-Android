package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.app.ui.theme.ChartColors
import dev.dsprobe.app.ui.theme.NumberStyle
import dev.dsprobe.pad.Limits

private const val PAD_ASPECT = 1919f / 1079f
private const val FINGER_PX = 16f
private const val RIPPLE_RADIUS_PX = 90f

/**
 * Up to two fingers and a click ripple. In Android mode the "finger" is the mouse pointer scaled to
 * the rectangle, and says so. [lockedReason] greys the two-finger part with its reason.
 */
@Composable
fun TouchpadView(ui: LiveUi, lockedReason: Int?, androidMode: Boolean, modifier: Modifier = Modifier) {
    val c = drawColors()
    val touches = ui.frame.state.touches
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row { Text(stringResource(R.string.label_touchpad), color = c.textDim) }
        Canvas(Modifier.fillMaxWidth().aspectRatio(PAD_ASPECT)) {
            drawRect(c.surfaceHigh, Offset.Zero, size)
            drawRect(c.outline, Offset.Zero, size, style = Stroke(width = 2f))
            for (p in ui.feel.touchTrail) {
                val age = (ui.feel.nowMs - p.atMs).toFloat() / AppLimits.STICK_TRAIL_MS
                drawCircle(c.accent.copy(alpha = (1f - age).coerceIn(0f, 1f) * 0.4f), FINGER_PX * 0.6f, Offset(p.x * size.width, p.y * size.height))
            }
            for (r in ui.feel.ripples) {
                val progress = ((ui.feel.nowMs - r.atMs).toFloat() / AppLimits.CLICK_RIPPLE_MS).coerceIn(0f, 1f)
                drawCircle(c.accent.copy(alpha = 1f - progress), RIPPLE_RADIUS_PX * progress, Offset(r.x * size.width, r.y * size.height), style = Stroke(width = 4f))
            }
            for (t in touches) drawCircle(c.accent, FINGER_PX, Offset(t.x * size.width, t.y * size.height))
            if (lockedReason != null) drawLock(c, Offset(size.width - LOCK_PX * 1.5f, LOCK_PX * 1.5f), LOCK_PX)
        }
        if (androidMode) Text(stringResource(R.string.touchpad_android_note), color = c.textDim)
        // two fixed lines (one per finger slot) so a second finger never adds height
        Text(touchText(touches), style = NumberStyle, minLines = TOUCH_LINES, maxLines = TOUCH_LINES, softWrap = false)
        if (lockedReason != null) Text(stringResource(lockedReason), color = c.textDim)
    }
}

private const val LOCK_PX = 28f
private const val TOUCH_LINES = 2

@Composable
private fun touchText(touches: List<TouchPoint>): String {
    if (touches.isEmpty()) return stringResource(R.string.touch_none)
    val parts = ArrayList<String>(touches.size)
    for (t in touches) {
        parts.add(
            if (t.isPointer) {
                stringResource(R.string.touch_pointer, signed(t.x), signed(t.y))
            } else {
                stringResource(R.string.touch_finger, t.id, Math.round(t.x * Limits.TOUCH_X_PX_MAX), Math.round(t.y * Limits.TOUCH_Y_PX_MAX))
            },
        )
    }
    return parts.joinToString("\n")
}
