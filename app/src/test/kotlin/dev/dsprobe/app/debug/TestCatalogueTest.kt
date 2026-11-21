package dev.dsprobe.app.debug

import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestCatalogueTest {
    private val list = buildCatalogue()

    @Test fun theOrderIsAndroidTestsTheSwitchUsbTestsThenTheReleaseCheck() {
        val routeSteps = list.withIndex().filter { it.value.kind == Kind.ROUTE }
        assertEquals(
            listOf("route.direct_usb", "route.android_back"),
            routeSteps.map {
                it.value.id
            },
        )
        val (usbStart, backStart) = routeSteps.map { it.index }
        assertTrue(list.subList(0, usbStart).all { it.route == Route.ANDROID })
        assertTrue(list.subList(usbStart + 1, backStart).all { it.route == Route.DIRECT_USB })
        assertEquals(list.size - 1, backStart)
    }

    @Test fun inputsComeBeforeOutputsWithinARoute() {
        for (route in Route.entries) {
            val kinds = list.filter { it.route == route && it.kind != Kind.ROUTE }.map { it.kind }
            assertEquals(route.name, kinds.sortedBy { it.ordinal }, kinds)
        }
    }

    @Test fun everyIdFollowsTheNamingAndIsUniquePerRoute() {
        for (t in list.filter { it.kind != Kind.ROUTE }) {
            assertTrue(
                t.id,
                if (t.kind == Kind.INPUT) {
                    t.id.startsWith("in.")
                } else {
                    t.id.startsWith("out.")
                },
            )
        }
        assertEquals(list.size, list.map { it.route to it.id }.toSet().size)
        val hasButton = list.any { it.id == "in.button.triangle" }
        val hasDpad = list.any { it.id == "in.dpad.ne" }
        val hasLight = list.any { it.id == "out.lightbar.red" }
        assertTrue(hasButton && hasDpad && hasLight)
    }

    @Test fun everyInputTestReadsAControlTheSamplesHave() {
        val keys = samplesOf(
            PadState.EMPTY.copy(
                gyro = dev.dsprobe.app.live.Motion(0f, 0f, 0f, dev.dsprobe.app.live.MotionUnit.SI),
                accel = dev.dsprobe.app.live.Motion(0f, 0f, 0f, dev.dsprobe.app.live.MotionUnit.SI),
            ),
        ).keys
        for (t in list.filter { it.kind == Kind.INPUT }) {
            val id = (t.check as Check.AwaitInput).id
            InputMatcher(id) // a D-pad name that is not a direction would throw here
            val control = controlOfTest(id)
            assertTrue(
                "${t.id} reads $control",
                control in keys || keys.any { it.startsWith(control) },
            )
        }
    }

    @Test fun everyOutputTestHasACleanupSoThePadIsLeftQuiet() {
        for (t in list.filter { it.kind == Kind.OUTPUT }) {
            assertTrue(
                t.id,
                (t.check as Check.SendOutput).cleanup != null,
            )
        }
    }

    @Test fun theTriggerTestSendsTheProvenEffect() {
        val t = list.first { it.id == "out.trigger.r2.resistance" && it.route == Route.DIRECT_USB }
        val req = (t.check as Check.SendOutput).request as OutputRequest.Trigger
        assertEquals(listOf(0x01, 0x30, 0xc0), req.effect.bytes.map { it.toInt() })
    }

    @Test fun companionsAreDeclaredWhereControlsMoveTogether() {
        fun companions(id: String) = list.first { it.id == id }.companions.map { it.id }
        assertEquals(listOf("in.trigger.l2"), companions("in.button.l2"))
        assertEquals(listOf("in.button.r2"), companions("in.trigger.r2"))
        assertEquals(listOf("in.touch."), companions("in.button.touchpad_click"))
        assertEquals(listOf("in.stick.left."), companions("in.stick.left.y"))
        assertEquals(listOf("in.stick.right."), companions("in.button.r3"))
    }
}
