package dev.dsprobe.app.ui

import dev.dsprobe.app.route.ReleaseReason
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.RouteState
import dev.dsprobe.app.route.androidMode
import dev.dsprobe.app.route.btPad
import dev.dsprobe.app.route.usbHit
import dev.dsprobe.app.route.usbPad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTest {
    @Test fun onlyTooManyHasItsOwnScreen() {
        assertEquals(Screen.TOO_MANY, screenFor(RouteState.TooMany))
        for (s in listOf(RouteState.NoPad(), RouteState.Stranded(usbHit), androidMode(btPad), RouteState.DirectUsb(usbPad))) {
            assertEquals(Screen.LIVE, screenFor(s))
        }
    }

    @Test fun theTitleNamesDirectUsbWhileClaimedOrLeaving() {
        assertEquals(Route.DIRECT_USB, routeOf(RouteState.DirectUsb(usbPad)))
        assertEquals(Route.DIRECT_USB, routeOf(RouteState.Releasing(usbPad, ReleaseReason.BACK)))
        for (s in listOf(RouteState.NoPad(), RouteState.TooMany, androidMode(), RouteState.AskingUsbPermission(usbPad))) {
            assertEquals(Route.ANDROID, routeOf(s))
        }
    }

    @Test fun padAndReductionsComeOnlyFromStatesThatHaveThem() {
        assertEquals(usbPad, padOf(androidMode()))
        assertNull(padOf(RouteState.NoPad()))
        assertEquals(androidMode().reduced, reducedOf(androidMode()))
        assertEquals(emptyList<Any>(), reducedOf(RouteState.DirectUsb(usbPad)))
    }
}
