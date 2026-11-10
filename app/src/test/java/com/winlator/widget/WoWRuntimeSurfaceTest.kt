package com.winlator.widget

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.renderer.XServerRenderer
import com.winlator.xserver.Pointer
import com.winlator.xserver.ScreenInfo
import com.winlator.xserver.XServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class WoWRuntimeSurfaceTest {
    private class Surface(context: Context) : View(context), XServerRendererView {
        override fun queueEvent(r: Runnable) = r.run()
        override fun requestRender() {}
        override fun setFrameRateLimit(limit: Int) {}
        override fun onResume() {}
        override fun onPause() {}
        override fun getxServer(): XServer = error("unused")
        override fun getRenderer(): XServerRenderer = error("unused")
    }
    @Test fun attachedPortraitAndLandscapeViewsMapHostTouchesToClientAndRejectPanel() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        val surface = Surface(activity)
        val controls = InputControlsView(activity)
        root.addView(surface)
        root.addView(controls)
        activity.setContentView(root)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val server = mock<XServer>()
        val renderer = mock<XServerRenderer>()
        ReflectionHelpers.setField(server, "screenInfo", ScreenInfo(1920, 1080))
        whenever(server.renderer).thenReturn(renderer)
        whenever(renderer.rendererView).thenReturn(surface)
        ReflectionHelpers.setField(controls, "xServer", server)
        controls.setTouchpadView(mock<TouchpadView>())
        val profile = com.winlator.inputcontrols.InputControlsManager(activity).getProfiles(false).first { it.hasWoWGamepad() }
        controls.setProfile(profile)
        for ((height, gameHeight) in listOf(1200 to 563, 600 to 563)) {
            root.layout(0, 0, 1200, height)
            surface.layout(100, 30, 1100, 30 + gameHeight)
            controls.layout(40, 10, 1140, height)
            val location = IntArray(2)
            surface.getLocationOnScreen(location)
            val overlayLocation = IntArray(2)
            controls.getLocationOnScreen(overlayLocation)
            val x = location[0] + 500f - overlayLocation[0]
            val y = location[1] + 281f - overlayLocation[1]
            assertTrue(controls.isWoWGamePoint(x, y))
            controls.performWoWGameClick(x, y)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            verify(server).injectPointerMove(960, 539)
            verify(server).injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
            verify(server).injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
            clearInvocations(server)
            assertFalse(controls.isWoWGamePoint(x, y + 600f))
            // Compose's host coordinates differ from the controls' coordinates.
            val hostLocation = IntArray(2)
            root.getLocationOnScreen(hostLocation)
            val event = MotionEvent.obtain(0, 5, MotionEvent.ACTION_DOWN, location[0] + 500f, location[1] + 281f, 0)
            event.offsetLocation(-hostLocation[0].toFloat(), -hostLocation[1].toFloat())
            assertTrue(controls.dispatchWoWHostTouch(event))
            event.recycle()
            val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, location[0] + 500f, location[1] + 281f, 0)
            up.offsetLocation(-hostLocation[0].toFloat(), -hostLocation[1].toFloat())
            controls.dispatchWoWHostTouch(up)
            up.recycle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            verify(server).injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
            verify(server).injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
            clearInvocations(server)
        }
    }
}
