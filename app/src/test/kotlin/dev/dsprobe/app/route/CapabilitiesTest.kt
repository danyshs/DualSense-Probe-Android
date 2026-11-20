package dev.dsprobe.app.route

import dev.dsprobe.app.R
import dev.dsprobe.app.pad.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CapabilitiesTest {
    private fun table(route: Route, transport: Transport) =
        reductions(route, transport).associate { it.feature to it.reasonRes }

    @Test fun androidOverUsbGreysExactlyTheTableRows() {
        assertEquals(
            mapOf(
                Feature.MUTE_BUTTON to R.string.reason_mute_button,
                Feature.TOUCHPAD_TWO_FINGERS to R.string.reason_two_fingers,
                Feature.LIGHTBAR to R.string.reason_lights_usb,
                Feature.PLAYER_LEDS to R.string.reason_lights_usb,
                Feature.BATTERY to R.string.reason_battery_usb,
                Feature.MUTE_LED to R.string.reason_no_android_api,
                Feature.ADAPTIVE_TRIGGERS to R.string.reason_no_android_api,
                Feature.SPEAKER to R.string.reason_no_audio_output,
                Feature.HEADPHONES to R.string.reason_no_audio_output,
                Feature.HD_HAPTICS to R.string.reason_no_audio_output,
            ),
            table(Route.ANDROID, Transport.USB),
        )
    }

    @Test fun androidOverBluetoothKeepsLightsAndBatteryButLosesTheMic() {
        val t = table(Route.ANDROID, Transport.BLUETOOTH)
        assertEquals(R.string.reason_mic_bluetooth, t[Feature.MICROPHONE])
        for (f in listOf(Feature.LIGHTBAR, Feature.PLAYER_LEDS, Feature.BATTERY, Feature.RUMBLE, Feature.MOTION_SENSORS, Feature.STICKS)) {
            assertTrue("$f should work over Bluetooth", f !in t)
        }
        assertEquals(8, t.size)
    }

    @Test fun usbShowsSticksMotionAndTheMicEvenThoughTheyAreUntested() {
        val t = table(Route.ANDROID, Transport.USB)
        for (f in listOf(Feature.STICKS, Feature.TRIGGERS_ANALOG, Feature.MOTION_SENSORS, Feature.MICROPHONE, Feature.RUMBLE)) {
            assertTrue("$f is shown, not greyed", f !in t)
        }
    }

    @Test fun directUsbReducesNothing() {
        assertEquals(emptyList<Reduction>(), reductions(Route.DIRECT_USB, Transport.USB))
    }

    @Test fun directUsbOverBluetoothNeverExists() {
        try {
            reductions(Route.DIRECT_USB, Transport.BLUETOOTH)
            fail("expected a refusal")
        } catch (_: IllegalArgumentException) {
            // wanted
        }
    }

    @Test fun reductionsComeInFeatureOrder() {
        val order = reductions(Route.ANDROID, Transport.USB).map { it.feature.ordinal }
        assertEquals(order.sorted(), order)
    }
}
