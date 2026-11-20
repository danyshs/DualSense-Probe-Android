package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.ui.theme.ChartColors
import dev.dsprobe.app.ui.theme.NumberStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val TRIGGER_COUNTS_MAX = 255
private const val PERCENT_MAX = 100
private const val MARKER_PX = 4f
private const val TRIGGER_COLUMN_DP = 80 // fits "255 100%" in 14 sp monospace (about 67 dp)

/** A filling bar with a peak marker and the 0-255 value and percentage under it. */
@Composable
fun TriggerBar(label: String, value: Float, peak: Peak, modifier: Modifier = Modifier) {
    val c = drawColors()
    val counts = (value * TRIGGER_COUNTS_MAX).roundToInt()
    val percent = (value * PERCENT_MAX).roundToInt()
    Column(modifier.width(TRIGGER_COLUMN_DP.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = c.textDim)
        Canvas(Modifier.width(32.dp).height(140.dp)) {
            drawRect(c.surfaceHigh, Offset.Zero, size)
            val fill = size.height * value
            drawRect(c.accent, Offset(0f, size.height - fill), Size(size.width, fill))
            val markerY = size.height * (1f - peak.value)
            drawRect(c.text, Offset(0f, markerY.coerceIn(0f, size.height - MARKER_PX)), Size(size.width, MARKER_PX))
        }
        // fixed 8 chars, one line: the value can never reflow the layout
        Text(String.format(Locale.ROOT, "%3d %3d%%", counts, percent), style = NumberStyle, maxLines = 1, softWrap = false)
    }
}
