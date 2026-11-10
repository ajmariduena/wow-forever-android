package com.winlator.widget

import com.winlator.renderer.XServerRenderer
import com.winlator.xserver.Pointer
import com.winlator.xserver.ScreenInfo
import com.winlator.xserver.XServer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class InputControlsViewWoWClickTest {
    @Test fun missingRendererIsDiagnosedAndDoesNotInject() {
        val server = mock<XServer>()
        val view = InputControlsView(RuntimeEnvironment.getApplication())
        ReflectionHelpers.setField(view, "xServer", server)
        view.performWoWGameClick(100f, 100f)
        verify(server, never()).injectPointerButtonPress(any())
        org.junit.Assert.assertTrue(org.robolectric.shadows.ShadowLog.getLogsForTag("WoWTouch").any { it.msg.contains("no renderer") })
    }

    @Test fun clickUsesActualSurfaceOffsetAndInjectsMovePressReleaseInOrder() {
        val server = mock<XServer>()
        ReflectionHelpers.setField(server, "screenInfo", ScreenInfo(1920, 1080))
        val renderer = mock<XServerRenderer>()
        val surface = mock<XServerView>()
        whenever(server.renderer).thenReturn(renderer)
        whenever(renderer.rendererView).thenReturn(surface)
        whenever(surface.width).thenReturn(1000)
        whenever(surface.height).thenReturn(563)
        doAnswer { invocation ->
            invocation.getArgument<IntArray>(0).apply { this[0] = 20; this[1] = 30 }
            null
        }.whenever(surface).getLocationInWindow(any())
        val view = InputControlsView(RuntimeEnvironment.getApplication())
        ReflectionHelpers.setField(view, "xServer", server)
        view.performWoWGameClick(520f, 311f)
        verify(server, never()).injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        inOrder(server) {
            verify(server).injectPointerMove(960, 539)
            verify(server).injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
            verify(server).injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        }
        clearInvocations(server)
        view.clickWoWPointer(520f, 311f, true)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        verify(server).injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
        verify(server).injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
        clearInvocations(server)
        view.scrollWoWPointer(520f, 311f, -2)
        verify(server, times(2)).injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN)
        verify(server, times(2)).injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN)
        clearInvocations(server)
        view.performWoWGameClick(520f, 700f)
        verify(server, never()).injectPointerMove(any(), any())
        verify(server, never()).injectPointerButtonPress(any())
        clearInvocations(server)
        view.performWoWGameClick(520f, 311f)
        view.setWoWMouseButton(false, true)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        verify(server, times(1)).injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        view.setWoWMouseButton(false, false)
        verify(server, times(2)).injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        clearInvocations(server)
        view.performWoWGameClick(520f, 311f)
        view.setProfile(null)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        verify(server, times(1)).injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
    }
}
