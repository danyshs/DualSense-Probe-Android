package dev.dsprobe.app.live

import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun live(): LiveState = LiveState().also { it.setWriter(Thread.currentThread()) }

private fun pressed(vararg buttons: Button, dpad: Dpad = Dpad.NONE) =
    PadState.EMPTY.copy(held = buttons.toSet(), dpad = dpad)

class LiveStateTest {
    @Test fun aPressAndReleaseBetweenFramesStillShowsInTheNextFrame() {
        val l = live()
        l.publish(pressed(Button.CROSS))
        l.publish(PadState.EMPTY) // released before the screen looked
        val frame = l.takeFrame()
        assertEquals(emptySet<Button>(), frame.state.held)
        assertEquals(setOf(Button.CROSS), frame.pressedSinceLast)
    }

    @Test fun theLatchClearsAfterTheFrameIsTaken() {
        val l = live()
        l.publish(pressed(Button.L1))
        l.takeFrame()
        assertEquals(emptySet<Button>(), l.takeFrame().pressedSinceLast)
    }

    @Test fun aDpadTapBetweenFramesIsLatchedByDirection() {
        val l = live()
        l.publish(pressed(dpad = Dpad.NE))
        l.publish(pressed(dpad = Dpad.NONE))
        assertEquals(setOf(Dpad.NE), l.takeFrame().dpadSinceLast)
    }

    @Test fun everyButtonAndDirectionHasItsOwnBit() {
        val l = live()
        for (b in Button.entries) l.publish(pressed(b))
        for (d in Dpad.entries) l.publish(pressed(dpad = d))
        val frame = l.takeFrame()
        assertEquals(Button.entries.toSet(), frame.pressedSinceLast)
        assertEquals(Dpad.entries.filter { it != Dpad.NONE }.toSet(), frame.dpadSinceLast)
    }

    @Test fun newestStateWins() {
        val l = live()
        l.publish(PadState.EMPTY.copy(l2 = 0.2f))
        l.publish(PadState.EMPTY.copy(l2 = 0.9f))
        assertEquals(0.9f, l.takeFrame().state.l2, 0f)
    }

    @Test fun resetEmptiesStateAndLatch() {
        val l = live()
        l.publish(pressed(Button.PS))
        l.reset()
        assertEquals(Frame.EMPTY, l.takeFrame())
    }

    @Test fun publishFromAnotherThreadIsRefused() {
        val l = LiveState()
        l.setWriter(Thread.currentThread())
        var failure: Throwable? = null
        val other = Thread {
            try {
                l.publish(PadState.EMPTY)
            } catch (e: IllegalStateException) {
                failure = e
            }
        }
        other.start()
        other.join()
        assertTrue(failure != null)
    }

    @Test fun publishWithNoWriterIsRefused() {
        try {
            LiveState().publish(PadState.EMPTY)
            fail("expected a refusal")
        } catch (_: IllegalStateException) {
            // wanted
        }
    }
}
