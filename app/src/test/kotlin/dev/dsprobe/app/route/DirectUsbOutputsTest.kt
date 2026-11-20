package dev.dsprobe.app.route

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val POLL_STEP_MS = 5L
private const val POLL_MS_MAX = 3_000L
private const val IDLE_READ_MS = 5L
private const val HALF: UByte = 0x80u

/** A pad that accepts every write and records it; [firmware] is what a feature read answers. */
private class RecordingLink(private val firmware: ByteArray?) : UsbLink {
    val writes = CopyOnWriteArrayList<ByteArray>()

    override fun readReport(timeoutMs: Int): ReadResult {
        Thread.sleep(IDLE_READ_MS)
        return ReadResult.Timeout
    }

    override fun writeReport(report: ByteArray): Boolean {
        writes.add(report.copyOf())
        return true
    }

    override fun readFeature(reportId: Int, length: Int): ByteArray? = firmware

    override fun releaseInterfaces() = Unit

    override fun close() = Unit
}

private fun rgb(hex: String): Rgb {
    val v = hex.toInt(16)
    return Rgb((v shr 16).toUByte(), ((v shr 8) and 0xff).toUByte(), (v and 0xff).toUByte())
}

private val PREFIXES = listOf("lightbar-", "player-leds-", "mute-led-", "rumble-")

class DirectUsbOutputsTest {
    private val log = CopyOnWriteArrayList<String>()
    private var route: DirectUsbRoute? = null
    private lateinit var link: RecordingLink

    private fun open(firmware: ByteArray?): DirectUsbRoute {
        link = RecordingLink(firmware)
        return DirectUsbRoute(link, LiveState(), EventFeed(), { log.add("fault $it") }, { log.add(it) }).also {
            route = it
            it.start()
        }
    }

    @After fun closeWhateverIsLeft() {
        route?.release()
    }

    private fun waitForWrites(n: Int) {
        var waited = 0L
        while (link.writes.size < n) {
            check(waited < POLL_MS_MAX) { "gave up waiting for write $n, have ${link.writes.size}" }
            Thread.sleep(POLL_STEP_MS)
            waited += POLL_STEP_MS
        }
    }

    /** Sends each request in turn and checks the report it produced against the golden file of that name. */
    private fun check(cases: Map<String, OutputRequest>) {
        val r = route!!
        var expected = 0
        for ((name, req) in cases) {
            assertEquals(name, ApiAnswer.Ok, r.send(req))
            expected += 1
            waitForWrites(expected)
            assertArrayEquals(name, TestFixtures.bytes("golden/$name.hex"), link.writes[expected - 1])
        }
    }

    @Test fun everyLightbarColourBecomesItsGoldenReport() {
        open(null)
        check(listOf("000000", "0000ff", "00ff00", "00ffff", "ff0000", "ffff00", "ffffff").associate { "lightbar-$it" to OutputRequest.Lightbar(rgb(it)) })
    }

    @Test fun everyPlayerLedMaskBecomesItsGoldenReport() {
        open(null)
        check(listOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x1f, 0x00).associate { "player-leds-%02x".format(it) to OutputRequest.PlayerLeds(it) })
    }

    @Test fun theMuteLedOffOnAndPulseBecomeTheirGoldenReports() {
        open(null)
        check(MuteLed.entries.associate { "mute-led-${it.ordinal}" to OutputRequest.SetMuteLed(it) })
    }

    @Test fun withoutAFirmwareAnswerRumbleUsesTheCompatibleFlags() {
        open(null)
        check(
            linkedMapOf(
                "rumble-right" to OutputRequest.Rumble(0u, HALF, 1_000),
                "rumble-left" to OutputRequest.Rumble(HALF, 0u, 1_000),
                "rumble-both" to OutputRequest.Rumble(HALF, HALF, 1_000),
                "rumble-off" to OutputRequest.Rumble(0u, 0u, 1),
            ),
        )
        assertTrue(log.any { it.contains("firmware report unreadable") })
    }

    @Test fun aNewFirmwareMakesRumbleUseTheV2Flags() {
        open(TestFixtures.bytes("feature/firmware-0x20.hex"))
        check(
            linkedMapOf(
                "rumble-v2-both" to OutputRequest.Rumble(HALF, HALF, 1_000),
                "rumble-v2-off" to OutputRequest.Rumble(0u, 0u, 1),
            ),
        )
        assertTrue(log.any { it.contains("0x0630") && it.contains("V2") })
    }

    @Test fun anOldFirmwareKeepsTheCompatibleFlags() {
        val old = TestFixtures.bytes("feature/firmware-0x20.hex").also {
            it[44] = 0x14
            it[45] = 0x02
        }
        open(old)
        check(linkedMapOf("rumble-both" to OutputRequest.Rumble(HALF, HALF, 1_000)))
        assertTrue(log.any { it.contains("0x0214") && it.contains("COMPATIBLE") })
    }

    @Test fun rumbleStopsByItselfAtItsDurationWithTheGoldenOffReport() {
        open(null)
        route!!.send(OutputRequest.Rumble(HALF, HALF, 40))
        waitForWrites(2)
        assertArrayEquals(TestFixtures.bytes("golden/rumble-both.hex"), link.writes[0])
        assertArrayEquals(TestFixtures.bytes("golden/rumble-off.hex"), link.writes[1])
    }

    @Test fun everyGoldenOfTheFourOutputGroupsIsCoveredByATestAbove() {
        val covered = setOf(
            "000000", "0000ff", "00ff00", "00ffff", "ff0000", "ffff00", "ffffff",
        ).map { "lightbar-$it" } + listOf("00", "01", "02", "04", "08", "10", "1f").map { "player-leds-$it" } +
            listOf(0, 1, 2).map { "mute-led-$it" } +
            listOf("right", "left", "both", "off", "v2-both", "v2-off").map { "rumble-$it" }
        val onDisk = TestFixtures.names("golden").filter { n -> PREFIXES.any { n.startsWith(it) } }
        assertEquals(covered.sorted(), onDisk)
    }

    @Test fun triggersAndAudioAreRefusedUntilTheirStepsAndNothingIsSent() {
        open(null)
        val r = route!!
        assertTrue(r.send(OutputRequest.Trigger(true, dev.dsprobe.pad.TriggerEffect.OFF)) is ApiAnswer.Failed)
        assertTrue(r.send(OutputRequest.AudioTone(dev.dsprobe.pad.AudioRoute.SPEAKER, 440)) is ApiAnswer.Failed)
        Thread.sleep(3 * IDLE_READ_MS)
        assertEquals(0, link.writes.size)
    }

    @Test fun aRequestAfterTheReleaseIsRefused() {
        open(null)
        route!!.release()
        assertTrue(route!!.send(OutputRequest.Lightbar(rgb("ff0000"))) is ApiAnswer.Failed)
        assertTrue(route!!.send(OutputRequest.Rumble(HALF, HALF, 100)) is ApiAnswer.Failed)
    }

    @Test fun aMaskPastFiveBitsIsOurBugAndIsRefusedLoudly() {
        open(null)
        assertThrows(IllegalArgumentException::class.java) { route!!.send(OutputRequest.PlayerLeds(0x20)) }
    }
}
