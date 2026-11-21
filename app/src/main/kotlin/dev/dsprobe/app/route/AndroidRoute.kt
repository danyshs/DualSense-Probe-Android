package dev.dsprobe.app.route

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager.LightsSession
import android.hardware.lights.LightsRequest
import android.os.CombinedVibration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.live.EventFeed
import dev.dsprobe.app.live.LiveState
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.pad.FoundPad
import dev.dsprobe.pad.Button
import dev.dsprobe.pad.Limits
import dev.dsprobe.pad.MuteLed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AndroidRoute"

private fun logWarn(message: String) {
    Log.w(TAG, message)
}
private const val LATE_DROP_COUNT_MAX = 1_000_000
private const val NO_REPEAT = -1 // createWaveform: play once

/** Next value of the late-event counter: counts up, stops at the limit so it can never wrap. */
internal fun nextLateDropCount(current: Int): Int {
    require(current in 0..LATE_DROP_COUNT_MAX) { "late drop count $current" }
    return if (current == LATE_DROP_COUNT_MAX) current else current + 1
}

/**
 * Runs [close] only when a request was made: the system throws on closing a session that never had
 * one. A failure is Android's, not ours, so it is logged through [boundary], not thrown.
 */
internal fun closeLightsIfRequested(
    requested: Boolean,
    log: (String) -> Unit,
    close: () -> Unit,
): Boolean = !requested || boundary("lights session close", log, close)

private fun mainThread(): Thread = Looper.getMainLooper().thread

/**
 * Android mode: only Android's own controller APIs, nothing claimed. Every method runs on the
 * main thread, which is also the one thread that publishes to LiveState.
 */
class AndroidRoute(
    private val context: Context,
    private val live: LiveState,
    private val feed: EventFeed,
    private val nowMs: () -> Long = SystemClock::uptimeMillis,
) : PadRoute {
    private val handler = Handler(Looper.getMainLooper())
    private var pad: FoundPad? = null
    private var device: InputDevice? = null
    private var state = PadState.EMPTY
    private var lightsSession: LightsSession? = null
    private var lightsRequested = false
    private var sensorManager: SensorManager? = null
    private var touchPointersSeen = 0
    private var lastAxes = AXES_AT_REST
    private val callsMade = ArrayList<String>()

    /** True while Debug Mode's Mute prompt is up: see [noteMuteKey]. */
    @Volatile
    var muteWatched = false
    private var playing: List<RumbleStep>? = null
    private var playStartMs = 0L
    private val mutableAnswers = MutableStateFlow<AndroidAnswers?>(null)

    /**
     * What Android says it offers for the pad, asked again on every start and by [requery]; null
     * while the route is not live.
     */
    val answers: StateFlow<AndroidAnswers?> = mutableAnswers.asStateFlow()

    val isLive: Boolean get() = device != null

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
            if (feed.isActive) {
                val values = floatArrayOf(x, y, z)
                sensorTraceEvent(System.nanoTime(), event.sensor.type, values, state)
                    ?.let(feed::publish)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val batteryPoll = object : Runnable {
        override fun run() {
            if (droppedAsLate()) return
            val current = device ?: return
            val battery = askOrNull("batteryState", ::logWarn) { current.batteryState }
            if (battery != null) {
                state =
                    state.copy(
                        battery = batteryFrom(battery.isPresent, battery.capacity, battery.status),
                    )
                live.publish(state)
            }
            handler.postDelayed(this, AppLimits.BATTERY_POLL_INTERVAL_MS)
        }
    }

    fun handlesDevice(deviceId: Int): Boolean = pad?.inputDeviceIds?.contains(deviceId) == true

    /**
     * Starts listening on the pad's input devices. Asking again for the same pad changes nothing.
     */
    fun start(found: FoundPad) {
        check(Thread.currentThread() === mainThread()) { "AndroidRoute starts on the main thread" }
        if (pad == found) return
        stop()
        val first = InputDevice.getDevice(found.inputDeviceIds.first())
        if (first == null) {
            Log.w(
                TAG,
                "input device ${found.inputDeviceIds.first()} vanished before the route started",
            )
            return
        }
        pad = found
        device = first
        state = PadState.EMPTY
        live.reset()
        live.setWriter(Thread.currentThread())
        touchPointersSeen = 0
        lastAxes = AXES_AT_REST
        registerSensors(first)
        openLights(first)
        mutableAnswers.value = readAnswers(context, first, touchPointersSeen, ::logWarn)
        batteryPoll.run() // Android's answer is shown even where it reads as unknown
    }

    /** Asks Android again, for the "What Android reports" sheet. */
    fun requery() {
        check(Thread.currentThread() === mainThread()) { "AndroidRoute asks on the main thread" }
        val current = pad?.inputDeviceIds?.first()?.let { InputDevice.getDevice(it) } ?: return
        mutableAnswers.value =
            readAnswers(
                context,
                current,
                touchPointersSeen,
                ::logWarn,
            ).copy(muteKeyCode = mutableAnswers.value?.muteKeyCode)
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
            manager.registerListener(
                sensorListener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
                handler,
            )
        }
    }

    private fun openLights(from: InputDevice) {
        lightsSession = askOrNull("lights session", ::warn) { from.lightsManager.openSession() }
    }

    private fun warn(message: String) {
        Log.w(TAG, message)
    }

    override fun stop() {
        pad ?: return
        check(Thread.currentThread() === mainThread()) { "AndroidRoute stops on the main thread" }
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(sensorListener)
        sensorManager = null
        device?.vibratorManager?.cancel()
        playing = null
        closeLightsIfRequested(lightsRequested, ::warn) { lightsSession?.close() }
        lightsSession = null
        lightsRequested = false
        mutableAnswers.value = null
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
            noteMuteKey(event.keyCode, down)
            if (feed.isActive) {
                val name = KeyEvent.keyCodeToString(event.keyCode)
                feed.publish(
                    keyTraceEvent(
                        EventOrigin(System.nanoTime(), event.deviceId, event.source, state),
                        down,
                        event.keyCode,
                        name,
                        event.scanCode,
                    ),
                )
            }
        }
        return true
    }

    // while the Mute prompt is up, a key that maps to no pad button is how Android would offer Mute
    private fun noteMuteKey(keyCode: Int, down: Boolean) {
        if (!down) return
        val current = mutableAnswers.value ?: return
        val next = learnMuteKey(current, muteWatched, keyCode)
        if (next !== current) mutableAnswers.value = next
    }

    /**
     * Joystick axes, or the touchpad's mouse pointer scaled by the window size. Returns true when
     * it was the pad's.
     */
    fun onMotion(
        event: MotionEvent,
        windowWidthPx: Int,
        windowHeightPx: Int,
        hook: String,
    ): Boolean {
        if (droppedAsLate()) return false
        if (!handlesDevice(event.deviceId)) return false
        val isStickMove =
            event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
                event.action == MotionEvent.ACTION_MOVE
        val isPointer =
            event.isFromSource(InputDevice.SOURCE_MOUSE) ||
                event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
        if (isStickMove) {
            val reading = readAxes(event)
            val before = lastAxes
            lastAxes = reading
            state = state.withAxes(reading)
            live.publish(state)
            if (feed.isActive) {
                axisTraceEvents(
                    EventOrigin(System.nanoTime(), event.deviceId, event.source, state),
                    hook,
                    before,
                    reading,
                ).forEach(feed::publish)
            }
        } else if (isPointer) {
            val hasWindow = windowWidthPx > 0 && windowHeightPx > 0
            if (event.action != MotionEvent.ACTION_SCROLL && hasWindow) {
                noteFingers(event.pointerCount)
                val primary = event.buttonState and MotionEvent.BUTTON_PRIMARY != 0
                val wasDown = Button.TOUCHPAD_CLICK in state.held
                state =
                    state.withPointer(event.x / windowWidthPx, event.y / windowHeightPx, primary)
                live.publish(state)
                if (feed.isActive) {
                    val dot = state.touches.single()
                    val origin =
                        EventOrigin(System.nanoTime(), event.deviceId, event.source, state)
                    val events = pointerTraceEvents(origin, hook, dot, wasDown, primary)
                    events.forEach(feed::publish)
                }
            }
        }
        return true
    }

    // a second finger on the touchpad is the one thing Android has no query for: it is seen or it
    // is not
    private fun noteFingers(count: Int) {
        if (count <= touchPointersSeen) return
        touchPointersSeen = count
        mutableAnswers.value = mutableAnswers.value?.copy(touchPointersSeen = count)
    }

    private fun readAxes(e: MotionEvent) = AxisReading(
        x = e.getAxisValue(MotionEvent.AXIS_X),
        y = e.getAxisValue(MotionEvent.AXIS_Y),
        z = e.getAxisValue(MotionEvent.AXIS_Z),
        rz = e.getAxisValue(MotionEvent.AXIS_RZ),
        lTrigger = e.getAxisValue(MotionEvent.AXIS_LTRIGGER),
        rTrigger = e.getAxisValue(MotionEvent.AXIS_RTRIGGER),
        hatX = e.getAxisValue(MotionEvent.AXIS_HAT_X),
        hatY = e.getAxisValue(MotionEvent.AXIS_HAT_Y),
    )

    // the platform's own refusal, whatever its type, is the answer
    @Suppress("TooGenericExceptionCaught")
    override fun send(req: OutputRequest): ApiAnswer {
        validate(req) // our own bad request must crash, not read as Android's refusal below
        val current = device ?: return ApiAnswer.Failed("Android mode is not live")
        callsMade.clear()
        val answer = try {
            when (req) {
                is OutputRequest.Rumble -> sendRumble(current, req)

                is OutputRequest.RumblePattern -> sendPattern(current, req)

                is OutputRequest.SetRumbleMode -> ApiAnswer.Failed("Android has no rumble modes")

                is OutputRequest.Lightbar -> sendLightbar(current, req)

                is OutputRequest.PlayerLeds -> sendPlayerLeds(current, req)

                is OutputRequest.SetMuteLed -> sendMuteLed(current, req)

                is OutputRequest.Trigger -> ApiAnswer.Failed(
                    "Android has no API for adaptive triggers",
                )

                is OutputRequest.AudioTone -> ApiAnswer.Failed(
                    "Android lists no audio output for the pad",
                )
            }
        } catch (e: RuntimeException) {
            // the platform's own answer to a bad request, handed back as the result
            ApiAnswer.Failed(e.toString())
        }
        if (feed.isActive) {
            feed.publish(
                androidOutputEvent(System.nanoTime(), req, callsMade.toList(), answer, state),
            )
        }
        return answer
    }

    /** The checks on our own requests, kept outside send's catch of Android's failures. */
    private fun validate(req: OutputRequest) {
        when (req) {
            is OutputRequest.Rumble -> require(req.durationMs in 1..Limits.RUMBLE_DURATION_MS_MAX) {
                "rumble duration ${req.durationMs}"
            }

            is OutputRequest.RumblePattern -> {
                val totalMs = totalDurationMs(req.steps)
                require(totalMs in 1..Limits.RUMBLE_DURATION_MS_MAX) {
                    "rumble pattern $totalMs ms"
                }
            }

            is OutputRequest.PlayerLeds -> require(req.mask in 0..Limits.PLAYER_LED_MASK_MAX) {
                "player LED mask ${req.mask}"
            }

            else -> Unit
        }
    }

    private fun sendRumble(from: InputDevice, req: OutputRequest.Rumble): ApiAnswer {
        val manager: VibratorManager = from.vibratorManager
        val plan = rumblePlan(manager.vibratorIds.toList(), req.left.toInt(), req.right.toInt())
        if (plan.isEmpty()) {
            callsMade.add("VibratorManager.cancel()")
            manager.cancel()
            playing = null
            return ApiAnswer.Ok
        }
        val effect = CombinedVibration.startParallel()
        for ((id, strength) in plan) {
            effect.addVibrator(
                id,
                VibrationEffect.createOneShot(req.durationMs.toLong(), strength),
            )
        }
        callsMade.add(oneShotCall(plan, req.durationMs))
        manager.vibrate(effect.combine())
        notePlaying(listOf(RumbleStep(req.left.toInt(), req.right.toInt(), req.durationMs)))
        return ApiAnswer.Ok
    }

    /** One waveform per vibrator id Android reports, played together; Android ends it by itself. */
    private fun sendPattern(from: InputDevice, req: OutputRequest.RumblePattern): ApiAnswer {
        val manager: VibratorManager = from.vibratorManager
        val lanes = waveformsFor(manager.vibratorIds.toList(), req.steps)
        manager.cancel() // the new pattern replaces whatever was playing, on every motor
        if (lanes.isEmpty()) {
            callsMade.add("VibratorManager.cancel()")
            playing = null
            return ApiAnswer.Ok
        }
        val effect = CombinedVibration.startParallel()
        for (lane in lanes) {
            effect.addVibrator(
                lane.vibratorId,
                VibrationEffect.createWaveform(lane.timingsMs, lane.amplitudes, NO_REPEAT),
            )
        }
        callsMade.add(waveformCall(lanes))
        manager.vibrate(effect.combine())
        notePlaying(req.steps)
        return ApiAnswer.Ok
    }

    private fun notePlaying(steps: List<RumbleStep>) {
        playing = steps
        playStartMs = nowMs()
    }

    override fun rumbleSent(): MotorLevels {
        val steps = playing ?: return MotorLevels.OFF
        val index = stepIndexAt(steps, nowMs() - playStartMs)
        if (index == null) {
            playing = null
            return MotorLevels.OFF
        }
        return MotorLevels(steps[index].left, steps[index].right)
    }

    private fun sendLightbar(from: InputDevice, req: OutputRequest.Lightbar): ApiAnswer {
        val light = from.lightsManager.lights.firstOrNull { it.name == LIGHTBAR_NAME }
            ?: return ApiAnswer.Failed("no light named $LIGHTBAR_NAME")
        val rgb = req.rgb
        val color = Color.rgb(rgb.r.toInt(), rgb.g.toInt(), rgb.b.toInt())
        return request(light, color)
    }

    private fun sendPlayerLeds(from: InputDevice, req: OutputRequest.PlayerLeds): ApiAnswer {
        val lights = from.lightsManager.lights
        val builder = LightsRequest.Builder()
        val entries = ArrayList<LightEntry>(AppLimits.PLAYER_LED_COUNT)
        for (n in 1..AppLimits.PLAYER_LED_COUNT) {
            val light = lights.firstOrNull { it.name.endsWith(PLAYER_LIGHT_NAME_PART + n) }
                ?: return ApiAnswer.Failed("no light named ${PLAYER_LIGHT_NAME_PART}$n")
            // brightness-only lights: white is on, black is off
            val on = req.mask and (1 shl (n - 1)) != 0
            val color = if (on) Color.WHITE else Color.BLACK
            builder.addLight(light, LightState.Builder().setColor(color).build())
            entries.add(LightEntry(light.name, light.id, color))
        }
        return submit(builder.build(), entries)
    }

    // only reachable once Android lists a light that names the mute LED; it has no pulse mode
    private fun sendMuteLed(from: InputDevice, req: OutputRequest.SetMuteLed): ApiAnswer {
        val light =
            from.lightsManager.lights.firstOrNull {
                it.name.contains(MUTE_LIGHT_NAME_PART, ignoreCase = true)
            }
                ?: return ApiAnswer.Failed("Android lists no light for the mute LED")
        if (req.mode == MuteLed.PULSE) return ApiAnswer.Failed("Android lights have no pulse mode")
        return request(light, if (req.mode == MuteLed.ON) Color.WHITE else Color.BLACK)
    }

    private fun request(light: Light, color: Int): ApiAnswer = submit(
        LightsRequest.Builder().addLight(
            light,
            LightState.Builder().setColor(color).build(),
        ).build(),
        listOf(LightEntry(light.name, light.id, color)),
    )

    private fun submit(request: LightsRequest, entries: List<LightEntry>): ApiAnswer {
        val session = lightsSession ?: return ApiAnswer.Failed("no lights session")
        lightsRequested = true
        callsMade.add(lightsCall(entries))
        session.requestLights(request)
        return ApiAnswer.Ok
    }
}
