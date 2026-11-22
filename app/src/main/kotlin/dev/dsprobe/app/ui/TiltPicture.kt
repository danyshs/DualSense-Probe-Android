package dev.dsprobe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dsprobe.app.R
import dev.dsprobe.app.debug.Angles
import dev.dsprobe.app.debug.attitudeOf
import dev.dsprobe.app.live.Motion
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.ui.theme.NumberStyle
import java.util.Locale

private const val CAMERA_DISTANCE_DP = 12f // far enough that a turned picture is not clipped

/**
 * The home screen's controller picture turned in all three axes: pitch and roll from gravity,
 * yaw from the integrated gyro. [onReset] zeroes the yaw; null when the card has its own Reset.
 */
@Composable
fun TiltPicture(gravity: Motion?, angles: Angles?, onReset: (() -> Unit)?) {
    val attitude = attitudeOf(gravity, angles?.z ?: 0f)
    val description = stringResource(R.string.tilt_picture_cd)
    val distance = with(LocalDensity.current) { CAMERA_DISTANCE_DP.dp.toPx() }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ControllerDrawing(
            LiveUi.EMPTY,
            emptySet(),
            OutputsShown.IDLE,
            Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .semantics { contentDescription = description }
                .graphicsLayer {
                    rotationX = attitude.pitchDeg
                    rotationY = attitude.rollDeg
                    rotationZ = attitude.yawDeg
                    cameraDistance = distance
                },
        )
        Text(
            stringResource(
                R.string.tilt_angles,
                String.format(Locale.ROOT, "%+4.0f", attitude.pitchDeg),
                String.format(Locale.ROOT, "%+4.0f", attitude.rollDeg),
                String.format(Locale.ROOT, "%+4.0f", attitude.yawDeg),
            ),
            style = NumberStyle,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            stringResource(R.string.tilt_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onReset != null) QuietButton(onReset) { OneLine(R.string.gyro_reset) }
    }
}
