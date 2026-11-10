package com.winlator.inputcontrols

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import com.winlator.widget.InputControlsView
import com.winlator.widget.TouchpadView
import com.winlator.widget.XServerRendererView
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.ByteOrder
import com.winlator.winhandler.WinHandler
import com.winlator.xserver.XServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers

@Implements(value = WinHandler::class, isInAndroidSdk = false)
class WoWWinHandlerShadow {
    companion object {
        @JvmStatic @Implementation fun __staticInitializer__() {}
        @JvmStatic @Implementation fun notifyStateChanged(playerIndex: Int) {}
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [WoWWinHandlerShadow::class])
class WoWXInputWiringTest {
    private class Harness {
        val context = RuntimeEnvironment.getApplication()
        val view = InputControlsView(context)
        val profile = ControlsProfile(context, 9999)
        val element = ControlElement(view).apply { setType(ControlElement.Type.WOW_GAMEPAD) }
        val server = mock<XServer>()
        val rendererView = mock<XServerRendererView>().apply { whenever(getContext()).thenReturn(this@Harness.context) }
        val handler = WinHandler(server, rendererView)
        private val file = java.io.File.createTempFile("wow-input", ".bin", context.cacheDir)
        private val memory = RandomAccessFile(file, "rw").use { output ->
            output.setLength(64)
            output.channel.map(FileChannel.MapMode.READ_WRITE, 0, 64).apply { order(ByteOrder.LITTLE_ENDIAN) }
        }
        val packets = mutableListOf<GamepadState>()
        init {
            ControllerManager.getInstance().init(context)
            whenever(server.getWinHandler()).thenReturn(handler)
            ReflectionHelpers.setField(handler, "gamepadBuffer", memory)
            view.layout(0, 0, 1100, 1000)
            view.setTouchpadView(mock<TouchpadView>())
            ReflectionHelpers.setField(view, "xServer", server)
            profile.addElement(element)
            profile.setVirtualGamepad(true)
            handler.setInputControlsView(view)
            view.setProfile(profile)
            view.setWoWPanelBounds(560, 560)
            view.draw(Canvas(Bitmap.createBitmap(1100, 1000, Bitmap.Config.ARGB_8888)))
            packets.clear()
        }
        fun event(action: Int, x: Float, y: Float) = send(MotionEvent.obtain(0, 10, action, x, y, 0))
        fun multi(action: Int, points: List<Triple<Int, Float, Float>>) {
            val properties = points.map { (id, _, _) -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
            val coordinates = points.map { (_, px, py) -> MotionEvent.PointerCoords().apply { x = px; y = py; pressure = 1f; size = 1f } }.toTypedArray()
            send(MotionEvent.obtain(0, 10, action, points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0))
        }
        private fun send(event: MotionEvent) {
            view.onTouchEvent(event)
            event.recycle()
            assertEquals(1, memory.getInt(40))
            packets += GamepadState().apply {
                thumbLX = memory.getShort(4).toFloat() / 32767
                thumbLY = memory.getShort(6).toFloat() / 32767
                thumbRX = memory.getShort(8).toFloat() / 32767
                thumbRY = memory.getShort(10).toFloat() / 32767
                triggerL = if (memory.getShort(12) > 0) 1f else 0f
                triggerR = if (memory.getShort(14) > 0) 1f else 0f
                for ((index, sdl) in listOf(0,1,2,3,9,10,4,6,7,8).withIndex()) setPressed(index, memory.get(16+sdl).toInt() == 1)
                for ((index, sdl) in listOf(11,14,12,13).withIndex()) dpad[index] = memory.get(16+sdl).toInt() == 1
            }
        }
    }
    @Test fun everyOverlayButtonReachesWinHandlerVirtualStateAndReleases() {
        val h = Harness()
        val buttons = listOf(
            Binding.GAMEPAD_BUTTON_A, Binding.GAMEPAD_BUTTON_B, Binding.GAMEPAD_BUTTON_X, Binding.GAMEPAD_BUTTON_Y,
            Binding.GAMEPAD_BUTTON_L1, Binding.GAMEPAD_BUTTON_R1, Binding.GAMEPAD_BUTTON_L2, Binding.GAMEPAD_BUTTON_R2,
            Binding.GAMEPAD_BUTTON_L3, Binding.GAMEPAD_BUTTON_R3, Binding.GAMEPAD_BUTTON_SELECT, Binding.GAMEPAD_BUTTON_START,
            Binding.GAMEPAD_DPAD_UP, Binding.GAMEPAD_DPAD_RIGHT, Binding.GAMEPAD_DPAD_DOWN, Binding.GAMEPAD_DPAD_LEFT,
        )
        for (binding in buttons) {
            val bounds = h.element.woWGamepad.buttonBounds(binding)
            assertNotNull(binding.name, bounds)
            h.event(MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY())
            val sent = h.packets.last()
            when (binding) {
                Binding.GAMEPAD_BUTTON_L2 -> assertEquals(1f, sent.triggerL, 0f)
                Binding.GAMEPAD_BUTTON_R2 -> assertEquals(1f, sent.triggerR, 0f)
                Binding.GAMEPAD_DPAD_UP, Binding.GAMEPAD_DPAD_RIGHT, Binding.GAMEPAD_DPAD_DOWN, Binding.GAMEPAD_DPAD_LEFT ->
                    assertTrue(binding.name, sent.dpad[binding.ordinal - Binding.GAMEPAD_DPAD_UP.ordinal])
                else -> assertTrue(binding.name, sent.isPressed(binding.ordinal - Binding.GAMEPAD_BUTTON_A.ordinal))
            }
            h.event(MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY())
            val released = h.packets.last()
            assertEquals(binding.name, 0, released.buttons.toInt())
            assertEquals(0f, released.triggerL, 0f)
            assertEquals(0f, released.triggerR, 0f)
            assertTrue(released.dpad.none { it })
        }
        assertEquals(32, h.packets.size)
    }
    @Test fun bothTriggersAndDpadCanBeHeldTogetherForShiftedActionBars() {
        val h = Harness()
        fun point(id: Int, binding: Binding): Triple<Int,Float,Float> = h.element.woWGamepad.buttonBounds(binding).let { Triple(id,it.centerX(),it.centerY()) }
        val lt = point(1, Binding.GAMEPAD_BUTTON_L2)
        val rt = point(2, Binding.GAMEPAD_BUTTON_R2)
        val up = point(3, Binding.GAMEPAD_DPAD_UP)
        h.multi(MotionEvent.ACTION_DOWN, listOf(lt))
        h.multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), listOf(lt, rt))
        h.multi(MotionEvent.ACTION_POINTER_DOWN or (2 shl 8), listOf(lt, rt, up))
        assertEquals(1f, h.packets.last().triggerL, 0f)
        assertEquals(1f, h.packets.last().triggerR, 0f)
        assertTrue(h.packets.last().dpad[0])
        h.multi(MotionEvent.ACTION_POINTER_UP or (2 shl 8), listOf(lt, rt, up))
        h.multi(MotionEvent.ACTION_POINTER_UP or (1 shl 8), listOf(lt, rt))
        h.multi(MotionEvent.ACTION_UP, listOf(lt))
        assertEquals(0f, h.packets.last().triggerL, 0f)
        assertEquals(0f, h.packets.last().triggerR, 0f)
    }
    @Test fun bothSticksReachEverySignedAxisAndReturnToCenter() {
        val h = Harness()
        for (right in listOf(false, true)) for ((dx, dy) in listOf(-200f to 0f, 200f to 0f, 0f to -200f, 0f to 200f)) {
            val x = if (right) 550f else 150f
            val y = if (right) 300f else 800f
            h.event(MotionEvent.ACTION_DOWN, x, y)
            h.event(MotionEvent.ACTION_MOVE, x + dx, y + dy)
            val state = h.packets.last()
            val actualX = if (right) state.thumbRX else state.thumbLX
            val actualY = if (right) state.thumbRY else state.thumbLY
            assertEquals(dx.compareTo(0f), actualX.compareTo(0f))
            assertEquals(dy.compareTo(0f), actualY.compareTo(0f))
            h.event(MotionEvent.ACTION_UP, x + dx, y + dy)
            val released = h.packets.last()
            assertEquals(0f, if (right) released.thumbRX else released.thumbLX, 0f)
            assertEquals(0f, if (right) released.thumbRY else released.thumbLY, 0f)
        }
    }
    @Test fun wowProfileSuppressesLegacyShooterAndMouseModePersistsAcrossReload() {
        val h = Harness()
        h.view.setContainerShooterMode(true)
        h.view.setShooterModeActive(true)
        assertTrue(h.view.isWoWGamepadProfileActive)
        verify(h.server, atLeastOnce()).setRelativeMouseMovement(false)
        val toggle = h.element.woWGamepad.buttonBounds(Binding.NONE)
        h.event(MotionEvent.ACTION_DOWN, toggle.centerX(), toggle.centerY())
        h.event(MotionEvent.ACTION_UP, toggle.centerX(), toggle.centerY())
        assertTrue(h.view.isWoWMouseMode)
        h.view.setProfile(h.profile)
        assertTrue(h.view.isWoWMouseMode)
        val a = h.element.woWGamepad.buttonBounds(Binding.GAMEPAD_BUTTON_A)
        h.event(MotionEvent.ACTION_DOWN, a.centerX(), a.centerY())
        assertTrue(h.packets.last().isPressed(ExternalController.IDX_BUTTON_A.toInt()))
    }
    @Test fun allButtonsHaveSeparateHitTargetsInCoverOpenAndPanelLayouts() {
        val context = RuntimeEnvironment.getApplication()
        for ((w, h, panel) in listOf(Triple(2100, 900, -1), Triple(1100, 1000, -1), Triple(1100, 1000, 560))) {
            val view = InputControlsView(context)
            view.layout(0, 0, w, h)
            val pad = WoWTouchGamepad(view)
            pad.setPanelBounds(panel, panel)
            val bindings = Binding.values().filter { it.name.startsWith("GAMEPAD_BUTTON_") || it.name.startsWith("GAMEPAD_DPAD_") } + Binding.NONE + Binding.SHOW_KEYBOARD
            val bounds = bindings.map { pad.buttonBounds(it) }
            for (i in bounds.indices) for (j in i+1 until bounds.size) {
                val a = bounds[i]; val b = bounds[j]
                val distance = Math.hypot((a.centerX()-b.centerX()).toDouble(), (a.centerY()-b.centerY()).toDouble())
                assertTrue("${bindings[i]} overlaps ${bindings[j]} in $w x $h", distance >= (a.width()+b.width())/2)
            }
        }
    }
}
