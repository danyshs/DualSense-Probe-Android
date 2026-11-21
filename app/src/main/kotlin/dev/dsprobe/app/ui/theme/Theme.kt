package dev.dsprobe.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Numbers in a fixed-width face so they don't jitter as they change. The colour is inherited. */
val NumberStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)

private val DARK_SCHEME = darkColorScheme(
    primary = Color(0xFF3DD6FF),
    onPrimary = Color(0xFF00323F),
    primaryContainer = Color(0xFF004D61),
    onPrimaryContainer = Color(0xFFBDEAFF),
    secondary = Color(0xFF8FB7C9),
    onSecondary = Color(0xFF0B2531),
    secondaryContainer = Color(0xFF2A3F4C),
    onSecondaryContainer = Color(0xFFD2E6F2),
    tertiary = Color(0xFFFFB347),
    onTertiary = Color(0xFF3F2800),
    tertiaryContainer = Color(0xFF5A3C00),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF450008),
    errorContainer = Color(0xFF5C1620),
    onErrorContainer = Color(0xFFFFD9DC),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF222A38),
    onSurfaceVariant = Color(0xFF9AA4B8),
    outline = Color(0xFF5A6478),
    outlineVariant = Color(0xFF3A4458),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF12161E),
    surfaceContainer = Color(0xFF171C26),
    surfaceContainerHigh = Color(0xFF222A38),
    surfaceContainerHighest = Color(0xFF2C3546),
)

@Composable
fun DsProbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DARK_SCHEME, content = content)
}
