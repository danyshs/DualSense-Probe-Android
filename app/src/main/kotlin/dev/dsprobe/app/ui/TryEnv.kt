package dev.dsprobe.app.ui

import dev.dsprobe.app.audio.AndroidMic
import dev.dsprobe.app.audio.AndroidTone
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.output.OutputsShown
import dev.dsprobe.app.route.Reduction
import dev.dsprobe.app.route.RouteState

/**
 * What a Try it needs from the app: the route, what Android locks, the outputs, and the two audio
 * tests.
 */
class TryEnv(
    val state: RouteState,
    val locked: List<Reduction>,
    val shown: OutputsShown,
    val outputs: OutputController,
    val mic: AndroidMic,
    val tone: AndroidTone,
    val onMicTap: () -> Unit,
)
