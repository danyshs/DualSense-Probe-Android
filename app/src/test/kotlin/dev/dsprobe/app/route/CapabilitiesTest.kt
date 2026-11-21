package dev.dsprobe.app.route

import android.media.AudioDeviceInfo
import android.view.InputDevice
import android.view.MotionEvent
import dev.dsprobe.app.R
import dev.dsprobe.pad.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun lockedSet(a: AndroidAnswers): Set<Feature> = reductions(a).map { it.feature }.toSet()

private val MUTE_KEY = KeyAnswer(91, "mute", Button.MUTE, present = true)

class CapabilitiesTest {
    @Test fun theMeasuredPixelAnswersLockExactlyWhatAndroidOffersNothingFor() {
        assertEquals(
            setOf(
                Feature.MUTE_BUTTON,
                Feature.TOUCHPAD_TWO_FINGERS,
                Feature.MUTE_LED,
                Feature.ADAPTIVE_TRIGGERS,
                Feature.SPEAKER,
                Feature.HEADPHONES,
                Feature.HD_HAPTICS,
            ),
            lockedSet(usbPixelAnswers()),
        )
    }

    @Test fun lightsAndBatteryStayUnlockedEvenWhereTheyWereSeenToFailOnUsb() {
        val locked = lockedSet(usbPixelAnswers())
        for (f in listOf(Feature.LIGHTBAR, Feature.PLAYER_LEDS, Feature.BATTERY)) {
            assertTrue(
                "$f",
                f !in locked,
            )
        }
        // the raw answer Android gave on USB is a battery answer, not an absence
        assertEquals(-0.01f, usbPixelAnswers().battery!!.capacity)
    }

    @Test fun noAnswerAtAllLocksEveryFeatureWithTheOneReason() {
        val all = reductions(fakeAnswers())
        assertEquals(Feature.entries.toList(), all.map { it.feature })
        assertTrue(all.all { it.reasonRes == R.string.reason_android_offers_nothing })
    }

    @Test fun aFeatureAndroidNewlyOffersComesOutUnlocked() {
        val base = usbPixelAnswers()
        val cases: List<Pair<AndroidAnswers, Set<Feature>>> = listOf(
            base.copy(keys = base.keys!! + MUTE_KEY) to setOf(Feature.MUTE_BUTTON),
            base.copy(touchPointersSeen = 2) to setOf(Feature.TOUCHPAD_TWO_FINGERS),
            base.copy(lights = base.lights!! + LightAnswer("pad:mute", 1)) to
                setOf(Feature.MUTE_LED),
            base.copy(audioOutputs = listOf(AudioAnswer(AudioDeviceInfo.TYPE_USB_HEADSET))) to
                setOf(Feature.SPEAKER, Feature.HEADPHONES, Feature.HD_HAPTICS),
        )
        val before = lockedSet(base)
        for ((upgraded, unlocked) in cases) {
            assertEquals(before - unlocked, lockedSet(upgraded))
        }
    }

    @Test fun nothingAndroidOffersUnlocksAdaptiveTriggers() {
        val everything = usbPixelAnswers().copy(
            lights =
            usbPixelAnswers().lights!! + LightAnswer("pad:mute", 1) +
                LightAnswer("pad:trigger", 1),
            audioOutputs = listOf(AudioAnswer(AudioDeviceInfo.TYPE_USB_HEADSET)),
            touchPointersSeen = 2,
            keys = usbPixelAnswers().keys!! + MUTE_KEY,
        )
        assertEquals(setOf(Feature.ADAPTIVE_TRIGGERS), lockedSet(everything))
    }

    @Test fun aQuestionThatThrewLocksItsFeaturesAndIsListedInTheSheet() {
        val broken = usbPixelAnswers().copy(
            vibratorIds = null,
            battery = null,
            failed = listOf("vibratorIds", "batteryState"),
        )
        val locked = lockedSet(broken)
        assertTrue(Feature.RUMBLE in locked && Feature.BATTERY in locked)
        val rows = answerRows(broken)
        assertTrue(rows.any { it.labelRes == R.string.answer_vibrators && it.value == "no answer" })
        assertEquals(
            listOf("vibratorIds", "batteryState"),
            rows.filter {
                it.labelRes ==
                    R.string.answer_failed
            }.map { it.value },
        )
    }

    @Test fun anEmptyVibratorListIsAnAnswerNotAFailure() {
        val none = usbPixelAnswers().copy(vibratorIds = emptyList())
        assertTrue(Feature.RUMBLE in lockedSet(none))
        assertEquals(
            "none",
            answerRows(none).single {
                it.labelRes == R.string.answer_vibrators
            }.value,
        )
    }

    @Test fun allFivePlayerLightsAreNeeded() {
        val four = usbPixelAnswers().let {
            it.copy(lights = it.lights!!.filterNot { l -> l.name.endsWith("player-3") })
        }
        assertTrue(Feature.PLAYER_LEDS in lockedSet(four))
        assertTrue(Feature.LIGHTBAR !in lockedSet(four))
    }

    @Test fun theLightbarNeedsTheLightNamedIndicator() {
        val renamed = usbPixelAnswers().let {
            it.copy(
                lights = it.lights!!.map { l ->
                    if (l.name == LIGHTBAR_NAME) {
                        l.copy(name = "other")
                    } else {
                        l
                    }
                },
            )
        }
        assertTrue(Feature.LIGHTBAR in lockedSet(renamed))
        assertTrue(Feature.PLAYER_LEDS !in lockedSet(renamed))
    }

    @Test fun eitherMouseOrTouchpadSourceOffersThePointer() {
        val base = usbPixelAnswers()
        assertTrue(
            Feature.TOUCHPAD_POINTER in lockedSet(base.copy(sources = InputDevice.SOURCE_GAMEPAD)),
        )
        assertTrue(
            Feature.TOUCHPAD_POINTER !in lockedSet(base.copy(sources = InputDevice.SOURCE_MOUSE)),
        )
        assertTrue(
            Feature.TOUCHPAD_POINTER !in lockedSet(
                base.copy(sources = InputDevice.SOURCE_TOUCHPAD),
            ),
        )
    }

    @Test fun sticksAndTriggersAreDecidedByTheirOwnAxes() {
        val base = usbPixelAnswers()
        val onlyTriggers = base.copy(
            axes = base.axes!!.filter {
                it.axis ==
                    MotionEvent.AXIS_LTRIGGER
            },
        )
        assertTrue(Feature.STICKS in lockedSet(onlyTriggers))
        assertTrue(Feature.TRIGGERS_ANALOG !in lockedSet(onlyTriggers))
        val onlySticks = base.copy(axes = base.axes.filter { it.axis == MotionEvent.AXIS_X })
        assertTrue(Feature.STICKS !in lockedSet(onlySticks))
        assertTrue(Feature.TRIGGERS_ANALOG in lockedSet(onlySticks))
    }

    @Test fun aKeyThatAndroidDoesNotListDoesNotCountAsAnOfferedButton() {
        val base = usbPixelAnswers()
        assertTrue(
            Feature.BUTTONS in
                lockedSet(base.copy(keys = base.keys!!.map { it.copy(present = false) })),
        )
        assertTrue(Feature.BUTTONS !in lockedSet(base))
        assertTrue(
            Feature.MUTE_BUTTON in
                lockedSet(base.copy(keys = base.keys + MUTE_KEY.copy(present = false))),
        )
    }

    @Test fun theMicNeedsAUsbHeadsetInputAndGyroOrAccelNeedsASensor() {
        val base = usbPixelAnswers()
        assertTrue(
            Feature.MICROPHONE in
                lockedSet(
                    base.copy(audioInputs = listOf(AudioAnswer(AudioDeviceInfo.TYPE_BUILTIN_MIC))),
                ),
        )
        assertTrue(Feature.MICROPHONE !in lockedSet(base))
        assertTrue(Feature.MOTION_SENSORS in lockedSet(base.copy(sensors = emptyList())))
        assertTrue(Feature.MOTION_SENSORS !in lockedSet(base))
    }

    @Test fun lockedFeaturesComeInFeatureOrder() {
        val order = reductions(usbPixelAnswers()).map { it.feature.ordinal }
        assertEquals(order.sorted(), order)
    }

    @Test fun theSheetShowsAndroidsRawBatteryAnswerAsGiven() {
        val rows = answerRows(usbPixelAnswers())
        assertEquals(
            "present true capacity -0.01 status 1",
            rows.single {
                it.labelRes ==
                    R.string.answer_battery
            }.value,
        )
        assertEquals(13, rows.count { it.labelRes == R.string.answer_key })
        assertEquals(6, rows.count { it.labelRes == R.string.answer_light })
        assertEquals("0, 1", rows.single { it.labelRes == R.string.answer_vibrators }.value)
        assertEquals("1", rows.single { it.labelRes == R.string.answer_pointers }.value)
        assertEquals(
            "no answer",
            answerRows(fakeAnswers(keys = null)).first {
                it.labelRes ==
                    R.string.answer_key
            }.value,
        )
    }
}
