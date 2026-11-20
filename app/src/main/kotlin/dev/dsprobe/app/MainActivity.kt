package dev.dsprobe.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.dsprobe.app.route.HOOK_MOTION
import dev.dsprobe.app.route.HOOK_TOUCH
import dev.dsprobe.app.route.PadSession
import dev.dsprobe.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val session: PadSession get() = (application as App).session

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot(session) }
    }

    override fun onStart() {
        super.onStart()
        session.onActivityStart()
    }

    override fun onStop() {
        super.onStop()
        // a rotation or a theme change restarts the activity; the pad stays as it is
        if (!isChangingConfigurations) session.onActivityStop()
    }

    // the pad's own events never reach the UI: Circle isn't Back and the touchpad click taps nothing
    // androidx marks only its own override restricted; calling super is the plain Activity contract
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = session.androidRoute.onKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = fromPad(event, HOOK_MOTION) || super.dispatchGenericMotionEvent(event)

    // a touchpad click arrives as a mouse button press, which is a touch event
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = fromPad(event, HOOK_TOUCH) || super.dispatchTouchEvent(event)

    private fun fromPad(event: MotionEvent, hook: String): Boolean {
        val decor = window.decorView
        return session.androidRoute.onMotion(event, decor.width, decor.height, hook)
    }
}
