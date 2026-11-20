package dev.dsprobe.app.debug

import dev.dsprobe.app.route.OutputRequest

private fun hex(v: UByte): String = "%02x".format(v.toInt())

/** The request as the Outputs panel made it, short enough for one trace line. */
fun requestText(req: OutputRequest): String = when (req) {
    is OutputRequest.Rumble -> "Rumble(left ${req.left}, right ${req.right}, ${req.durationMs} ms)"
    is OutputRequest.RumblePattern -> "RumblePattern(${req.steps.size} steps)"
    is OutputRequest.SetRumbleMode -> "SetRumbleMode(${req.mode})"
    is OutputRequest.Lightbar -> "Lightbar(rgb ${hex(req.rgb.r)}${hex(req.rgb.g)}${hex(req.rgb.b)})"
    is OutputRequest.PlayerLeds -> "PlayerLeds(mask 0x%02x)".format(req.mask)
    is OutputRequest.SetMuteLed -> "SetMuteLed(${req.mode})"
    is OutputRequest.Trigger -> "Trigger(${if (req.isRight) "R2" else "L2"}, ${req.effect.bytes.joinToString(" ") { hex(it) }.ifEmpty { "off" }}, ${req.durationMs} ms)"
    is OutputRequest.AudioTone -> "AudioTone(${req.route}, ${req.freqHz} Hz)"
}

/** The control ID a request belongs to, the same IDs the Direct USB trace uses. */
fun requestControl(req: OutputRequest): String = when (req) {
    is OutputRequest.Rumble, is OutputRequest.RumblePattern, is OutputRequest.SetRumbleMode -> "out.rumble"
    is OutputRequest.Lightbar -> "out.lightbar"
    is OutputRequest.PlayerLeds -> "out.led.player"
    is OutputRequest.SetMuteLed -> "out.led.mute"
    is OutputRequest.Trigger -> if (req.isRight) "out.trigger.r2" else "out.trigger.l2"
    is OutputRequest.AudioTone -> "out.audio.tone"
}
