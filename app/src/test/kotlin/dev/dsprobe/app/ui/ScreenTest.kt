package dev.dsprobe.app.ui

import dev.dsprobe.app.route.ReleaseReason
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.route.androidMode
import dev.dsprobe.app.route.fakeAnswers
import dev.dsprobe.app.route.reductions
import dev.dsprobe.app.route.btPad
import dev.dsprobe.app.route.usbHit
import dev.dsprobe.app.route.usbPad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {
    @Test fun tooManyAlwaysWinsAndTheStackShowsOtherwise() {
        val deep = ScreenStack().push(Screen.DEBUG).push(Screen.LOG)
        assertEquals(Screen.TOO_MANY, screenFor(RouteState.TooMany, ScreenStack()))
        assertEquals(Screen.TOO_MANY, screenFor(RouteState.TooMany, deep))
        for (s in listOf(RouteState.NoPad(), RouteState.Stranded(usbHit), androidMode(btPad), RouteState.DirectUsb(usbPad))) {
            assertEquals(Screen.LIVE, screenFor(s, ScreenStack()))
            assertEquals(Screen.LOG, screenFor(s, deep))
        }
    }

    @Test fun backReturnsExactlyOneStep() {
        val stack = ScreenStack().push(Screen.GUIDED).push(Screen.LOG)
        assertEquals(Screen.LOG, stack.top)
        assertEquals(Screen.GUIDED, stack.pop().top)
        assertEquals(Screen.LIVE, stack.pop().pop().top)
        assertEquals("back on Live stays on Live", Screen.LIVE, stack.pop().pop().pop().top)
        assertFalse(ScreenStack().canPop)
        assertTrue(stack.canPop)
    }

    @Test fun openingAScreenAlreadyInTheStackGoesBackToItSoItNeverRepeats() {
        val stack = ScreenStack().push(Screen.DEBUG).push(Screen.GUIDED).push(Screen.LOG)
        val again = stack.push(Screen.DEBUG)
        assertEquals(listOf(Screen.LIVE, Screen.DEBUG), again.items)
        assertEquals(stack, stack.push(Screen.LOG))
    }

    @Test(expected = IllegalArgumentException::class) fun aStackThatDoesNotStartAtLiveIsRefused() {
        ScreenStack(listOf(Screen.DEBUG))
    }

    @Test fun outputsCanBeSentOnlyWhileARouteIsLive() {
        assertTrue(canSendOutputs(androidMode()))
        assertTrue(canSendOutputs(RouteState.DirectUsb(usbPad)))
        for (s in listOf(RouteState.NoPad(), RouteState.TooMany, RouteState.Stranded(usbHit), RouteState.AskingUsbPermission(usbPad), RouteState.Releasing(usbPad, ReleaseReason.BACK))) {
            assertFalse("$s", canSendOutputs(s))
        }
    }

    @Test fun theTitleNamesDirectUsbWhileClaimedOrLeaving() {
        assertEquals(Route.DIRECT_USB, routeOf(RouteState.DirectUsb(usbPad)))
        assertEquals(Route.DIRECT_USB, routeOf(RouteState.Releasing(usbPad, ReleaseReason.BACK)))
        for (s in listOf(RouteState.NoPad(), RouteState.TooMany, androidMode(), RouteState.AskingUsbPermission(usbPad))) {
            assertEquals(Route.ANDROID, routeOf(s))
        }
    }

    @Test fun padComesOnlyFromStatesThatHaveOne() {
        assertEquals(usbPad, padOf(androidMode()))
        assertNull(padOf(RouteState.NoPad()))
    }

    @Test fun onlyAndroidModeAsksAndroidAndDirectUsbLocksNothing() {
        val nothing = fakeAnswers()
        assertEquals(reductions(nothing), lockedFor(androidMode(), nothing))
        assertTrue(lockedFor(androidMode(), nothing).isNotEmpty())
        assertEquals(emptyList<Any>(), lockedFor(RouteState.DirectUsb(usbPad), nothing))
        assertEquals(emptyList<Any>(), lockedFor(androidMode(), null))
    }
}
