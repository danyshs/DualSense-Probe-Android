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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.app.ui.theme.NumberStyle
import kotlin.math.roundToInt

private const val TRIGGER_COUNTS_MAX = 255
private const val PERCENT_MAX = 100
private const val MARKER_PX = 4f

/** A filling bar with a peak marker and the 0-255 value and percentage under it. */
@Composable
fun TriggerBar(label: String, value: Float, peak: Peak, modifier: Modifier = Modifier) {
    val counts = (value * TRIGGER_COUNTS_MAX).roundToInt()
    val percent = (value * PERCENT_MAX).roundToInt()
    Column(modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = DsColors.TextDim)
        Canvas(Modifier.width(32.dp).height(140.dp)) {
            drawRect(DsColors.SurfaceHigh, Offset.Zero, size)
            val fill = size.height * value
            drawRect(DsColors.Accent, Offset(0f, size.height - fill), Size(size.width, fill))
            val markerY = size.height * (1f - peak.value)
            drawRect(DsColors.Text, Offset(0f, markerY.coerceIn(0f, size.height - MARKER_PX)), Size(size.width, MARKER_PX))
        }
        Text(stringResource(R.string.trigger_value, counts, percent), style = NumberStyle)
    }
}
