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
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.ui.theme.ChartColors
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import dev.dsprobe.pad.MuteLed
import kotlin.math.sin

private const val DRAWING_ASPECT = 1.6f
private const val TILT_PITCH_PX_PER_DEGREE = 0.6f
private const val MUTE_PULSE_MS = 300f // a guess: tune by eye
private const val OUTLINE_STROKE_PX = 1.5f
private const val BODY_STROKE_PX = 3f

// layout: fractions of the canvas; our own outline, not Sony artwork
private val FACE_CENTER = Offset(0.78f, 0.42f)
private val DPAD_CENTER = Offset(0.22f, 0.42f)
private val L3_CENTER = Offset(0.38f, 0.68f)
private val R3_CENTER = Offset(0.62f, 0.68f)
private val PS_CENTER = Offset(0.5f, 0.52f)

/** A rectangle as fractions of the canvas. */
private class FracRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** One cubic segment of the body outline, its two control points then its end. */
private class Cubic(val first: Offset, val second: Offset, val end: Offset)

private val BODY_START = Offset(0.14f, 0.22f)
private val BODY_OUTLINE = listOf(
    Cubic(Offset(0.30f, 0.12f), Offset(0.70f, 0.12f), Offset(0.86f, 0.22f)),
    Cubic(Offset(0.96f, 0.30f), Offset(0.99f, 0.70f), Offset(0.93f, 0.86f)),
    Cubic(Offset(0.88f, 0.98f), Offset(0.78f, 0.96f), Offset(0.72f, 0.84f)),
    Cubic(Offset(0.66f, 0.74f), Offset(0.34f, 0.74f), Offset(0.28f, 0.84f)),
    Cubic(Offset(0.22f, 0.96f), Offset(0.12f, 0.98f), Offset(0.07f, 0.86f)),
    Cubic(Offset(0.01f, 0.70f), Offset(0.04f, 0.30f), Offset(0.14f, 0.22f)),
)

// sizes below are fractions of the canvas height
private const val FACE_SPACING = 0.12f
private const val FACE_RADIUS = 0.075f
private const val DPAD_ARM = 0.07f
private const val DPAD_THICKNESS_PER_ARM = 1.1f
private const val PS_RADIUS = 0.035f
private const val MUTE_LOCK_SIZE = 0.06f
private const val PLAYER_LED_RADIUS = 0.014f
private const val MUTE_LED_RADIUS = 0.016f
private const val STICK_RADIUS = 0.11f
private const val STICK_TRAVEL_PER_RADIUS = 0.55f
private const val STICK_DOT_PER_RADIUS = 0.35f

private val BUMPER_BOXES = listOf(
    Button.L1 to FracRect(0.10f, 0.13f, 0.16f, 0.05f),
    Button.R1 to FracRect(0.74f, 0.13f, 0.16f, 0.05f),
    Button.L2 to FracRect(0.10f, 0.05f, 0.16f, 0.05f),
    Button.R2 to FracRect(0.74f, 0.05f, 0.16f, 0.05f),
)
private val CENTRE_BOXES = listOf(
    Button.TOUCHPAD_CLICK to FracRect(0.37f, 0.21f, 0.26f, 0.20f),
    Button.CREATE to FracRect(0.28f, 0.24f, 0.05f, 0.04f),
    Button.OPTIONS to FracRect(0.67f, 0.24f, 0.05f, 0.04f),
    Button.MUTE to FracRect(0.47f, 0.60f, 0.06f, 0.035f),
)
private val MUTE_LOCK_CENTER = Offset(0.5f, 0.62f)

// the lightbar is a thin bar down each side of the touchpad; the player LEDs sit under it
private val LIGHTBAR_BOXES = listOf(
    FracRect(0.350f, 0.21f, 0.012f, 0.20f),
    FracRect(0.638f, 0.21f, 0.012f, 0.20f),
)
private val PLAYER_LED_FIRST = Offset(0.42f, 0.445f)
private const val PLAYER_LED_STEP = 0.04f // across, as a fraction of the width
private val MUTE_LED_CENTER = Offset(0.56f, 0.617f)

private fun DrawScope.at(p: Offset) = Offset(p.x * size.width, p.y * size.height)

private fun DrawScope.topLeft(r: FracRect) = Offset(r.left * size.width, r.top * size.height)

private fun DrawScope.sizeOf(r: FracRect) = Size(r.width * size.width, r.height * size.height)

private fun DrawScope.bodyPath(): Path = Path().apply {
    val start = at(BODY_START)
    moveTo(start.x, start.y)
    for (segment in BODY_OUTLINE) {
        val first = at(segment.first)
        val second = at(segment.second)
        val end = at(segment.end)
        cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
    }
    close()
}

private fun DrawScope.drawFaceButtons(c: DrawColors, ui: LiveUi) {
    val unit = size.height * FACE_SPACING
    val radius = size.height * FACE_RADIUS
    val center = at(FACE_CENTER)
    val spots = listOf(
        Button.TRIANGLE to Offset(0f, -unit),
        Button.CROSS to Offset(0f, unit),
        Button.SQUARE to Offset(-unit, 0f),
        Button.CIRCLE to Offset(unit, 0f),
    )
    for ((button, delta) in spots) glowCircle(c, center + delta, radius, buttonGlow(ui, button))
}

private fun DrawScope.drawDpad(c: DrawColors, ui: LiveUi) {
    val center = at(DPAD_CENTER)
    val arm = size.height * DPAD_ARM
    val thick = arm * DPAD_THICKNESS_PER_ARM
    val half = thick / 2f
    val arms = listOf(
        Dpad.N to Pair(Offset(-half, -arm - half), Size(thick, arm + half)),
        Dpad.S to Pair(Offset(-half, 0f), Size(thick, arm + half)),
        Dpad.W to Pair(Offset(-arm - half, -half), Size(arm + half, thick)),
        Dpad.E to Pair(Offset(0f, -half), Size(arm + half, thick)),
    )
    for ((dir, rect) in arms) {
        val alpha =
            glowAlpha(dir in armsOf(ui.frame.state.dpad), ui.feel.armActiveMs[dir], ui.feel.nowMs)
        glowRect(c, center + rect.first, rect.second, alpha)
    }
}

private fun DrawScope.drawBumpers(c: DrawColors, ui: LiveUi) {
    for ((button, box) in BUMPER_BOXES) {
        glowRect(c, topLeft(box), sizeOf(box), buttonGlow(ui, button))
    }
}

private fun DrawScope.drawCentre(c: DrawColors, ui: LiveUi, locked: Set<Feature>) {
    val h = size.height
    for ((button, box) in CENTRE_BOXES) {
        glowRect(c, topLeft(box), sizeOf(box), buttonGlow(ui, button))
    }
    glowCircle(c, at(PS_CENTER), h * PS_RADIUS, buttonGlow(ui, Button.PS))
    if (Feature.MUTE_BUTTON in locked) drawLock(c, at(MUTE_LOCK_CENTER), h * MUTE_LOCK_SIZE)
}

private fun DrawScope.drawLightsAndLeds(c: DrawColors, ui: LiveUi, shown: OutputsShown) {
    val w = size.width
    val h = size.height
    val bar = Color(shown.lightbar.r.toInt(), shown.lightbar.g.toInt(), shown.lightbar.b.toInt())
    for (box in LIGHTBAR_BOXES) {
        drawRect(bar, topLeft(box), sizeOf(box))
        drawRect(c.outline, topLeft(box), sizeOf(box), style = Stroke(width = OUTLINE_STROKE_PX))
    }
    val ledRadius = h * PLAYER_LED_RADIUS
    for (i in 0 until AppLimits.PLAYER_LED_COUNT) {
        val on = shown.playerLeds and (1 shl i) != 0
        val spot = Offset((PLAYER_LED_FIRST.x + PLAYER_LED_STEP * i) * w, PLAYER_LED_FIRST.y * h)
        drawCircle(if (on) Color.White else c.surfaceHigh, ledRadius, spot)
        drawCircle(c.outline, ledRadius, spot, style = Stroke(width = OUTLINE_STROKE_PX))
    }
    // a 0..1 pulse from the sine's -1..1
    val pulse = (1f + sin(ui.feel.nowMs / MUTE_PULSE_MS)) / 2f
    val muteAlpha = when (shown.muteLed) {
        MuteLed.OFF -> 0f
        MuteLed.ON -> 1f
        MuteLed.PULSE -> pulse
    }
    val mute = at(MUTE_LED_CENTER)
    val muteRadius = h * MUTE_LED_RADIUS
    drawCircle(c.surfaceHigh, muteRadius, mute)
    drawCircle(c.warn.copy(alpha = muteAlpha), muteRadius, mute)
    drawCircle(c.outline, muteRadius, mute, style = Stroke(width = OUTLINE_STROKE_PX))
}

private fun DrawScope.drawSticks(c: DrawColors, ui: LiveUi) {
    val s = ui.frame.state
    val radius = size.height * STICK_RADIUS
    val travel = radius * STICK_TRAVEL_PER_RADIUS
    val sticks = listOf(
        Triple(L3_CENTER, Button.L3, Offset(s.leftX, s.leftY)),
        Triple(R3_CENTER, Button.R3, Offset(s.rightX, s.rightY)),
    )
    for ((spot, button, value) in sticks) {
        val center = at(spot)
        glowCircle(c, center, radius, buttonGlow(ui, button))
        drawCircle(c.accent, radius * STICK_DOT_PER_RADIUS, center + value * travel)
    }
}

/** The glow of one button right now: full while held, fading after, a latched tap still shows. */
fun buttonGlow(ui: LiveUi, button: Button): Float =
    glowAlpha(button in ui.frame.state.held, ui.feel.buttonActiveMs[button], ui.feel.nowMs)

@Composable
fun ControllerDrawing(
    ui: LiveUi,
    locked: Set<Feature>,
    shown: OutputsShown,
    modifier: Modifier = Modifier,
) {
    val c = drawColors()
    val tilt = tiltOf(ui.frame.state.accel)
    Canvas(modifier.aspectRatio(DRAWING_ASPECT)) {
        withTransform({
            if (tilt != null) {
                rotate(tilt.rollDegrees, center)
                translate(0f, tilt.pitchDegrees * TILT_PITCH_PX_PER_DEGREE)
            }
        }) {
            drawPath(bodyPath(), c.surface)
            drawPath(bodyPath(), c.outline, style = Stroke(width = BODY_STROKE_PX))
            drawBumpers(c, ui)
            drawDpad(c, ui)
            drawFaceButtons(c, ui)
            drawCentre(c, ui, locked)
            drawLightsAndLeds(c, ui, shown)
            drawSticks(c, ui)
        }
    }
}
