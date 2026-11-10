package app.gamenative.externaldisplay

import android.view.KeyEvent
import com.winlator.xserver.Keyboard
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import app.gamenative.ui.screen.wow.WoWRetrySequence
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameTextKeyboardTest {
    @Test fun symbolsAndUppercaseReachXServerThroughImeKeyboardPath() {
        val cases = mapOf('@' to XKeycode.KEY_2, '.' to XKeycode.KEY_PERIOD, '_' to XKeycode.KEY_MINUS,
            '-' to XKeycode.KEY_MINUS, '+' to XKeycode.KEY_EQUAL, '!' to XKeycode.KEY_1, 'A' to XKeycode.KEY_A)
        for ((char, xKey) in cases) {
            val server = mock<XServer>()
            val keyboard = Keyboard(server)
            val stroke = GameTextKeyMapping.map(char)!!
            val meta = if (stroke.requiresShift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            keyboard.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, stroke.keyCode, 0, meta))
            keyboard.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, stroke.keyCode, 0, meta))
            verify(server).injectKeyPress(xKey, char.code)
            verify(server).injectKeyRelease(xKey)
        }
    }
    @Test fun cancellationReleasesHeldKeyAndModifiers() = runBlocking {
        val keyboard = mock<Keyboard>()
        val downSeen = CompletableDeferred<Unit>()
        whenever(keyboard.onKeyEvent(any())).thenAnswer { invocation ->
            val event = invocation.getArgument<KeyEvent>(0)
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_A) downSeen.complete(Unit)
            true
        }
        val job = launch { WoWRetrySequence.execute(keyboard, WoWRetrySequence.build("Test!", true)!!) }
        withTimeout(2000) { downSeen.await() }
        job.cancelAndJoin()
        val events = argumentCaptor<KeyEvent>()
        verify(keyboard, atLeastOnce()).onKeyEvent(events.capture())
        for (key in listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT)) {
            assertEquals(KeyEvent.ACTION_UP, events.allValues.last { it.keyCode == key }.action)
        }
    }
}
