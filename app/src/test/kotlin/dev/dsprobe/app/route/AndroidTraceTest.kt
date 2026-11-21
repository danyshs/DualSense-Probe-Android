package dev.dsprobe.app.route

import android.hardware.Sensor
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.dsprobe.app.live.FeedEvent
import dev.dsprobe.app.live.PadState
import dev.dsprobe.app.live.TouchPoint
import dev.dsprobe.app.output.AskKind
import dev.dsprobe.pad.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PAD_DEVICE = 7
private const val SCAN_CROSS = 304
private val GAMEPAD_SOURCE = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD

private val FeedEvent.line: String get() = checkNotNull(described)

class AndroidTraceTest {
    @Test fun aKeyRowNamesTheHookTheConstantAndItsValueTheScanCodeAndTheDevice() {
        val e =
            keyTraceEvent(
                EventOrigin(1L, PAD_DEVICE, GAMEPAD_SOURCE, PadState.EMPTY),
                true,
                KeyEvent.KEYCODE_BUTTON_A,
                "KEYCODE_BUTTON_A",
                SCAN_CROSS,
            )
        assertEquals("in.button.cross", e.control)
        val text = e.line
        assertTrue(text, text.contains("hook Activity.dispatchKeyEvent"))
        assertTrue(text, text.contains("KeyEvent.KEYCODE_BUTTON_A = 96"))
        for (part in listOf("KeyEvent.ACTION_DOWN = 0", "scan 304", "device 7")) {
            assertTrue(text, text.contains(part))
        }
        assertTrue(text, text.contains("GAMEPAD") && text.contains("maps to Button.CROSS"))
    }

    @Test fun aKeyUpSaysUpAndOtherButtonsGetTheirOwnConstants() {
        val up =
            keyTraceEvent(
                EventOrigin(1L, PAD_DEVICE, GAMEPAD_SOURCE, PadState.EMPTY),
                false,
                KeyEvent.KEYCODE_BUTTON_THUMBL,
                "KEYCODE_BUTTON_THUMBL",
                317,
            )
        assertEquals("in.button.l3", up.control)
        assertTrue(up.line.contains("KeyEvent.KEYCODE_BUTTON_THUMBL = 106"))
        assertTrue(up.line.contains("KeyEvent.ACTION_UP = 1"))
    }

    @Test fun anUnmappedKeyIsShownWithItsCodeAndScanCodeNotDropped() {
        val e =
            keyTraceEvent(
                EventOrigin(1L, PAD_DEVICE, GAMEPAD_SOURCE, PadState.EMPTY),
                true,
                KeyEvent.KEYCODE_MUTE,
                "KEYCODE_MUTE",
                248,
            )
        assertEquals("in.key.unmapped.91", e.control)
        val text = e.line
        for (part in listOf("KeyEvent.KEYCODE_MUTE = 91", "scan 248", "UNMAPPED")) {
            assertTrue(text, text.contains(part))
        }
    }

    @Test fun anAxisRowNamesTheConstantItsValueTheControlAndTheBeforeAndAfter() {
        val cur = AXES_AT_REST.copy(z = 0.5f)
        val events =
            axisTraceEvents(
                EventOrigin(2L, PAD_DEVICE, InputDevice.SOURCE_JOYSTICK, PadState.EMPTY),
                HOOK_MOTION,
                AXES_AT_REST,
                cur,
            )
        val e = events.single()
        assertEquals("in.stick.right.x", e.control)
        val text = e.line
        assertTrue(
            text,
            text.contains("MotionEvent.AXIS_Z = 11") && text.contains("raw +0.000 -> +0.500"),
        )
        assertTrue(
            text,
            text.contains("hook Activity.dispatchGenericMotionEvent") && text.contains("JOYSTICK"),
        )
    }

    @Test fun everyAxisHasItsOwnConstantAndControl() {
        val all = AxisReading(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 1f, -1f)
        val events =
            axisTraceEvents(
                EventOrigin(2L, PAD_DEVICE, InputDevice.SOURCE_JOYSTICK, PadState.EMPTY),
                HOOK_MOTION,
                AXES_AT_REST,
                all,
            )
        val byConstant = events.associate {
            it.line.substringAfter("MotionEvent.").substringBefore("  ") to
                it.control
        }
        assertEquals(
            mapOf(
                "AXIS_X = 0" to "in.stick.left.x",
                "AXIS_Y = 1" to "in.stick.left.y",
                "AXIS_Z = 11" to "in.stick.right.x",
                "AXIS_RZ = 14" to "in.stick.right.y",
                "AXIS_LTRIGGER = 17" to "in.trigger.l2",
                "AXIS_RTRIGGER = 18" to "in.trigger.r2",
                "AXIS_HAT_X = 15" to "in.dpad",
                "AXIS_HAT_Y = 16" to "in.dpad",
            ),
            byConstant,
        )
    }

    @Test fun anAxisThatDidNotMoveGivesNoRow() {
        assertEquals(
            emptyList<Any>(),
            axisTraceEvents(
                EventOrigin(2L, PAD_DEVICE, InputDevice.SOURCE_JOYSTICK, PadState.EMPTY),
                HOOK_MOTION,
                AXES_AT_REST,
                AXES_AT_REST,
            ),
        )
    }

    @Test fun theTouchpadPointerAndItsClickAreTwoRowsWithTheirHooks() {
        val rows =
            pointerTraceEvents(
                EventOrigin(3L, PAD_DEVICE, InputDevice.SOURCE_TOUCHPAD, PadState.EMPTY),
                HOOK_TOUCH,
                TouchPoint(0, 0.25f, 0.75f, isPointer = true),
                wasDown = false,
                isDown = true,
            )
        assertEquals(
            listOf("in.touch.0.pointer", "in.button.touchpad_click"),
            rows.map {
                it.control
            },
        )
        assertTrue(rows[1].line, rows[1].line.contains("MotionEvent.BUTTON_PRIMARY = 1"))
        assertTrue(rows[1].line, rows[1].line.contains("0 -> 1"))
        assertTrue(rows[0].line.contains("Activity.dispatchTouchEvent"))
        assertEquals(
            1,
            pointerTraceEvents(
                EventOrigin(3L, PAD_DEVICE, InputDevice.SOURCE_TOUCHPAD, PadState.EMPTY),
                HOOK_TOUCH,
                TouchPoint(0, 0.1f, 0.1f, isPointer = true),
                wasDown = true,
                isDown = true,
            ).size,
        )
    }

    @Test fun aSensorRowNamesItsTypeConstant() {
        val gyro =
            checkNotNull(
                sensorTraceEvent(
                    4L,
                    Sensor.TYPE_GYROSCOPE,
                    floatArrayOf(0.1f, 0.2f, 0.3f),
                    PadState.EMPTY,
                ),
            )
        assertEquals("in.gyro", gyro.control)
        assertTrue(
            gyro.line,
            gyro.line.contains("Sensor.TYPE_GYROSCOPE = 4") &&
                gyro.line.contains("hook SensorEventListener.onSensorChanged"),
        )
        val accel =
            checkNotNull(
                sensorTraceEvent(
                    4L,
                    Sensor.TYPE_ACCELEROMETER,
                    floatArrayOf(0f, 0f, 9.8f),
                    PadState.EMPTY,
                ),
            )
        assertTrue(accel.line.contains("Sensor.TYPE_ACCELEROMETER = 1"))
        val light = floatArrayOf(0f, 0f, 0f)
        assertEquals(null, sensorTraceEvent(4L, Sensor.TYPE_LIGHT, light, PadState.EMPTY))
    }

    @Test fun aVibrateRowGivesTheCallWithEveryIdAmplitudeAndDuration() {
        val text = oneShotCall(listOf(0 to 200, 1 to 50), 1500)
        assertTrue(text, text.contains("VibratorManager.vibrate(CombinedVibration.startParallel()"))
        assertTrue(
            text,
            text.contains(
                ".addVibrator(id 0, VibrationEffect.createOneShot(1500 ms, amplitude 200))",
            ),
        )
        assertTrue(
            text,
            text.contains(
                ".addVibrator(id 1, VibrationEffect.createOneShot(1500 ms, amplitude 50))",
            ),
        )
    }

    @Test fun aWaveformRowGivesTimingsAmplitudesAndNoRepeat() {
        val text = waveformCall(listOf(Waveform(1, longArrayOf(100, 50), intArrayOf(255, 0))))
        assertTrue(
            text,
            text.contains(
                "addVibrator(id 1, VibrationEffect.createWaveform(timings [100, 50] ms, " +
                    "amplitudes [255, 0], repeat -1))",
            ),
        )
        val long = waveformCall(listOf(Waveform(0, LongArray(10) { 10L }, IntArray(10) { 5 })))
        assertTrue(long, long.contains("...(10 entries)"))
    }

    @Test fun aLightsRowGivesTheLightNameIdColourAndTheRequestCall() {
        val text = lightsCall(listOf(LightEntry("indicator", 3, 0xffff0000.toInt())))
        assertTrue(
            text,
            text.contains("LightsManager.LightsSession.requestLights(LightsRequest.Builder()"),
        )
        assertTrue(
            text,
            text.contains(
                ".addLight(Light \"indicator\" id 3, " +
                    "LightState.Builder().setColor(0xffff0000).build())",
            ),
        )
    }

    @Test fun anOutputRowCarriesTheCallsAndAndroidsAnswerOrItsException() {
        val req = OutputRequest.Lightbar(Rgb(255u, 0u, 0u))
        val ok = androidOutputEvent(5L, req, listOf("a call"), ApiAnswer.Ok, PadState.EMPTY)
        assertEquals("out.lightbar", ok.control)
        val okParts =
            listOf("request Lightbar(rgb ff0000)", "call a call", "Android returned normally")
        for (part in okParts) assertTrue(ok.line, ok.line.contains(part))
        val failed =
            androidOutputEvent(
                5L,
                req,
                emptyList(),
                ApiAnswer.Failed("java.lang.IllegalStateException: boom"),
                null,
            )
        assertTrue(
            failed.line,
            failed.line.contains("no Android call made") &&
                failed.line.contains("Android answered: java.lang.IllegalStateException: boom"),
        )
    }

    @Test fun theDevelopersDidItChangeAnswerSitsNextToAndroidsAnswer() {
        val e =
            changedAnswerEvent(
                6L,
                AskKind.LIGHTBAR,
                "lightbar ff0000",
                ApiAnswer.Ok,
                changed = false,
            )
        assertEquals("out.lightbar", e.control)
        val parts =
            listOf("lightbar ff0000", "Android said Android returned normally", "developer said no")
        for (part in parts) assertTrue(e.line, e.line.contains(part))
    }
}
