package dev.dsprobe.app.route

import dev.dsprobe.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val API_LEVEL = 36
private val QUIET_PROBE = ApiProbe(
    API_LEVEL,
    listOf(
        ClassMethods(
            "InputDevice",
            listOf("getName", "getLightsManager", "getBatteryState", "isEnabled", "hasKeys"),
        ),
        ClassMethods("VibratorManager", listOf("vibrate", "cancel", "getVibratorIds")),
    ),
)

private fun lockedOf(a: AndroidAnswers): Set<Feature> = reductions(a).map { it.feature }.toSet()

private fun withProbe(probe: ApiProbe?) = usbPixelAnswers().copy(probe = probe)

class CapabilitiesProbeTest {
    @Test fun wordsAreSplitAtCapitalsAndLowercased() {
        assertEquals(listOf("set", "trigger", "effect"), wordsOf("setTriggerEffect"))
        assertEquals(listOf("get", "lights", "manager"), wordsOf("getLightsManager"))
    }

    @Test fun aWordMatchesWholeOrPluralNeverInsideAnotherWord() {
        val probe =
            ApiProbe(
                API_LEVEL,
                listOf(
                    ClassMethods(
                        "InputDevice",
                        listOf(
                            "isEnabled",
                            "getLeds",
                            "getLightsManager",
                            "setMuted",
                            "hasBattery",
                        ),
                    ),
                ),
            )
        val hits = probe.matches().map { it.method to it.keyword }
        assertEquals(
            listOf(
                "InputDevice.getLeds" to "led",
                "InputDevice.getLightsManager" to "light",
                "InputDevice.hasBattery" to "battery",
            ),
            hits,
        )
    }

    @Test fun withoutAMatchTheFeatureStaysLockedAndTheReasonCountsWhatWasProbed() {
        val a = withProbe(QUIET_PROBE)
        for (f in listOf(
            Feature.ADAPTIVE_TRIGGERS,
            Feature.MUTE_LED,
            Feature.MUTE_BUTTON,
            Feature.HD_HAPTICS,
        )) {
            assertTrue(
                "$f",
                f in lockedOf(a),
            )
        }
        val r = reductions(a).single { it.feature == Feature.ADAPTIVE_TRIGGERS }
        assertEquals(R.string.reason_android_offers_nothing_probed, r.reasonRes)
        assertEquals(listOf(QUIET_PROBE.methodCount, API_LEVEL), r.reasonArgs)
        assertEquals(8, QUIET_PROBE.methodCount)
    }

    @Test fun aFeatureNotInTheProbeListKeepsThePlainReason() {
        val a = withProbe(QUIET_PROBE).copy(touchPointersSeen = 0)
        val r = reductions(a).single { it.feature == Feature.TOUCHPAD_TWO_FINGERS }
        assertEquals(R.string.reason_android_offers_nothing, r.reasonRes)
        assertEquals(emptyList<Int>(), r.reasonArgs)
    }

    @Test fun aMatchingMethodNameShowsMayOfferAndUnlocksOnlyThatFeature() {
        val fake =
            ApiProbe(
                API_LEVEL,
                listOf(ClassMethods("InputDevice", listOf("getName", "setTriggerEffect"))),
            )
        val a = withProbe(fake)
        assertEquals(
            listOf(MayOffer(Feature.ADAPTIVE_TRIGGERS, "InputDevice.setTriggerEffect")),
            mayOffer(a),
        )
        assertFalse(Feature.ADAPTIVE_TRIGGERS in lockedOf(a))
        assertTrue(Feature.MUTE_LED in lockedOf(a))
        val rows = answerRows(a)
        assertTrue(
            rows.any {
                it.labelRes == R.string.answer_may_offer &&
                    it.value == "ADAPTIVE_TRIGGERS InputDevice.setTriggerEffect"
            },
        )
        assertTrue(
            rows.any {
                it.labelRes == R.string.answer_probe_match &&
                    it.value.startsWith("InputDevice.setTriggerEffect (trigger)")
            },
        )
    }

    @Test fun eachProbeWordUnlocksItsOwnFeatures() {
        val cases = mapOf(
            "setMuteState" to setOf(Feature.MUTE_LED, Feature.MUTE_BUTTON),
            "playHapticChannel" to setOf(Feature.HD_HAPTICS),
            "getTriggerEffects" to setOf(Feature.ADAPTIVE_TRIGGERS),
        )
        for ((method, unlocked) in cases) {
            val a = withProbe(ApiProbe(API_LEVEL, listOf(ClassMethods("Light", listOf(method)))))
            assertEquals(method, lockedOf(withProbe(QUIET_PROBE)) - unlocked, lockedOf(a))
        }
    }

    @Test fun withNoProbeAnswerNothingIsMayOffer() {
        assertEquals(emptyList<MayOffer>(), mayOffer(withProbe(null)))
        assertEquals(
            "no answer",
            answerRows(withProbe(null)).single {
                it.labelRes ==
                    R.string.answer_probe
            }.value,
        )
    }

    @Test fun aKeyEventWhileMuteIsWatchedUnlocksTheMuteButton() {
        val base = usbPixelAnswers()
        assertTrue(Feature.MUTE_BUTTON in lockedOf(base))
        val learned = learnMuteKey(base, muteWatched = true, keyCode = 91)
        assertEquals(91, learned.muteKeyCode)
        assertFalse(Feature.MUTE_BUTTON in lockedOf(learned))
    }

    @Test fun aKeyEventOutsideTheMutePromptOrOfAMappedButtonUnlocksNothing() {
        val base = usbPixelAnswers()
        assertSame(base, learnMuteKey(base, muteWatched = false, keyCode = 91))
        assertSame(
            base,
            learnMuteKey(
                base,
                muteWatched = true,
                keyCode = android.view.KeyEvent.KEYCODE_BUTTON_A,
            ),
        )
        val learned = learnMuteKey(base, true, 91)
        assertSame(learned, learnMuteKey(learned, true, 92))
        assertNull(base.muteKeyCode)
    }

    @Test fun theLogLinesCarryTheProbeAndSayNothingWasCalled() {
        val fake =
            ApiProbe(API_LEVEL, listOf(ClassMethods("InputDevice", listOf("setTriggerEffect"))))
        val lines = answerLines(withProbe(fake))
        assertTrue(lines.contains("android.probe api_level=36 methods=1"))
        assertTrue(
            lines.contains(
                "android.probe.match method=InputDevice.setTriggerEffect keyword=trigger " +
                    "called=never",
            ),
        )
        assertTrue(
            lines.contains(
                "android.may_offer feature=ADAPTIVE_TRIGGERS method=InputDevice.setTriggerEffect",
            ),
        )
        assertTrue(
            lines.any {
                it.startsWith("android.battery present=true capacity=-0.01 status=1")
            },
        )
    }

    @Test fun theSheetListsEveryPresentKeyAndTheMappedOnesButNotTheRest() {
        val keys = listOf(
            KeyAnswer(96, "KEYCODE_BUTTON_A", dev.dsprobe.pad.Button.CROSS, present = true),
            KeyAnswer(97, "KEYCODE_BUTTON_B", dev.dsprobe.pad.Button.CIRCLE, present = false),
            KeyAnswer(120, "KEYCODE_SYSRQ", null, present = true),
        )
        val rows = answerRows(usbPixelAnswers().copy(keys = keys)).filter {
            it.labelRes ==
                R.string.answer_key
        }
        assertEquals(
            listOf(
                "KEYCODE_BUTTON_A (96) true",
                "KEYCODE_BUTTON_B (97) false",
                "KEYCODE_SYSRQ (120) true",
            ),
            rows.map {
                it.value
            },
        )
    }
}
