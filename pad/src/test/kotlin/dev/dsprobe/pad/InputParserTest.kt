package dev.dsprobe.pad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InputParserTest {
    private val idle = Fixtures.parsed("idle")

    private fun held(name: String) = Fixtures.parsed(name).held

    private fun assertNoCrossTalk(name: String) {
        val p = Fixtures.parsed(name)
        for ((got, base) in listOf(
            p.leftX to idle.leftX,
            p.leftY to idle.leftY,
            p.rightX to idle.rightX,
            p.rightY to idle.rightY,
        )) {
            assertTrue(
                "$name: stick moved $got vs $base",
                kotlin.math.abs(got.toInt() - base.toInt()) <= CROSS_TALK_THRESHOLD_COUNTS,
            )
        }
        assertEquals("$name: dpad", Dpad.NONE, p.dpad)
        assertTrue("$name: touches", p.touches.isEmpty())
    }

    @Test fun idleReadsAsNothingPressed() {
        assertEquals(emptySet<Button>(), idle.held)
        assertEquals(Dpad.NONE, idle.dpad)
        assertEquals(0u.toUByte(), idle.l2)
        assertEquals(0u.toUByte(), idle.r2)
        assertTrue(idle.touches.isEmpty())
        assertEquals(
            listOf(0x7a, 0x84, 0x7e, 0x81),
            listOf(idle.leftX, idle.leftY, idle.rightX, idle.rightY).map {
                it.toInt()
            },
        )
    }

    @Test fun eachSingleButtonFixtureHoldsOnlyThatButton() {
        val cases = mapOf(
            "square" to Button.SQUARE, "cross" to Button.CROSS, "circle" to Button.CIRCLE,
            "triangle" to Button.TRIANGLE,
            "l1" to Button.L1, "r1" to Button.R1, "l2-button" to Button.L2,
            "r2-button" to Button.R2,
            "create" to Button.CREATE, "options" to Button.OPTIONS, "l3" to Button.L3,
            "r3" to Button.R3,
            "ps" to Button.PS, "mute" to Button.MUTE,
        )
        assertEquals(14, cases.size)
        for ((file, button) in cases) {
            assertEquals(file, setOf(button), held(file))
            assertTrue("$file: dpad", Fixtures.parsed(file).dpad == Dpad.NONE)
        }
    }

    @Test fun buttonFixturesShowNoStrayStickOrTouch() {
        val buttons = listOf(
            "square", "cross", "circle", "triangle", "l1", "r1", "l2-button", "r2-button",
            "options", "ps",
        )
        for (file in buttons) {
            assertNoCrossTalk(file)
        }
    }

    @Test fun touchpadClickFixtureHasTheClickBitAmongTheTriggersTheUserHeld() {
        assertEquals(
            setOf(Button.L1, Button.L2, Button.R2, Button.TOUCHPAD_CLICK),
            held("touchpad-click"),
        )
    }

    @Test fun analogTriggers() {
        assertEquals(0x1a, Fixtures.parsed("l2-button").l2.toInt())
        assertEquals(0x2a, Fixtures.parsed("r2-button").r2.toInt())
        assertEquals(0xff, Fixtures.parsed("l2-full").l2.toInt())
        assertEquals(0, Fixtures.parsed("l2-full").r2.toInt())
        assertEquals(0xff, Fixtures.parsed("r2-full").r2.toInt())
        assertEquals(0, Fixtures.parsed("r2-full").l2.toInt())
        assertEquals(setOf(Button.L2), held("l2-full"))
        assertEquals(setOf(Button.R2), held("r2-full"))
    }

    @Test fun stickAxesReachBothEnds() {
        assertEquals(0xfb, Fixtures.parsed("left-x-max").leftX.toInt())
        assertEquals(0x02, Fixtures.parsed("left-x-min").leftX.toInt())
        assertEquals(0xff, Fixtures.parsed("left-y-max").leftY.toInt())
        assertEquals(0x01, Fixtures.parsed("left-y-min").leftY.toInt())
        assertEquals(0xfe, Fixtures.parsed("right-x-max").rightX.toInt())
        assertEquals(0x05, Fixtures.parsed("right-x-min").rightX.toInt())
        assertEquals(0xfb, Fixtures.parsed("right-y-max").rightY.toInt())
        assertEquals(0x04, Fixtures.parsed("right-y-min").rightY.toInt())
        for (f in listOf(
            "left-x-max",
            "left-x-min",
            "left-y-max",
            "left-y-min",
            "right-x-max",
            "right-x-min",
            "right-y-max",
            "right-y-min",
        )) {
            assertEquals(f, emptySet<Button>(), held(f))
        }
    }

    @Test fun dpadEightDirectionsAndNone() {
        val cases = mapOf(
            "dpad-n" to Dpad.N, "dpad-ne" to Dpad.NE, "dpad-e" to Dpad.E, "dpad-se" to Dpad.SE,
            "dpad-s" to Dpad.S, "dpad-sw" to Dpad.SW, "dpad-w" to Dpad.W, "dpad-nw" to Dpad.NW,
            "dpad-none" to Dpad.NONE,
        )
        assertEquals(9, cases.size)
        for ((file, dir) in cases) assertEquals(file, dir, Fixtures.parsed(file).dpad)
    }

    @Test fun dpadNorthWestFixtureAlsoHasTriangleAndShouldersHeld() {
        assertEquals(
            setOf(Button.TRIANGLE, Button.L1, Button.R1, Button.L2, Button.R2),
            held("dpad-nw"),
        )
    }

    @Test fun oneFingerTouch() {
        assertEquals(listOf(Touch(1, 1919, 359)), Fixtures.parsed("touch-0").touches)
        assertEquals(listOf(Touch(1, 1631, 680)), Fixtures.parsed("touchpad-click").touches)
    }

    @Test fun twoFingerTouch() {
        assertEquals(
            listOf(Touch(1, 1521, 779), Touch(2, 0, 260)),
            Fixtures.parsed("touch-1").touches,
        )
        assertTrue(Button.TOUCHPAD_CLICK in held("touch-1"))
    }

    @Test fun liftedContactIsNotReported() {
        // create.hex carries stale coordinates under a lifted flag (bit 7 set)
        assertTrue(Fixtures.parsed("create").touches.isEmpty())
    }

    @Test fun motionSensorsAreSignedLittleEndian() {
        assertEquals(Vec3Raw(-1, -5, -6), idle.gyro)
        assertEquals(Vec3Raw(56, 8257, 1064), idle.accel)
    }

    @Test fun statusBytes() {
        assertEquals(BatteryRaw(levelNibble = 8, chargingNibble = 2), idle.battery)
        assertEquals(false, idle.headphonesIn)
        assertEquals(false, idle.micIn)
        assertEquals(0xe9, idle.sequence!!.toInt())
    }

    @Test fun statusBitsSetAreRead() {
        val r = Fixtures.report("idle").copyOf()
        r[54] = (r[54].toInt() or 0x03).toByte()
        val p = (parseUsbInput(r) as ParseResult.Ok).input
        assertEquals(true, p.headphonesIn)
        assertEquals(true, p.micIn)
    }

    @Test fun wrongLengthIsRejected() {
        for (size in listOf(0, 1, 63, 65)) {
            val r = parseUsbInput(ByteArray(size).also { if (size > 0) it[0] = 1 })
            assertEquals(
                "size $size",
                RejectReason.WRONG_LENGTH,
                (r as ParseResult.Rejected).reason,
            )
        }
    }

    @Test fun wrongIdIsRejected() {
        val r = Fixtures.report("idle").copyOf().also { it[0] = 0x11 }
        assertEquals(RejectReason.WRONG_ID, (parseUsbInput(r) as ParseResult.Rejected).reason)
    }

    @Test fun undefinedDpadNibbleIsRejected() {
        for (nibble in 9..15) {
            val r = Fixtures.report("idle").copyOf().also { it[8] = nibble.toByte() }
            assertEquals(
                "nibble $nibble",
                RejectReason.BAD_DPAD,
                (parseUsbInput(r) as ParseResult.Rejected).reason,
            )
        }
    }

    @Test fun touchOutsideThePadIsRejected() {
        val r = Fixtures.report("touch-0").copyOf().also {
            it[34] = 0xff.toByte()
            it[35] =
                0xff.toByte()
        } // x = 4095
        assertEquals(RejectReason.BAD_TOUCH, (parseUsbInput(r) as ParseResult.Rejected).reason)
    }

    @Test fun neverThrowsOnArbitraryPadData() {
        val rnd = Random(42)
        var ok = 0
        for (i in 0 until 2_000) {
            val bytes = rnd.nextBytes(if (i % 2 == 0) 64 else rnd.nextInt(0, 80))
            if (bytes.isNotEmpty() && i % 4 == 0) bytes[0] = 1
            if (parseUsbInput(bytes) is ParseResult.Ok) ok++
        }
        assertTrue("some random reports should parse, got $ok", ok > 0)
    }

    private companion object {
        const val CROSS_TALK_THRESHOLD_COUNTS = 8
    }
}
