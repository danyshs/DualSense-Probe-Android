package dev.dsprobe.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object DsColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF171C26)
    val SurfaceHigh = Color(0xFF222A38)
    val Outline = Color(0xFF3A4458)
    val Text = Color(0xFFE8ECF4)
    val TextDim = Color(0xFF8A94A8)
    val Accent = Color(0xFF3DD6FF)
    val Warn = Color(0xFFFFB347)
    val Locked = Color(0xFF5A6478)
    val AxisX = Color(0xFFFF6B6B)
    val AxisY = Color(0xFF6BFF9E)
    val AxisZ = Color(0xFF6BA8FF)
}

/** Numbers in a fixed-width face so they don't jitter as they change. */
val NumberStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = DsColors.Text)

@Composable
fun DsProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DsColors.Accent,
            background = DsColors.Background,
            surface = DsColors.Surface,
            onBackground = DsColors.Text,
            onSurface = DsColors.Text,
            onPrimary = DsColors.Background,
        ),
        content = content,
    )
}
