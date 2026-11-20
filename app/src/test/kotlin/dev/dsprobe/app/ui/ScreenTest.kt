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
    @Test fun tooManyAlwaysWinsAndOutputsShowOnlyWhenAskedFor() {
        assertEquals(Screen.TOO_MANY, screenFor(RouteState.TooMany, wantsOutputs = false))
        assertEquals(Screen.TOO_MANY, screenFor(RouteState.TooMany, wantsOutputs = true))
        for (s in listOf(RouteState.NoPad(), RouteState.Stranded(usbHit), androidMode(btPad), RouteState.DirectUsb(usbPad))) {
            assertEquals(Screen.LIVE, screenFor(s, wantsOutputs = false))
            assertEquals(Screen.OUTPUTS, screenFor(s, wantsOutputs = true))
        }
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
