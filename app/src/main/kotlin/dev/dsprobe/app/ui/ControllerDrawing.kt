package dev.dsprobe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.ui.theme.DsColors
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import dev.dsprobe.pad.MuteLed
import kotlin.math.sin

private const val DRAWING_ASPECT = 1.6f
private const val TILT_PITCH_PX_PER_DEGREE = 0.6f
private const val MUTE_PULSE_MS = 300f // a guess: tune by eye

// positions are fractions of the canvas; our own outline, not Sony artwork
private val FACE_CENTER = Offset(0.78f, 0.42f)
private val DPAD_CENTER = Offset(0.22f, 0.42f)
private val L3_CENTER = Offset(0.38f, 0.68f)
private val R3_CENTER = Offset(0.62f, 0.68f)

private fun DrawScope.at(p: Offset) = Offset(p.x * size.width, p.y * size.height)

private fun DrawScope.bodyPath(): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(0.14f * w, 0.22f * h)
        cubicTo(0.30f * w, 0.12f * h, 0.70f * w, 0.12f * h, 0.86f * w, 0.22f * h)
        cubicTo(0.96f * w, 0.30f * h, 0.99f * w, 0.70f * h, 0.93f * w, 0.86f * h)
        cubicTo(0.88f * w, 0.98f * h, 0.78f * w, 0.96f * h, 0.72f * w, 0.84f * h)
        cubicTo(0.66f * w, 0.74f * h, 0.34f * w, 0.74f * h, 0.28f * w, 0.84f * h)
        cubicTo(0.22f * w, 0.96f * h, 0.12f * w, 0.98f * h, 0.07f * w, 0.86f * h)
        cubicTo(0.01f * w, 0.70f * h, 0.04f * w, 0.30f * h, 0.14f * w, 0.22f * h)
        close()
    }
}

private fun DrawScope.drawFaceButtons(ui: LiveUi) {
    val unit = size.height * 0.12f
    val radius = size.height * 0.075f
    val center = at(FACE_CENTER)
    val spots = listOf(
        Button.TRIANGLE to Offset(0f, -unit), Button.CROSS to Offset(0f, unit),
        Button.SQUARE to Offset(-unit, 0f), Button.CIRCLE to Offset(unit, 0f),
    )
    for ((button, delta) in spots) glowCircle(center + delta, radius, buttonGlow(ui, button))
}

private fun DrawScope.drawDpad(ui: LiveUi) {
    val center = at(DPAD_CENTER)
    val arm = size.height * 0.07f
    val thick = arm * 1.1f
    val arms = listOf(
        Dpad.N to Pair(Offset(-thick / 2f, -arm - thick / 2f), Size(thick, arm + thick / 2f)),
        Dpad.S to Pair(Offset(-thick / 2f, 0f), Size(thick, arm + thick / 2f)),
        Dpad.W to Pair(Offset(-arm - thick / 2f, -thick / 2f), Size(arm + thick / 2f, thick)),
        Dpad.E to Pair(Offset(0f, -thick / 2f), Size(arm + thick / 2f, thick)),
    )
    for ((dir, rect) in arms) {
        val alpha = glowAlpha(dir in armsOf(ui.frame.state.dpad), ui.feel.armActiveMs[dir], ui.feel.nowMs)
        glowRect(center + rect.first, rect.second, alpha)
    }
}

private fun DrawScope.drawBumpers(ui: LiveUi) {
    val h = size.height
    val w = size.width
    for ((button, left) in listOf(Button.L1 to 0.10f, Button.R1 to 0.74f)) {
        glowRect(Offset(left * w, 0.13f * h), Size(0.16f * w, 0.05f * h), buttonGlow(ui, button))
    }
    for ((button, left) in listOf(Button.L2 to 0.10f, Button.R2 to 0.74f)) {
        glowRect(Offset(left * w, 0.05f * h), Size(0.16f * w, 0.05f * h), buttonGlow(ui, button))
    }
}

private fun DrawScope.drawCentre(ui: LiveUi, locked: Set<Feature>) {
    val w = size.width
    val h = size.height
    glowRect(Offset(0.37f * w, 0.21f * h), Size(0.26f * w, 0.20f * h), buttonGlow(ui, Button.TOUCHPAD_CLICK))
    glowRect(Offset(0.28f * w, 0.24f * h), Size(0.05f * w, 0.04f * h), buttonGlow(ui, Button.CREATE))
    glowRect(Offset(0.67f * w, 0.24f * h), Size(0.05f * w, 0.04f * h), buttonGlow(ui, Button.OPTIONS))
    glowCircle(at(Offset(0.5f, 0.52f)), h * 0.035f, buttonGlow(ui, Button.PS))
    val mute = Offset(0.47f * w, 0.60f * h)
    glowRect(mute, Size(0.06f * w, 0.035f * h), buttonGlow(ui, Button.MUTE))
    if (Feature.MUTE_BUTTON in locked) drawLock(Offset(0.5f * w, 0.62f * h), h * 0.06f)
}

// the lightbar is a thin bar down each side of the touchpad; the player LEDs sit under it
private fun DrawScope.drawLightsAndLeds(ui: LiveUi, shown: OutputsShown) {
    val w = size.width
    val h = size.height
    val bar = Color(shown.lightbar.r.toInt(), shown.lightbar.g.toInt(), shown.lightbar.b.toInt())
    for (left in listOf(0.350f, 0.638f)) {
        val topLeft = Offset(left * w, 0.21f * h)
        val barSize = Size(0.012f * w, 0.20f * h)
        drawRect(bar, topLeft, barSize)
        drawRect(DsColors.Outline, topLeft, barSize, style = Stroke(width = 1.5f))
    }
    for (i in 0 until AppLimits.PLAYER_LED_COUNT) {
        val on = shown.playerLeds and (1 shl i) != 0
        val spot = Offset((0.42f + 0.04f * i) * w, 0.445f * h)
        drawCircle(if (on) Color.White else DsColors.SurfaceHigh, h * 0.014f, spot)
        drawCircle(DsColors.Outline, h * 0.014f, spot, style = Stroke(width = 1.5f))
    }
    val pulse = 0.5f + 0.5f * sin(ui.feel.nowMs / MUTE_PULSE_MS)
    val muteAlpha = when (shown.muteLed) {
        MuteLed.OFF -> 0f
        MuteLed.ON -> 1f
        MuteLed.PULSE -> pulse
    }
    val mute = Offset(0.56f * w, 0.617f * h)
    drawCircle(DsColors.SurfaceHigh, h * 0.016f, mute)
    drawCircle(DsColors.Warn.copy(alpha = muteAlpha), h * 0.016f, mute)
    drawCircle(DsColors.Outline, h * 0.016f, mute, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawSticks(ui: LiveUi) {
    val s = ui.frame.state
    val radius = size.height * 0.11f
    val travel = radius * 0.55f
    val sticks = listOf(
        Triple(L3_CENTER, Button.L3, Offset(s.leftX, s.leftY)),
        Triple(R3_CENTER, Button.R3, Offset(s.rightX, s.rightY)),
    )
    for ((spot, button, value) in sticks) {
        val center = at(spot)
        glowCircle(center, radius, buttonGlow(ui, button))
        drawCircle(DsColors.Accent, radius * 0.35f, center + value * travel)
    }
}

/** The glow of one button right now: full while held, fading after, a latched tap still shows. */
fun buttonGlow(ui: LiveUi, button: Button): Float =
    glowAlpha(button in ui.frame.state.held, ui.feel.buttonActiveMs[button], ui.feel.nowMs)

@Composable
fun ControllerDrawing(ui: LiveUi, locked: Set<Feature>, shown: OutputsShown, modifier: Modifier = Modifier) {
    val tilt = tiltOf(ui.frame.state.accel)
    Canvas(modifier.aspectRatio(DRAWING_ASPECT)) {
        withTransform({
            if (tilt != null) {
                rotate(tilt.rollDegrees, center)
                translate(0f, tilt.pitchDegrees * TILT_PITCH_PX_PER_DEGREE)
            }
        }) {
            drawPath(bodyPath(), DsColors.Surface)
            drawPath(bodyPath(), DsColors.Outline, style = Stroke(width = 3f))
            drawBumpers(ui)
            drawDpad(ui)
            drawFaceButtons(ui)
            drawCentre(ui, locked)
            drawLightsAndLeds(ui, shown)
            drawSticks(ui)
        }
    }
}
