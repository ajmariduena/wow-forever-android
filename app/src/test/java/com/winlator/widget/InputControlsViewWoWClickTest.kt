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
        inOrder(server) {
            verify(server).injectPointerMove(960, 539)
            verify(server).injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
            verify(server).injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        }
        clearInvocations(server)
        view.performWoWGameClick(520f, 700f)
        verify(server, never()).injectPointerMove(any(), any())
        verify(server, never()).injectPointerButtonPress(any())
    }
}
