package dev.dsprobe.app.route

import dev.dsprobe.app.TestFixtures
import dev.dsprobe.app.live.Battery
import dev.dsprobe.app.live.MotionUnit
import dev.dsprobe.app.live.PadState
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.ParseResult
import dev.dsprobe.pad.Touch
import dev.dsprobe.pad.parseUsbInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-6f

/**
 * Expected values are worked out from the raw bytes here, not through the parser, so a wrong offset
 * shows.
 */
class UsbInputMapperTest {
    private fun raw(name: String) = TestFixtures.report(name)

    private fun mapped(name: String): PadState =
        toPadState((parseUsbInput(raw(name)) as ParseResult.Ok).input)

    private fun u8(r: ByteArray, i: Int) = r[i].toInt() and 0xff

    private fun s16(r: ByteArray, i: Int) = (u8(r, i) or (u8(r, i + 1) shl 8)).toShort().toFloat()

    private fun touchX(r: ByteArray, base: Int) =
        (u8(r, base + 1) or ((u8(r, base + 2) and 0x0f) shl 8)) / Limits.TOUCH_X_PX_MAX.toFloat()

    private fun touchY(r: ByteArray, base: Int) =
        ((u8(r, base + 2) shr 4) or (u8(r, base + 3) shl 4)) / Limits.TOUCH_Y_PX_MAX.toFloat()

    @Test fun sticksAndTriggersFollowTheirBytes() {
        for (name in TestFixtures.names("reports")) {
            val r = raw(name)
            val s = mapped(name)
            assertEquals("$name leftX", u8(r, 1) / 255f * 2f - 1f, s.leftX, EPS)
            assertEquals("$name leftY", u8(r, 2) / 255f * 2f - 1f, s.leftY, EPS)
            assertEquals("$name rightX", u8(r, 3) / 255f * 2f - 1f, s.rightX, EPS)
            assertEquals("$name rightY", u8(r, 4) / 255f * 2f - 1f, s.rightY, EPS)
            assertEquals("$name l2", u8(r, 5) / 255f, s.l2, EPS)
            assertEquals("$name r2", u8(r, 6) / 255f, s.r2, EPS)
        }
    }

    @Test fun extremesReachTheEndsOfTheRange() {
        assertEquals(1f, mapped("l2-full").l2, 0.01f)
        assertEquals(1f, mapped("r2-full").r2, 0.01f)
        assertEquals(0f, mapped("idle").l2, EPS)
        assertTrue(mapped("left-x-max").leftX > 0.9f)
        assertTrue(mapped("left-x-min").leftX < -0.9f)
    }

    @Test fun motionIsTheRawCountsOfItsBytes() {
        for (name in TestFixtures.names("reports")) {
            val r = raw(name)
            val s = mapped(name)
            val gyro = checkNotNull(s.gyro)
            val accel = checkNotNull(s.accel)
            assertEquals(MotionUnit.RAW_COUNTS, gyro.unit)
            assertEquals(
                "$name gyro",
                listOf(s16(r, 16), s16(r, 18), s16(r, 20)),
                listOf(gyro.x, gyro.y, gyro.z),
            )
            assertEquals(
                "$name accel",
                listOf(s16(r, 22), s16(r, 24), s16(r, 26)),
                listOf(accel.x, accel.y, accel.z),
            )
        }
    }

    @Test fun touchFingersAreNormalisedAcrossThePad() {
        assertEquals(emptyList<Any>(), mapped("idle").touches)
        assertEquals(1, mapped("touch-0").touches.size)
        val two = mapped("touch-1").touches
        assertEquals(2, two.size)
        val r = raw("touch-1")
        for ((i, base) in listOf(33, 37).withIndex()) {
            assertEquals("finger $i id", u8(r, base) and 0x7f, two[i].id)
            assertEquals("finger $i x", touchX(r, base), two[i].x, EPS)
            assertEquals("finger $i y", touchY(r, base), two[i].y, EPS)
            assertFalse(two[i].isPointer)
        }
    }

    @Test fun everyPixelOfThePadSurvivesTheRoundTripToTheScreenNumber() {
        // the live screen prints round(x * max): no pixel may print as its neighbour
        for (x in 0..Limits.TOUCH_X_PX_MAX) {
            val p = toPadState(idleWith(Touch(0, x, 0))).touches.single()
            assertEquals(x, Math.round(p.x * Limits.TOUCH_X_PX_MAX))
        }
        for (y in 0..Limits.TOUCH_Y_PX_MAX) {
            val p = toPadState(idleWith(Touch(0, 0, y))).touches.single()
            assertEquals(y, Math.round(p.y * Limits.TOUCH_Y_PX_MAX))
        }
    }

    private fun idleWith(t: Touch) =
        (parseUsbInput(raw("idle")) as ParseResult.Ok).input.copy(touches = listOf(t))

    @Test fun aTouchOutsideThePadIsRefusedNotDrawn() {
        assertThrows(IllegalArgumentException::class.java) {
            toPadState(
                idleWith(
                    Touch(
                        0,
                        Limits.TOUCH_X_PX_MAX + 1,
                        0,
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            toPadState(
                idleWith(
                    Touch(
                        0,
                        0,
                        Limits.TOUCH_Y_PX_MAX + 1,
                    ),
                ),
            )
        }
    }

    @Test fun batteryHeadphonesAndSequenceFollowTheirBytes() {
        for (name in TestFixtures.names("reports")) {
            val r = raw(name)
            val s = mapped(name)
            assertEquals(
                "$name battery",
                Battery.RawNibble(u8(r, 53) and 0x0f, u8(r, 53) shr 4),
                s.battery,
            )
            assertEquals("$name headphones", u8(r, 54) and 0x01 != 0, s.headphonesIn)
            assertEquals("$name sequence", u8(r, 7), s.sequence)
        }
    }

    @Test fun muteAndTheButtonsComeThrough() {
        assertEquals(setOf(Button.MUTE), mapped("mute").held)
        assertEquals(setOf(Button.CROSS), mapped("cross").held)
    }
}
