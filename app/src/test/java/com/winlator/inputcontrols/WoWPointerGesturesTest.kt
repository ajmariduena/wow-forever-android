package com.winlator.inputcontrols

import android.os.Looper
import com.winlator.widget.InputControlsView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class WoWPointerGesturesTest {
    private fun view(mouse: Boolean = false): InputControlsView = mock<InputControlsView>().apply {
        whenever(width).thenReturn(1000)
        whenever(height).thenReturn(1200)
        whenever(resources).thenReturn(RuntimeEnvironment.getApplication().resources)
        whenever(isWoWMouseMode).thenReturn(mouse)
        whenever(isWoWGamePoint(any(), any())).thenReturn(true)
        whenever(moveWoWPointer(any(), any())).thenReturn(true)
        val handler = android.os.Handler(Looper.getMainLooper())
        whenever(postDelayed(any(), any())).thenAnswer { call -> handler.postDelayed(call.getArgument(0), call.getArgument(1)) }
        whenever(removeCallbacks(any())).thenAnswer { call -> handler.removeCallbacks(call.getArgument(0)); true }
    }
    @Test fun stationaryFingerDecaysWithoutMoreTouchEventsAndCommitsXInput() {
        val view = view()
        val gestures = WoWPointerGestures(view)
        gestures.down(1, 500f, 400f, true, true, 1f)
        gestures.move(1, 650f, 400f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        verify(view).handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, 1f)
        verify(view).commitGamepadState()
        clearInvocations(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(44))
        verify(view).handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, 0f)
        gestures.up(1)
        verify(view, never()).performWoWGameClick(any(), any())
    }
    @Test fun wheelEligibilityRequiresExactlyTwoGameTouches() {
        assertTrue(WoWTouchGeometry.Wheel.eligible(2, true, true))
        assertFalse(WoWTouchGeometry.Wheel.eligible(1, true, true))
        assertFalse(WoWTouchGeometry.Wheel.eligible(3, true, true))
        assertFalse(WoWTouchGeometry.Wheel.eligible(2, true, false))
    }
    @Test fun wheelAccumulatesRemainderAndHandlesReversals() {
        val wheel = WoWTouchGeometry.Wheel()
        wheel.begin(100f)
        assertEquals(0, wheel.move(115f, 24f))
        assertEquals(1, wheel.move(130f, 24f))
        assertEquals(2, wheel.move(180f, 24f))
        assertEquals(-1, wheel.move(140f, 24f))
        wheel.reset()
        assertEquals(0, wheel.move(500f, 24f))
        wheel.begin(500f)
        assertEquals(-2, wheel.move(452f, 24f))
    }
    @Test fun twoFingerWheelStopsStickAndCannotClickAfterOneFingerLifts() {
        val view = view()
        val gestures = WoWPointerGestures(view)
        gestures.down(1, 500f, 300f, true, true, 1f)
        gestures.move(1, 650f, 300f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        gestures.down(2, 750f, 300f, true, true, 1f)
        verify(view).handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, 0f)
        clearInvocations(view)
        gestures.move(1, 650f, 252f)
        verify(view).scrollWoWPointer(700f, 276f, -1)
        gestures.up(2)
        gestures.move(1, 650f, 500f)
        gestures.up(1)
        verify(view, never()).performWoWGameClick(any(), any())
        verify(view, never()).handleInputEvent(any<Binding>(), any(), any())
    }
    @Test fun secondFingerOutsideGameDoesNotStealRightStick() {
        val view = view()
        val gestures = WoWPointerGestures(view)
        gestures.down(1, 500f, 300f, true, true, 1f)
        gestures.move(1, 650f, 300f)
        gestures.down(2, 750f, 700f, false, true, 1f)
        clearInvocations(view)
        gestures.move(1, 700f, 300f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        verify(view).handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, 1f)
        verify(view, never()).scrollWoWPointer(any(), any(), any())
    }
    @Test fun mouseTapFollowsFingerAndLeftClicksWithoutDrag() {
        val view = view(true)
        val gestures = WoWPointerGestures(view)
        gestures.down(1, 500f, 300f, true, true, 1f)
        gestures.up(1)
        verify(view).moveWoWPointer(500f, 300f)
        verify(view).performWoWGameClick(500f, 300f)
        verify(view, never()).setWoWMouseButton(any(), any())
    }
    @Test fun mouseDragPressesMovesAndReleasesLeftButton() {
        val view = view(true)
        val gestures = WoWPointerGestures(view)
        gestures.down(1, 500f, 300f, true, true, 1f)
        gestures.move(1, 540f, 340f)
        gestures.up(1)
        verify(view).setWoWMouseButton(false, true)
        verify(view).moveWoWPointer(540f, 340f)
        verify(view).setWoWMouseButton(false, false)
        verify(view, never()).performWoWGameClick(any(), any())
        verify(view, never()).handleInputEvent(any<Binding>(), any(), any())
    }
    @Test fun longPressRightClicksAndDoesNotLeftClick() {
        val view = view(true)
        val gestures = WoWPointerGestures(view)
        gestures.down(1, 500f, 300f, true, true, 1f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        verify(view).clickWoWPointer(500f, 300f, true)
        gestures.up(1)
        verify(view, never()).performWoWGameClick(any(), any())
    }
    @Test fun secondFingerReleasesMouseDragAndCancelsLongPress() {
        val view = view(true)
        val gestures = WoWPointerGestures(view)
        gestures.down(1, 500f, 300f, true, true, 1f)
        gestures.move(1, 540f, 340f)
        gestures.down(2, 750f, 340f, true, true, 1f)
        verify(view).setWoWMouseButton(false, false)
        gestures.move(1, 540f, 292f)
        verify(view).scrollWoWPointer(645f, 316f, -1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        gestures.cancel()
        verify(view, never()).clickWoWPointer(any(), any(), eq(true))
        verify(view, never()).performWoWGameClick(any(), any())
    }
    @Test fun bottomPanelTouchesNeverWheelOrMouseClick() {
        val view = view(true)
        val gestures = WoWPointerGestures(view)
        gestures.setGameBottom(600)
        gestures.down(1, 500f, 700f, false, true, 1f)
        gestures.down(2, 750f, 700f, false, true, 1f)
        gestures.move(1, 500f, 600f)
        gestures.up(1); gestures.up(2)
        verify(view, never()).scrollWoWPointer(any(), any(), any())
        verify(view, never()).performWoWGameClick(any(), any())
    }
}
