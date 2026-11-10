package com.winlator.inputcontrols

import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Before
import org.junit.After

class GameControllerClassifierTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun device(sources: Int, virtual: Boolean = false, buttons: Boolean = false): InputDevice {
        val device = mockk<InputDevice>(relaxed = true)
        every { device.sources } returns sources
        every { device.supportsSource(any()) } answers { sources and firstArg<Int>() == firstArg<Int>() }
        every { device.isVirtual } returns virtual
        every { device.hasKeys(*anyIntVararg()) } returns booleanArrayOf(buttons, false, false, false)
        every { device.getMotionRange(any(), any()) } returns null
        return device
    }

    private fun assertClassification(expected: Boolean, device: InputDevice?) {
        assertEquals(expected, GameControllerClassifier.isGameController(device))
        assertEquals(expected, ControllerManager.isGameController(device))
        assertEquals(expected, ExternalController.isGameController(device))
    }

    @Test
    fun `physical and virtual gamepads use the same button criterion`() {
        for (virtual in listOf(false, true)) {
            assertClassification(true, device(InputDevice.SOURCE_GAMEPAD, virtual, buttons = true))
            assertClassification(false, device(InputDevice.SOURCE_GAMEPAD, virtual))
        }
    }

    @Test
    fun `virtual handheld joystick accepts axes from either controller source`() {
        for (source in listOf(InputDevice.SOURCE_GAMEPAD, InputDevice.SOURCE_JOYSTICK)) {
            val device = device(InputDevice.SOURCE_JOYSTICK, virtual = true)
            every { device.getMotionRange(MotionEvent.AXIS_X, source) } returns mockk(relaxed = true)
            assertClassification(true, device)
        }
    }

    @Test
    fun `composite keyboard mouse axes do not qualify as controller axes`() {
        val device = device(InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_MOUSE or
            InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD)
        for (axis in listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)) {
            every { device.getMotionRange(axis, InputDevice.SOURCE_MOUSE) } returns mockk(relaxed = true)
            every { device.getMotionRange(axis) } returns mockk(relaxed = true)
        }
        assertClassification(false, device)
    }

    @Test
    fun `missing device and non controller sources are rejected`() {
        assertClassification(false, null)
        assertClassification(false, device(InputDevice.SOURCE_KEYBOARD, buttons = true))
        assertClassification(false, device(InputDevice.SOURCE_JOYSTICK))
    }

    @Test
    fun `keyboard source does not exclude a real hybrid controller`() {
        assertClassification(true, device(InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD,
            virtual = true, buttons = true))
    }
}
