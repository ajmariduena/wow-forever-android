package com.winlator.inputcontrols

import android.app.Activity
import android.os.Looper
import android.view.View
import com.winlator.widget.InputControlsView
import com.winlator.widget.TouchpadView
import com.winlator.widget.XServerRendererView
import com.winlator.winhandler.WinHandler
import com.winlator.xserver.XServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.channels.FileChannel

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [WoWWinHandlerShadow::class])
class WoWDeferredProfileTest {
    @Test fun wowMetadataIsKnownWithoutDimensionsAndDeferredLoadConnectsP1ThenReconnectsOnResize() {
        val context = RuntimeEnvironment.getApplication()
        val manager = ControllerManager.getInstance().apply { init(context); setSlotEnabled(0, false) }
        val profile = InputControlsManager(context).getProfiles(false).first { it.hasWoWGamepad() }
        assertTrue(profile.isVirtualGamepad)
        assertFalse(profile.isElementsLoaded)
        val server = mock<XServer>()
        val rendererView = mock<XServerRendererView>()
        whenever(rendererView.getContext()).thenReturn(context)
        val handler = WinHandler(server, rendererView)
        whenever(server.getWinHandler()).thenReturn(handler)
        val memory = RandomAccessFile(File.createTempFile("wow-deferred", ".mem", context.cacheDir), "rw").use {
            it.setLength(64)
            it.channel.map(FileChannel.MapMode.READ_WRITE, 0, 64).apply { order(ByteOrder.LITTLE_ENDIAN) }
        }
        ReflectionHelpers.setField(handler, "gamepadBuffer", memory)
        val view = InputControlsView(context)
        view.setTouchpadView(mock<TouchpadView>())
        ReflectionHelpers.setField(view, "xServer", server)
        handler.setInputControlsView(view)
        view.visibility = View.GONE
        view.setShowTouchscreenControls(false)
        view.setProfile(profile)
        profile.loadElements(view)
        assertFalse(profile.isElementsLoaded)
        assertTrue(profile.isVirtualGamepad)
        assertEquals(0, memory.getInt(40))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(view)
        view.post {
            view.layout(0, 0, 1100, 1000)
            // This load deliberately happens before the first onDraw initializes the grid.
            assertTrue(view.maxWidth > 0)
            assertTrue(view.maxHeight > 0)
            profile.loadElements(view)
            view.setShowTouchscreenControls(true)
            view.visibility = View.VISIBLE
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(profile.isElementsLoaded)
        assertTrue(profile.isVirtualGamepad)
        assertTrue(manager.isSlotEnabled(0))
        assertEquals(1, memory.getInt(40))
        manager.setSlotEnabled(0, false)
        view.layout(0, 0, 1100, 1200)
        assertTrue(profile.isElementsLoaded)
        assertTrue(profile.isVirtualGamepad)
        assertTrue(manager.isSlotEnabled(0))
        assertEquals(1, memory.getInt(40))
        view.setShowTouchscreenControls(false)
        assertEquals(0, memory.getInt(40))
        view.setShowTouchscreenControls(true)
        assertEquals(1, memory.getInt(40))
    }
}
