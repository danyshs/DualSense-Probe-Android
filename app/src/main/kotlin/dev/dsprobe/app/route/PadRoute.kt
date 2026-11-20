package dev.dsprobe.app.route

import dev.dsprobe.pad.AudioRoute
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb
import dev.dsprobe.pad.TriggerEffect

/** What the Outputs panel can ask of a route; each route answers per request. */
sealed interface OutputRequest {
    data class Rumble(val left: UByte, val right: UByte, val durationMs: Int) : OutputRequest
    data class Lightbar(val rgb: Rgb) : OutputRequest
    data class PlayerLeds(val mask: Int) : OutputRequest
    data class SetMuteLed(val mode: MuteLed) : OutputRequest
    data class Trigger(val isRight: Boolean, val effect: TriggerEffect) : OutputRequest
    data class AudioTone(val route: AudioRoute, val freqHz: Int) : OutputRequest
}

sealed interface ApiAnswer {
    data object Ok : ApiAnswer
    data class Failed(val reason: String) : ApiAnswer
}

/** The one interface, two routes, one caller. */
interface PadRoute {
    fun send(req: OutputRequest): ApiAnswer

    /** Hands the pad back: cancels outputs and drops listeners. Safe to call twice. */
    fun stop()
}
