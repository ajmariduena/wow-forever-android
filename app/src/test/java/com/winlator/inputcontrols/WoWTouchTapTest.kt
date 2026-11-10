package com.winlator.inputcontrols

import com.winlator.widget.InputControlsView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class WoWTouchTapTest {
    @Test fun shortStationaryOrJitteryTouchIsTap() {
        val gesture = WoWTouchGeometry.TapOrDrag()
        gesture.begin(50f, 60f, 1000, 4f)
        assertFalse(gesture.move(52f, 62f))
        assertTrue(gesture.isTap(1250))
        assertFalse(gesture.isTap(1251))
    }
    @Test fun crossingSlopCannotBecomeTapByReturningToOrigin() {
        val gesture = WoWTouchGeometry.TapOrDrag()
        gesture.begin(50f, 60f, 1000, 4f)
        assertTrue(gesture.move(55f, 60f))
        assertTrue(gesture.move(50f, 60f))
        assertFalse(gesture.isTap(1100))
    }
    @Test fun longPressDoesNotClickOrMoveCameraUntilItMoves() {
        val gesture = WoWTouchGeometry.TapOrDrag()
        gesture.begin(50f, 60f, 0, 4f)
        assertFalse(gesture.isTap(300))
        assertFalse(gesture.isDragging)
        assertTrue(gesture.move(55f, 60f))
    }
    @Test fun cancelledGestureCannotClick() {
        val gesture = WoWTouchGeometry.TapOrDrag()
        gesture.begin(0f, 0f, 0, 4f)
        gesture.cancel()
        assertFalse(gesture.isTap(20))
    }
    @Test fun fitMapsScaledTopSurfaceAndRejectsLetterboxAndBottomPanel() {
        fun map(x: Float, y: Float) = WoWTouchGeometry.gamePoint(x, y, 20, 30, 1000, 800, 1920, 1080, 0)
        assertArrayEquals(floatArrayOf(960f, 540f), map(520f, 430f), 2f)
        assertNull(map(520f, 50f))
        assertNull(map(520f, 800f))
        assertNull(map(520f, 850f))
        assertNull(map(10f, 430f))
    }
    @Test fun portraitAspectSizedTopPaneMapsFullWidth() {
        assertArrayEquals(floatArrayOf(960f, 540f),
            WoWTouchGeometry.gamePoint(500f, 281.25f, 0, 0, 1000, 563, 1920, 1080, 0), 2f)
        assertNull(WoWTouchGeometry.gamePoint(500f, 600f, 0, 0, 1000, 563, 1920, 1080, 0))
    }
    @Test fun pillarboxStretchAndFillUseTheirRespectiveTransforms() {
        assertNull(WoWTouchGeometry.gamePoint(10f, 300f, 0, 0, 1600, 600, 800, 600, 0))
        assertArrayEquals(floatArrayOf(400f, 300f),
            WoWTouchGeometry.gamePoint(800f, 300f, 0, 0, 1600, 600, 800, 600, 0), .01f)
        assertArrayEquals(floatArrayOf(200f, 150f),
            WoWTouchGeometry.gamePoint(400f, 150f, 0, 0, 1600, 600, 800, 600, 1), .01f)
        assertArrayEquals(floatArrayOf(200f, 225f),
            WoWTouchGeometry.gamePoint(400f, 150f, 0, 0, 1600, 600, 800, 600, 2), .01f)
    }
    @Test fun unavailableSurfaceDoesNotProduceCoordinates() {
        assertNull(WoWTouchGeometry.gamePoint(0f, 0f, 0, 0, 0, 600, 800, 600, 0))
        assertNull(WoWTouchGeometry.gamePoint(0f, 0f, 0, 0, 800, 600, 0, 600, 0))
    }
    private fun view(): InputControlsView = mock<InputControlsView>().apply {
        whenever(isWoWGamePoint(any(), any())).thenReturn(true)
        whenever(width).thenReturn(2100)
        whenever(height).thenReturn(900)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        whenever(postDelayed(any(), any())).thenAnswer { call -> handler.postDelayed(call.getArgument(0), call.getArgument(1)) }
        whenever(removeCallbacks(any())).thenAnswer { call -> handler.removeCallbacks(call.getArgument(0)); true }
        whenever(resources).thenReturn(RuntimeEnvironment.getApplication().resources)
    }
    @Test fun tapClicksWithoutCameraDeflection() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.down(1, 1000f, 450f)
        pad.move(1, 1001f, 450f)
        pad.up(1)
        verify(view).performWoWGameClick(1000f, 450f)
        verify(view, never()).handleInputEvent(any<Binding>(), any(), any())
    }
    @Test fun dragAndCancellationNeverClickAndReleaseCamera() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.down(1, 1000f, 450f)
        pad.move(1, 1200f, 450f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        verify(view).handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, 1f)
        pad.cancel()
        verify(view).handleInputEvent(Binding.GAMEPAD_RIGHT_THUMB_RIGHT, true, 0f)
        verify(view, never()).performWoWGameClick(any(), any())
        pad.down(2, 1000f, 450f)
        pad.cancel()
        verify(view, never()).performWoWGameClick(any(), any())
    }
    @Test fun longTouchDoesNotClick() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.down(1, 1000f, 450f)
        ShadowSystemClock.advanceBy(Duration.ofMillis(251))
        pad.up(1)
        verify(view, never()).performWoWGameClick(any(), any())
    }
    @Test fun releaseInPanelDoesNotClickEvenIfTouchStartedInGame() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.setPanelBounds(450, 450)
        pad.down(1, 1200f, 449f)
        pad.move(1, 1200f, 451f)
        pad.up(1)
        verify(view, never()).performWoWGameClick(any(), any())
    }
    @Test fun bottomPanelTapDoesNotClickButGamePaneTapDoes() {
        val view = view()
        val pad = WoWTouchGamepad(view)
        pad.setPanelBounds(450, 470)
        pad.down(1, 1200f, 800f)
        pad.up(1)
        verify(view, never()).performWoWGameClick(any(), any())
        pad.down(2, 1200f, 200f)
        pad.up(2)
        verify(view).performWoWGameClick(1200f, 200f)
    }
}
