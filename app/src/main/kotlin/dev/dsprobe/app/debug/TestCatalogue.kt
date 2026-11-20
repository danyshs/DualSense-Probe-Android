package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.R
import dev.dsprobe.app.route.Feature
import dev.dsprobe.app.route.OutputRequest
import dev.dsprobe.app.route.Route
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Dpad
import dev.dsprobe.pad.MuteLed
import dev.dsprobe.pad.Rgb
import dev.dsprobe.pad.TriggerEffect

private const val PROBE_PULSE_MS = 1_000
private const val TRIGGER_START = 0x30 // the proven effect 01 30 c0 (P39)
private const val TRIGGER_FORCE = 0xc0
private const val TRIGGER_EFFECT_MS = 5_000
private const val RUMBLE_STRENGTH = 200

private fun title(raw: String): String = raw.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun buttonFeature(b: Button): Feature = when (b) {
    Button.MUTE -> Feature.MUTE_BUTTON
    Button.TOUCHPAD_CLICK -> Feature.TOUCHPAD_POINTER
    else -> Feature.BUTTONS
}

// controls that move with the target and are not cross-talk (see "Debug Mode" in the design)
private fun companionsOf(id: String): Set<InputId> = when {
    id == "in.button.l2" -> setOf(InputId("in.trigger.l2"))
    id == "in.button.r2" -> setOf(InputId("in.trigger.r2"))
    id == "in.trigger.l2" -> setOf(InputId("in.button.l2"))
    id == "in.trigger.r2" -> setOf(InputId("in.button.r2"))
    id == "in.button.touchpad_click" -> setOf(InputId("in.touch."))
    id == "in.button.l3" -> setOf(InputId("in.stick.left."))
    id == "in.button.r3" -> setOf(InputId("in.stick.right."))
    id.startsWith("in.stick.left.") -> setOf(InputId("in.stick.left."))
    id.startsWith("in.stick.right.") -> setOf(InputId("in.stick.right."))
    else -> emptySet()
}

private fun input(route: Route, id: String, promptRes: Int, arg: String, feature: Feature?): TestCase = TestCase(
    id = id, route = route, kind = Kind.INPUT, promptRes = promptRes, promptArg = arg,
    timeoutMs = AppLimits.INPUT_TEST_TIMEOUT_MS, check = Check.AwaitInput(InputId(id)), companions = companionsOf(id), feature = feature,
)

private fun inputTests(route: Route): List<TestCase> {
    val out = ArrayList<TestCase>()
    for (b in Button.entries) out.add(input(route, "in.button.${b.name.lowercase()}", R.string.prompt_press, title(b.name), buttonFeature(b)))
    for (d in Dpad.entries.filter { it != Dpad.NONE }) out.add(input(route, "in.dpad.${d.name.lowercase()}", R.string.prompt_dpad, d.name, Feature.BUTTONS))
    for (stick in listOf("left", "right")) {
        for (axis in listOf("x", "y")) out.add(input(route, "in.stick.$stick.$axis", R.string.prompt_stick, "$stick stick ${axis.uppercase()}", Feature.STICKS))
    }
    for (side in listOf("l2", "r2")) out.add(input(route, "in.trigger.$side", R.string.prompt_trigger, side.uppercase(), Feature.TRIGGERS_ANALOG))
    out.add(input(route, "in.touch.0", R.string.prompt_touch_one, "", Feature.TOUCHPAD_POINTER))
    out.add(input(route, "in.touch.1", R.string.prompt_touch_two, "", Feature.TOUCHPAD_TWO_FINGERS))
    out.add(input(route, "in.gyro", R.string.prompt_tilt, "gyroscope", Feature.MOTION_SENSORS))
    out.add(input(route, "in.accel", R.string.prompt_tilt, "accelerometer", Feature.MOTION_SENSORS))
    return out
}

private fun output(route: Route, id: String, what: String, request: OutputRequest, cleanup: OutputRequest?, feature: Feature?) = TestCase(
    id = id, route = route, kind = Kind.OUTPUT, promptRes = R.string.prompt_watch, promptArg = what,
    timeoutMs = AppLimits.CONFIRM_TAP_TIMEOUT_MS, check = Check.SendOutput(request, cleanup), feature = feature,
)

private fun rgb(r: Int, g: Int, b: Int) = Rgb(r.toUByte(), g.toUByte(), b.toUByte())

private fun outputTests(route: Route): List<TestCase> {
    val out = ArrayList<TestCase>()
    val off = OutputRequest.Lightbar(rgb(0, 0, 0))
    for ((name, c) in listOf("red" to rgb(255, 0, 0), "green" to rgb(0, 255, 0), "blue" to rgb(0, 0, 255))) {
        out.add(output(route, "out.lightbar.$name", "lightbar $name", OutputRequest.Lightbar(c), off, Feature.LIGHTBAR))
    }
    for (n in 1..AppLimits.PLAYER_LED_COUNT) {
        val mask = 1 shl (n - 1)
        out.add(output(route, "out.led.player.$n", "player LED $n", OutputRequest.PlayerLeds(mask), OutputRequest.PlayerLeds(0), Feature.PLAYER_LEDS))
    }
    val level = RUMBLE_STRENGTH.toUByte()
    val rumbles = listOf("left" to (level to 0.toUByte()), "right" to (0.toUByte() to level), "both" to (level to level))
    for ((name, motors) in rumbles) {
        val req = OutputRequest.Rumble(motors.first, motors.second, PROBE_PULSE_MS)
        out.add(output(route, "out.rumble.$name", "$name rumble", req, OutputRequest.Rumble(0u, 0u, 1), Feature.RUMBLE))
    }
    out.add(output(route, "out.led.mute.on", "mute LED", OutputRequest.SetMuteLed(MuteLed.ON), OutputRequest.SetMuteLed(MuteLed.OFF), Feature.MUTE_LED))
    for (isRight in listOf(true, false)) {
        val side = if (isRight) "r2" else "l2"
        val effect = TriggerEffect.resistance(TRIGGER_START.toUByte(), TRIGGER_FORCE.toUByte())
        val req = OutputRequest.Trigger(isRight, effect, TRIGGER_EFFECT_MS)
        out.add(output(route, "out.trigger.$side.resistance", "$side trigger resistance", req, OutputRequest.Trigger(isRight, TriggerEffect.OFF, 1), Feature.ADAPTIVE_TRIGGERS))
    }
    return out
}

private fun routeStep(id: String, route: Route, promptRes: Int) = TestCase(
    id = id, route = route, kind = Kind.ROUTE, promptRes = promptRes, promptArg = "",
    timeoutMs = AppLimits.ROUTE_SWITCH_TIMEOUT_MS, check = Check.AwaitRoute(route),
)

/**
 * The fixed, ordered list: every Android mode test, the switch to Direct USB, every Direct USB test,
 * then the release and a check that Android's devices came back. Input tests first within a route.
 */
fun buildCatalogue(): List<TestCase> {
    val list = inputTests(Route.ANDROID) + outputTests(Route.ANDROID) +
        routeStep("route.direct_usb", Route.DIRECT_USB, R.string.prompt_route_usb) +
        inputTests(Route.DIRECT_USB) + outputTests(Route.DIRECT_USB) +
        routeStep("route.android_back", Route.ANDROID, R.string.prompt_route_android)
    check(list.map { it.route to it.id }.toSet().size == list.size) { "a test appears twice for one route" }
    return list
}
