package app.gamenative.ui.screen.wow

import android.view.KeyEvent
import app.gamenative.externaldisplay.GameTextKeyMapping
import com.winlator.xserver.Keyboard
import kotlinx.coroutines.delay
import kotlin.random.Random

internal object WoWRetrySequence {
    class Step(val keyCode: Int, val shift: Boolean = false, val control: Boolean = false)

    // No Enter or Tab: the user selects the password field and submits the login themselves.
    fun build(password: String, passwordFieldConfirmed: Boolean): List<Step>? {
        if (!passwordFieldConfirmed || password.isEmpty() || password.any { it !in ' '..'~' }) return null
        val text = password.map { char ->
            val stroke = GameTextKeyMapping.map(char) ?: return null
            Step(stroke.keyCode, stroke.requiresShift)
        }
        return listOf(Step(KeyEvent.KEYCODE_A, control = true), Step(KeyEvent.KEYCODE_DEL)) + text
    }

    suspend fun execute(keyboard: Keyboard, steps: List<Step>) {
        fun event(key: Int, down: Boolean, meta: Int = 0) {
            keyboard.onKeyEvent(KeyEvent(0, 0, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key, 0, meta))
        }
        try {
            delay(400)
            for (step in steps) {
                val meta = (if (step.shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0) or
                    (if (step.control) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0)
                if (step.control) event(KeyEvent.KEYCODE_CTRL_LEFT, true)
                try {
                    event(step.keyCode, true, meta)
                    delay(45)
                } finally {
                    event(step.keyCode, false, meta)
                    if (step.control) event(KeyEvent.KEYCODE_CTRL_LEFT, false)
                }
                delay(Random.nextLong(75, 126))
            }
        } finally {
            event(KeyEvent.KEYCODE_SHIFT_LEFT, false)
            event(KeyEvent.KEYCODE_CTRL_LEFT, false)
        }
    }
}
