package dev.dsprobe.pad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OutputBuilderTest {
    private val off = OutputState.allOff()
    private fun rgb(r: Int, g: Int, b: Int) = Rgb(r.toUByte(), g.toUByte(), b.toUByte())
    private fun fields(vararg f: OutputField) = f.toSet()

    /** Golden file name -> (state, changed) that must reproduce it byte for byte. */
    private fun cases(): Map<String, Pair<OutputState, Set<OutputField>>> {
        val m = LinkedHashMap<String, Pair<OutputState, Set<OutputField>>>()
        val lightbars = mapOf(
            "000000" to rgb(0, 0, 0),
            "0000ff" to rgb(0, 0, 255),
            "00ff00" to rgb(0, 255, 0),
            "00ffff" to rgb(0, 255, 255),
            "ff0000" to rgb(255, 0, 0),
            "ffff00" to rgb(255, 255, 0),
            "ffffff" to rgb(255, 255, 255),
        )
        for ((hex, c) in lightbars) {
            m["lightbar-$hex"] =
                off.copy(lightbar = c) to fields(OutputField.LIGHTBAR)
        }
        for (mask in listOf(0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1f)) {
            m["player-leds-%02x".format(mask)] =
                off.copy(playerLedMask = mask) to fields(OutputField.PLAYER_LEDS)
        }
        m["mute-led-0"] = off.copy(muteLed = MuteLed.OFF) to fields(OutputField.MUTE_LED)
        m["mute-led-1"] = off.copy(muteLed = MuteLed.ON) to fields(OutputField.MUTE_LED)
        m["mute-led-2"] = off.copy(muteLed = MuteLed.PULSE) to fields(OutputField.MUTE_LED)
        val half = 0x80.toUByte()
        val rumble = fields(OutputField.RUMBLE)
        m["rumble-right"] = off.copy(rumbleRight = half) to rumble
        m["rumble-left"] = off.copy(rumbleLeft = half) to rumble
        m["rumble-both"] = off.copy(rumbleLeft = half, rumbleRight = half) to rumble
        m["rumble-off"] = off to rumble
        m["rumble-v2-both"] =
            off.copy(rumbleLeft = half, rumbleRight = half, rumbleMode = RumbleMode.V2) to rumble
        m["rumble-v2-off"] = off.copy(rumbleMode = RumbleMode.V2) to rumble
        val push = TriggerEffect.resistance(0x30u, 0xc0u)
        m["r2-resistance"] = off.copy(r2Effect = push) to fields(OutputField.RIGHT_TRIGGER)
        m["r2-off"] = off to fields(OutputField.RIGHT_TRIGGER)
        m["l2-resistance"] = off.copy(l2Effect = push) to fields(OutputField.LEFT_TRIGGER)
        m["l2-off"] = off to fields(OutputField.LEFT_TRIGGER)
        m["route-speaker"] =
            off.copy(audioRoute = AudioRoute.SPEAKER, speakerVolume = 0x64u) to
            fields(OutputField.AUDIO_ROUTE)
        m["route-headphones"] =
            off.copy(audioRoute = AudioRoute.HEADPHONES, headphoneVolume = 0x3fu) to
            fields(OutputField.AUDIO_ROUTE)
        m["mic-on"] = off.copy(micVolume = 0x40u) to fields(OutputField.MIC_VOLUME)
        m["release-all-off"] = off to RELEASE_FIELDS
        return m
    }

    @Test fun everyGoldenFileHasACaseAndNoCaseIsMissingItsFile() {
        val files = Fixtures.names("golden")
        assertEquals(31, files.size)
        assertEquals(files, cases().keys.sorted())
    }

    @Test fun matchesEveryGoldenReportByteForByte() {
        for ((name, case) in cases()) {
            val want = Fixtures.bytes("golden/$name.hex")
            assertEquals(name, Limits.USB_OUTPUT_REPORT_BYTES, want.size)
            assertArrayEquals(name, want, buildUsbOutput(case.first, case.second))
        }
    }

    @Test fun oneChangeNeverTouchesAnotherFeature() {
        val loud = OutputState(
            rumbleLeft = 200u, rumbleRight = 100u, rumbleMode = RumbleMode.V2,
            lightbar = rgb(
                1,
                2,
                3,
            ),
            playerLedMask = 0x1f, muteLed = MuteLed.ON, r2Effect = TriggerEffect.resistance(1u, 2u),
            l2Effect = TriggerEffect.resistance(3u, 4u), audioRoute = AudioRoute.SPEAKER,
            speakerVolume = 0x64u, headphoneVolume = 0x7fu, micVolume = 0x40u,
        )
        val want = ByteArray(Limits.USB_OUTPUT_REPORT_BYTES)
        want[0] = 2
        want[2] = 4
        want[45] = 1
        want[46] = 2
        want[47] = 3
        assertArrayEquals(want, buildUsbOutput(loud, fields(OutputField.LIGHTBAR)))
        val player = ByteArray(Limits.USB_OUTPUT_REPORT_BYTES)
        player[0] = 2
        player[2] = 0x10
        player[44] = 0x1f
        assertArrayEquals(player, buildUsbOutput(loud, fields(OutputField.PLAYER_LEDS)))
        val left = ByteArray(Limits.USB_OUTPUT_REPORT_BYTES)
        left[0] = 2
        left[1] = 8
        left[22] = 1
        left[23] = 3
        left[24] = 4
        assertArrayEquals(left, buildUsbOutput(loud, fields(OutputField.LEFT_TRIGGER)))
    }

    @Test fun routeNoneSendsNoAudioBytes() {
        val r = buildUsbOutput(off.copy(micVolume = 0u), fields(OutputField.AUDIO_ROUTE))
        val want = ByteArray(Limits.USB_OUTPUT_REPORT_BYTES).also { it[0] = 2 }
        assertArrayEquals(want, r)
    }

    @Test fun refusesOutOfRangeValues() {
        assertThrows(IllegalArgumentException::class.java) { TriggerEffect.resistance(0u, 0xc1u) }
        assertThrows(IllegalArgumentException::class.java) { off.copy(playerLedMask = 0x20) }
        assertThrows(IllegalArgumentException::class.java) { off.copy(speakerVolume = 0x65u) }
        assertThrows(IllegalArgumentException::class.java) { off.copy(headphoneVolume = 0x80u) }
        assertThrows(IllegalArgumentException::class.java) { off.copy(micVolume = 0x41u) }
        assertThrows(IllegalArgumentException::class.java) { TriggerEffect(List(12) { 0u }) }
        assertThrows(IllegalArgumentException::class.java) { buildUsbOutput(off, emptySet()) }
    }

    @Test fun everyCapAcceptsItselfAndRefusesOnePast() {
        val all = RELEASE_FIELDS + OutputField.entries
        fun accepts(s: OutputState) {
            buildUsbOutput(s, all)
        }
        fun refuses(make: () -> OutputState) =
            assertThrows(IllegalArgumentException::class.java) { buildUsbOutput(make(), all) }
        accepts(off.copy(playerLedMask = Limits.PLAYER_LED_MASK_MAX))
        refuses { off.copy(playerLedMask = Limits.PLAYER_LED_MASK_MAX + 1) }
        accepts(off.copy(speakerVolume = Limits.SPEAKER_VOLUME_MAX.toUByte()))
        refuses { off.copy(speakerVolume = (Limits.SPEAKER_VOLUME_MAX + 1).toUByte()) }
        accepts(off.copy(headphoneVolume = Limits.HEADPHONE_VOLUME_MAX.toUByte()))
        refuses { off.copy(headphoneVolume = (Limits.HEADPHONE_VOLUME_MAX + 1).toUByte()) }
        accepts(off.copy(micVolume = Limits.MIC_VOLUME_MAX.toUByte()))
        refuses { off.copy(micVolume = (Limits.MIC_VOLUME_MAX + 1).toUByte()) }
        val full = TriggerEffect(List(Limits.TRIGGER_EFFECT_BYTES_MAX) { 1u })
        accepts(off.copy(r2Effect = full, l2Effect = full))
        refuses {
            off.copy(r2Effect = TriggerEffect(List(Limits.TRIGGER_EFFECT_BYTES_MAX + 1) { 1u }))
        }
        refuses {
            off.copy(l2Effect = TriggerEffect(List(Limits.TRIGGER_EFFECT_BYTES_MAX + 1) { 1u }))
        }
        assertEquals(0x64, Limits.SPEAKER_VOLUME_MAX) // FACTS P34
    }

    @Test fun reportIsAlways63BytesWithId2() {
        for (field in OutputField.entries) {
            val r = buildUsbOutput(off, fields(field))
            assertEquals(63, r.size)
            assertEquals(2, r[0].toInt())
        }
    }
}
