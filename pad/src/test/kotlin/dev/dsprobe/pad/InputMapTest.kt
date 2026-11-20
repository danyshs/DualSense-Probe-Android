package dev.dsprobe.pad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputMapTest {
    /** (byte, bit) pairs an entry claims. */
    private fun bitsOf(src: FieldSource): Set<Pair<Int, Int>> {
        val out = HashSet<Pair<Int, Int>>()
        val bytes = (src.widthBits + 7) / 8
        for (b in 0 until bytes) {
            val mask = src.bitMask ?: if (src.widthBits - b * 8 >= 8) 0xff else (1 shl (src.widthBits - b * 8)) - 1
            for (bit in 0 until 8) if (mask and (1 shl bit) != 0) out.add(Pair(src.byteOffset + b, bit))
        }
        return out
    }

    @Test fun idsAreUniqueAndEntriesAreWellFormed() {
        val ids = INPUT_MAP.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (src in INPUT_MAP) {
            assertEquals(0x01, src.reportId)
            assertTrue(src.id, src.factsRow.matches(Regex("P\\d+")))
        }
    }

    @Test fun everyButtonAndAxisHasExactlyOneEntry() {
        for (b in Button.entries) {
            assertEquals(b.name, 1, INPUT_MAP.count { it.id == "in.button.${b.name.lowercase()}" })
        }
        for (id in listOf(
            "in.dpad", "in.stick.left.x", "in.stick.left.y", "in.stick.right.x", "in.stick.right.y",
            "in.trigger.l2", "in.trigger.r2", "in.sequence", "in.gyro.x", "in.accel.z",
            "in.battery.level", "in.headphones_in", "in.mic_in",
        )) {
            assertEquals(id, 1, INPUT_MAP.count { it.id == id })
        }
    }

    @Test fun entriesNeverOverlap() {
        val seen = HashSet<Pair<Int, Int>>()
        for (src in INPUT_MAP) for (bit in bitsOf(src)) assertTrue("${src.id} overlaps at ${bit}", seen.add(bit))
    }

    @Test fun parserReadsExactlyTheBitsTheMapNames() {
        val base = Fixtures.report("touch-1") // both fingers down, so every touch bit is live
        val baseResult = parseUsbInput(base)
        assertTrue(baseResult is ParseResult.Ok)
        val claimed = INPUT_MAP.flatMap { bitsOf(it) }.toSet()
        var flips = 0
        for (byte in 1 until Limits.USB_INPUT_REPORT_BYTES) {
            for (bit in 0 until 8) {
                val r = base.copyOf()
                r[byte] = (r[byte].toInt() xor (1 shl bit)).toByte()
                val changed = parseUsbInput(r) != baseResult
                assertEquals("byte ${byte} bit ${bit}", Pair(byte, bit) in claimed, changed)
                flips++
            }
        }
        assertEquals(63 * 8, flips)
    }

    @Test fun eachButtonEntrySetsOnlyThatButton() {
        val idle = Fixtures.report("idle")
        val idleHeld = (parseUsbInput(idle) as ParseResult.Ok).input.held
        for (src in INPUT_MAP.filter { it.id.startsWith("in.button.") }) {
            val button = Button.valueOf(src.id.removePrefix("in.button.").uppercase())
            val pressed = idle.copyOf()
            pressed[src.byteOffset] = (pressed[src.byteOffset].toInt() or checkNotNull(src.bitMask)).toByte()
            assertEquals(src.id, setOf(button), (parseUsbInput(pressed) as ParseResult.Ok).input.held)
            assertFalse(src.id, button in idleHeld)
        }
    }
}
