package dev.dsprobe.app.debug

import dev.dsprobe.app.output.LiveRoute
import dev.dsprobe.app.output.OutputController
import dev.dsprobe.app.route.ApiAnswer
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.PadRoute
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.MuteLed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingRoute : PadRoute {
    val sent = ArrayList<OutputRequest>()

    override fun send(req: OutputRequest): ApiAnswer {
        sent.add(req)
        return ApiAnswer.Ok
    }

    override fun stop() = Unit
}

private fun fire(choice: TryChoice): List<OutputRequest> {
    val route = RecordingRoute()
    val controller = OutputController({ LiveRoute(route, Route.DIRECT_USB) }, { 1_000L }, { })
    fireChoice(choice, controller)
    return route.sent
}

class TryItTest {
    @Test fun everyChoiceSendsSomethingThroughTheControllerAndTheTriggerOffSendsBoth() {
        for (choice in TryChoice.entries) assertTrue(choice.name, fire(choice).isNotEmpty())
        assertEquals(2, fire(TryChoice.TRIGGER_OFF).size)
    }

    @Test fun theMuteChoicesSendTheirOwnMode() {
        val modes = listOf(TryChoice.MUTE_OFF to MuteLed.OFF, TryChoice.MUTE_ON to MuteLed.ON, TryChoice.MUTE_PULSE to MuteLed.PULSE)
        for ((choice, mode) in modes) assertEquals(listOf<OutputRequest>(OutputRequest.SetMuteLed(mode)), fire(choice))
    }

    @Test fun theBigButtonOfEachKindIsItsFirstChoiceAndEveryOutputKindHasChoices() {
        for (kind in TryKind.entries) {
            if (kind == TryKind.MIC || kind == TryKind.SPEAKER || kind == TryKind.HEADPHONES) assertTrue(kind.name, choicesOf(kind).isEmpty())
            else assertTrue(kind.name, choicesOf(kind).isNotEmpty())
        }
        assertEquals(TryChoice.RUMBLE_BOTH, choicesOf(TryKind.RUMBLE).first())
        assertEquals(TryChoice.MUTE_PULSE, choicesOf(TryKind.MUTE_LED).first())
    }

    @Test fun everythingOffReleasesRumbleTriggersLightbarLedsAndMuteLed() {
        val sent = fire(TryChoice.ALL_OFF)
        assertTrue(sent.any { it is OutputRequest.Lightbar })
        assertTrue(sent.any { it is OutputRequest.PlayerLeds })
        assertTrue(sent.any { it is OutputRequest.SetMuteLed })
        assertEquals(2, sent.count { it is OutputRequest.Trigger })
        assertTrue(sent.any { it is OutputRequest.Rumble })
    }

    @Test fun notSureDismissesTheQuestionWithoutLoggingAnAnswer() {
        val route = RecordingRoute()
        val log = ArrayList<String>()
        val controller = OutputController({ LiveRoute(route, Route.ANDROID) }, { 1_000L }, { log.add(it) })
        controller.setPlayerLeds(0x10)
        assertTrue(controller.shown.value.ask != null)
        controller.dismissAsk()
        assertEquals(null, controller.shown.value.ask)
        assertTrue(log.isEmpty())
    }
}
