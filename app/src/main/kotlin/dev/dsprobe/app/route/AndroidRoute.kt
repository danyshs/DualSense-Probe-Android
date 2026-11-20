package dev.dsprobe.app.route

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsRequest
import android.hardware.lights.LightsManager.LightsSession
import android.os.CombinedVibration
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.pad.Limits

private const val TAG = "AndroidRoute"
private const val LIGHTBAR_NAME = "indicator"
private const val PLAYER_LED_COUNT = 5
private const val RUMBLE_LEFT_ID = 0
private const val RUMBLE_RIGHT_ID = 1
private const val LATE_DROP_COUNT_MAX = 1_000_000

/** Next value of the late-event counter: counts up, stops at the limit so it can never wrap. */
internal fun nextLateDropCount(current: Int): Int {
    require(current in 0..LATE_DROP_COUNT_MAX) { "late drop count $current" }
    return if (current == LATE_DROP_COUNT_MAX) current else current + 1
}

/**
 * Runs [close] only when a request was made: the system throws on closing a session that never had one.
 * Returns the failure instead of throwing, since that is Android's fault, not ours.
 */
internal fun closeIfRequested(requested: Boolean, close: () -> Unit): RuntimeException? {
    if (!requested) return null
    return try {
        close()
        null
    } catch (e: RuntimeException) {
        e
    }
}

private fun mainThread(): Thread = Looper.getMainLooper().thread

/**
 * Android mode: only Android's own controller APIs, nothing claimed. Every method runs on the
 * main thread, which is also the one thread that publishes to LiveState.
 */
class AndroidRoute(
    private val context: Context,
    private val live: LiveState,
    private val feed: EventFeed,
) : PadRoute {
    private val handler = Handler(Looper.getMainLooper())
    private var pad: FoundPad? = null
    private var device: InputDevice? = null
    private var state = PadState.EMPTY
    private var lightsSession: LightsSession? = null
    private var lightsRequested = false
    private var sensorManager: SensorManager? = null

    /** Callbacks that arrived after stop() and were dropped; for the log. */
    var lateEventsDropped = 0
        private set

    // callbacks already queued on the main looper can outlive stop(); they are not input
    private fun droppedAsLate(): Boolean {
        if (pad != null) return false
        lateEventsDropped = nextLateDropCount(lateEventsDropped)
        return true
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (droppedAsLate()) return
            if (event.values.size < 3) return
            val (x, y, z) = event.values
            state = when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> state.withGyro(x, y, z)
                Sensor.TYPE_ACCELEROMETER -> state.withAccel(x, y, z)
                else -> return
            }
            live.publish(state)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val batteryPoll = object : Runnable {
        override fun run() {
            if (droppedAsLate()) return
            val current = device ?: return
            val battery = current.batteryState
            state = state.copy(battery = batteryFrom(battery.isPresent, battery.capacity))
            live.publish(state)
            handler.postDelayed(this, AppLimits.BATTERY_POLL_INTERVAL_MS)
        }
    }

    fun handlesDevice(deviceId: Int): Boolean = pad?.inputDeviceIds?.contains(deviceId) == true

    /** Starts listening on the pad's input devices. Asking again for the same pad changes nothing. */
    fun start(found: FoundPad) {
        check(Thread.currentThread() === mainThread()) { "AndroidRoute starts on the main thread" }
        if (pad == found) return
        stop()
        val first = InputDevice.getDevice(found.inputDeviceIds.first())
        if (first == null) {
            Log.w(TAG, "input device ${found.inputDeviceIds.first()} vanished before the route started")
            return
        }
        pad = found
        device = first
        state = PadState.EMPTY
        live.reset()
        live.setWriter(Thread.currentThread())
        registerSensors(first)
        openLights(first)
        // battery is greyed on USB, so only poll where it can answer
        if (reductions(Route.ANDROID, found.transport).none { it.feature == Feature.BATTERY }) batteryPoll.run()
    }

    private fun registerSensors(from: InputDevice) {
        val manager = from.sensorManager
        sensorManager = manager
        for (type in intArrayOf(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER)) {
            val sensor = manager.getDefaultSensor(type)
            if (sensor == null) {
                Log.w(TAG, "no sensor of type $type on the pad")
                continue
            }
            manager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
        }
    }

    private fun openLights(from: InputDevice) {
        lightsSession = try {
            from.lightsManager.openSession()
        } catch (e: RuntimeException) {
            Log.w(TAG, "lights session refused", e)
            null
        }
    }

    override fun stop() {
        pad ?: return
        check(Thread.currentThread() === mainThread()) { "AndroidRoute stops on the main thread" }
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(sensorListener)
        sensorManager = null
        device?.vibratorManager?.cancel()
        closeIfRequested(lightsRequested) { lightsSession?.close() }?.let { Log.w(TAG, "lights session close failed", it) }
        lightsSession = null
        lightsRequested = false
        device = null
        pad = null
        state = PadState.EMPTY
        live.setWriter(null)
        live.reset()
    }

    /** Returns true when the event was the pad's, so the activity swallows it. */
    fun onKey(event: KeyEvent): Boolean {
        if (droppedAsLate()) return false
        if (!handlesDevice(event.deviceId)) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        if (down || event.action == KeyEvent.ACTION_UP) {
            state = state.withKey(event.keyCode, down)
            live.publish(state)
            if (feed.isActive) feed.publish(describeKey(event))
        }
        return true
    }

    private fun describeKey(event: KeyEvent): FeedEvent {
        val text = "KeyEvent ${if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"} ${KeyEvent.keyCodeToString(event.keyCode)} " +
            "(${event.keyCode}) scan ${event.scanCode} source ${event.source}"
        return FeedEvent(System.nanoTime(), Route.ANDROID, null, text)
    }

    /** Joystick axes, or the touchpad's mouse pointer scaled by the window size. Returns true when it was the pad's. */
    fun onMotion(event: MotionEvent, windowWidthPx: Int, windowHeightPx: Int): Boolean {
        if (droppedAsLate()) return false
        if (!handlesDevice(event.deviceId)) return false
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            state = state.withAxes(readAxes(event))
            live.publish(state)
        } else if (event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) {
            if (event.action != MotionEvent.ACTION_SCROLL && windowWidthPx > 0 && windowHeightPx > 0) {
                val primary = event.buttonState and MotionEvent.BUTTON_PRIMARY != 0
                state = state.withPointer(event.x / windowWidthPx, event.y / windowHeightPx, primary)
                live.publish(state)
            }
        }
        return true
    }

    private fun readAxes(e: MotionEvent) = AxisReading(
        x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y),
        z = e.getAxisValue(MotionEvent.AXIS_Z), rz = e.getAxisValue(MotionEvent.AXIS_RZ),
        lTrigger = e.getAxisValue(MotionEvent.AXIS_LTRIGGER), rTrigger = e.getAxisValue(MotionEvent.AXIS_RTRIGGER),
        hatX = e.getAxisValue(MotionEvent.AXIS_HAT_X), hatY = e.getAxisValue(MotionEvent.AXIS_HAT_Y),
    )

    override fun send(req: OutputRequest): ApiAnswer {
        val current = device ?: return ApiAnswer.Failed("Android mode is not live")
        return try {
            when (req) {
                is OutputRequest.Rumble -> sendRumble(current, req)
                is OutputRequest.Lightbar -> sendLightbar(current, req)
                is OutputRequest.PlayerLeds -> sendPlayerLeds(current, req)
                is OutputRequest.SetMuteLed -> ApiAnswer.Failed("Android has no API for the mute LED")
                is OutputRequest.Trigger -> ApiAnswer.Failed("Android has no API for adaptive triggers")
                is OutputRequest.AudioTone -> ApiAnswer.Failed("Android lists no audio output for the pad")
            }
        } catch (e: RuntimeException) {
            // the platform's own answer to a bad request, handed back as the result
            ApiAnswer.Failed(e.toString())
        }
    }

    private fun sendRumble(from: InputDevice, req: OutputRequest.Rumble): ApiAnswer {
        require(req.durationMs in 1..Limits.RUMBLE_DURATION_MS_MAX) { "rumble duration ${req.durationMs}" }
        val manager: VibratorManager = from.vibratorManager
        val effect = CombinedVibration.startParallel()
        var motors = 0
        for ((id, strength) in listOf(RUMBLE_LEFT_ID to req.left, RUMBLE_RIGHT_ID to req.right)) {
            if (strength.toInt() == 0) continue
            effect.addVibrator(id, VibrationEffect.createOneShot(req.durationMs.toLong(), strength.toInt()))
            motors += 1
        }
        if (motors == 0) manager.cancel() else manager.vibrate(effect.combine())
        return ApiAnswer.Ok
    }

    private fun sendLightbar(from: InputDevice, req: OutputRequest.Lightbar): ApiAnswer {
        val light = from.lightsManager.lights.firstOrNull { it.name == LIGHTBAR_NAME }
            ?: return ApiAnswer.Failed("no light named $LIGHTBAR_NAME")
        val rgb = req.rgb
        val color = Color.rgb(rgb.r.toInt(), rgb.g.toInt(), rgb.b.toInt())
        return request(light, color)
    }

    private fun sendPlayerLeds(from: InputDevice, req: OutputRequest.PlayerLeds): ApiAnswer {
        require(req.mask in 0..Limits.PLAYER_LED_MASK_MAX) { "player LED mask ${req.mask}" }
        val lights = from.lightsManager.lights
        val builder = LightsRequest.Builder()
        for (n in 1..PLAYER_LED_COUNT) {
            val light = lights.firstOrNull { it.name.endsWith("player-$n") }
                ?: return ApiAnswer.Failed("no light named player-$n")
            // brightness-only lights: white is on, black is off
            val on = req.mask and (1 shl (n - 1)) != 0
            builder.addLight(light, LightState.Builder().setColor(if (on) Color.WHITE else Color.BLACK).build())
        }
        return submit(builder.build())
    }

    private fun request(light: Light, color: Int): ApiAnswer =
        submit(LightsRequest.Builder().addLight(light, LightState.Builder().setColor(color).build()).build())

    private fun submit(request: LightsRequest): ApiAnswer {
        val session = lightsSession ?: return ApiAnswer.Failed("no lights session")
        lightsRequested = true
        session.requestLights(request)
        return ApiAnswer.Ok
    }
}
