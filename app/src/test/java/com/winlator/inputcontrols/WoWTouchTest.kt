package com.winlator.inputcontrols

import com.winlator.widget.InputControlsView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
class WoWTouchTest {
    @Test fun floatingOriginAndClamping() {
        val stick = WoWTouchGeometry.Stick()
        stick.begin(81f, 240f)
        stick.move(91f, 230f, 20f)
        assertEquals(81f, stick.originX, 0f)
        assertEquals(240f, stick.originY, 0f)
        assertEquals(.5f, stick.x, .001f)
        assertEquals(-.5f, stick.y, .001f)
        stick.move(1000f, 1000f, 20f)
        assertEquals(1.0, Math.hypot(stick.x.toDouble(), stick.y.toDouble()), .0001)
        stick.release()
        assertEquals(0f, stick.x, 0f)
        stick.begin(50f, 70f)
        assertEquals(50f, stick.originX, 0f)
        assertEquals(0f, stick.y, 0f)
    }
    @Test fun cameraDeltaPreservesDirectionAndClamps() {
        assertEquals(.5f, WoWTouchGeometry.camera(10f, 20f), 0f)
        assertEquals(-1f, WoWTouchGeometry.camera(-50f, 20f), 0f)
        assertEquals(1f, WoWTouchGeometry.camera(50f, 20f), 0f)
        assertEquals(0f, WoWTouchGeometry.camera(0f, 20f), 0f)
    }
    @Test fun coverOpenAndFlexSelection() {
        assertEquals(WoWTouchGeometry.Layout.COVER, WoWTouchGeometry.layout(2100f, 900f, false, false))
        assertEquals(WoWTouchGeometry.Layout.OPEN, WoWTouchGeometry.layout(1100f, 1000f, false, true))
        assertEquals(WoWTouchGeometry.Layout.FLEX, WoWTouchGeometry.layout(1100f, 1000f, true, true))
        assertEquals(WoWTouchGeometry.Layout.OPEN, WoWTouchGeometry.layout(1100f, 1000f, true, false))
    }
    private fun view(): InputControlsView = mock<InputControlsView>().apply {
        whenever(resources).thenReturn(RuntimeEnvironment.getApplication().resources)
        whenever(width).thenReturn(2100)
        whenever(height).thenReturn(900)
        whenever(maxWidth).thenReturn(2100)
        whenever(maxHeight).thenReturn(900)
    }
    @Test fun movementZoneFloatsAndCancelCenters() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.down(1, 200f, 500f)
        pad.move(1, 250f, 500f)
        val value = argumentCaptor<Float>()
        verify(view).handleInputEvent(eq(Binding.GAMEPAD_LEFT_THUMB_RIGHT), eq(true), value.capture())
        assertTrue(value.firstValue > 0)
        assertTrue(pad.cancel())
        verify(view).handleInputEvent(Binding.GAMEPAD_LEFT_THUMB_RIGHT, true, 0f)
        assertFalse(pad.move(1, 300f, 500f))
    }
    @Test fun shoulderButtonReleasesWithoutMoving() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.down(1, 105f, 117f)
        verify(view).handleInputEvent(Binding.GAMEPAD_BUTTON_L2, true)
        pad.up(1)
        verify(view).handleInputEvent(Binding.GAMEPAD_BUTTON_L2, false)
        verify(view, never()).handleInputEvent(eq(Binding.GAMEPAD_LEFT_THUMB_RIGHT), any(), any())
    }
    @Test fun overlappingButtonWinsOverMovementZone() {
        val view = view()
        whenever(view.width).thenReturn(900)
        whenever(view.height).thenReturn(1000)
        val pad = WoWTouchGamepad(view)
        pad.down(1, 280f, 860f)
        verify(view).handleInputEvent(Binding.GAMEPAD_DPAD_LEFT, true)
        pad.move(1, 200f, 860f)
        verify(view, never()).handleInputEvent(eq(Binding.GAMEPAD_LEFT_THUMB_RIGHT), any(), any())
        pad.up(1)
        verify(view).handleInputEvent(Binding.GAMEPAD_DPAD_LEFT, false)
    }
    @Test fun postureChangeReleasesHeldInputs() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.down(1, 200f, 500f)
        pad.move(1, 250f, 500f)
        pad.setPanelBounds(450, 450)
        verify(view).handleInputEvent(Binding.GAMEPAD_LEFT_THUMB_RIGHT, true, 0f)
        assertFalse(pad.move(1, 250f, 500f))
    }
    @Test fun cameraReleaseCentersAndKeepsMovementPointer() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.down(1, 200f, 500f)
        pad.down(2, 1000f, 450f)
        pad.move(2, 1050f, 450f)
        verify(view).handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, 1f)
        pad.up(2)
        verify(view).handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, 0f)
        assertTrue(pad.move(1, 240f, 500f))
    }
    @Test fun builtinProfileLoadsAndOptionsSurviveSave() {
        val context = RuntimeEnvironment.getApplication()
        val profile = InputControlsManager(context).getProfiles(false).first { it.name == "WoW Mando táctil" }
        profile.loadElements(view())
        assertTrue(profile.isVirtualGamepad)
        assertEquals(1, profile.elements.size)
        val element = profile.elements.first()
        assertEquals(ControlElement.Type.WOW_GAMEPAD, element.type)
        assertTrue(element.woWGamepad.isFloatingStick)
        assertTrue(element.woWGamepad.isCameraDrag)
        element.woWGamepad.readOptions(JSONObject("""{"floatingStick":false,"cameraDrag":false,"cameraSensitivity":2}"""))
        profile.save()
        profile.loadElements(view())
        assertFalse(profile.elements.first().woWGamepad.isFloatingStick)
        assertFalse(profile.elements.first().woWGamepad.isCameraDrag)
        assertEquals(2f, profile.elements.first().woWGamepad.cameraSensitivity, 0f)
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun renderAllLayoutsWithShippingResources() {
        val context = RuntimeEnvironment.getApplication()
        for ((name, dimensions) in mapOf("cover" to (2100 to 900), "open" to (1100 to 1000), "flex" to (984 to 1092))) {
            val view = view()
            whenever(view.width).thenReturn(dimensions.first)
            whenever(view.height).thenReturn(dimensions.second)
            whenever(view.context).thenReturn(context)
            whenever(view.resources).thenReturn(context.resources)
            val pad = WoWTouchGamepad(view)
            if (name == "flex") pad.setPanelBounds(546, 546)
            val bitmap = android.graphics.Bitmap.createBitmap(dimensions.first, dimensions.second, android.graphics.Bitmap.Config.ARGB_8888)
            pad.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/outputs/wow-touch-$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(file.length() > 1000)
        }
    }
    @Test fun malformedSensitivityIsBounded() {
        val pad = WoWTouchGamepad(view())
        pad.readOptions(JSONObject("""{"cameraSensitivity":-5}"""))
        assertEquals(.1f, pad.cameraSensitivity, 0f)
    }
}
