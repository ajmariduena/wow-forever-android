package app.gamenative.externaldisplay

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.winlator.xserver.Pointer
import com.winlator.xserver.XServer
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Invisible view that receives IME (soft keyboard) input from Android system keyboard
 * and forwards it to XServer as keyboard events for the game.
 * 
 * This is needed when the system keyboard is pinned to the external display
 */
class IMEInputReceiver(
    context: Context,
    private val xServer: XServer,
    private val displayContext: Context = context,
) : FrameLayout(context) {

    val screenWidth: Int get() = xServer.screenInfo.width.toInt()
    val screenHeight: Int get() = xServer.screenInfo.height.toInt()

    suspend fun clickAt(x: Int, y: Int) {
        xServer.injectPointerMove(x, y)
        delay(60)
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
        delay(80)
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        delay(60)
    }

    fun movePointerTo(x: Int, y: Int) {
        xServer.injectPointerMove(x, y)
    }

    private var imeSessionActive = false

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onCheckIsTextEditor(): Boolean {
        val isEditor = imeSessionActive && hasFocus()
        Timber.d("IMEInputReceiver: onCheckIsTextEditor called - returning $isEditor")
        return isEditor
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        if (!imeSessionActive || !hasFocus()) {
            Timber.d(
                "IMEInputReceiver: onCreateInputConnection ignored (active=$imeSessionActive, hasFocus=${hasFocus()})",
            )
            return BaseInputConnection(this, false)
        }

        Timber.d("IMEInputReceiver: onCreateInputConnection called!")
        // Disable autocomplete/suggestions so each key commits immediately
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or 
                             EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                             EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or 
                              EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                              EditorInfo.IME_ACTION_NONE
        
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.let(::typeText)
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                Timber.d("IMEInputReceiver: deleteSurroundingText called")
                if (beforeLength > 0) {
                    repeat(beforeLength) {
                        xServer.injectKeyPress(com.winlator.xserver.XKeycode.KEY_BKSP, 0)
                        xServer.injectKeyRelease(com.winlator.xserver.XKeycode.KEY_BKSP)
                    }
                    Timber.v("IMEInputReceiver: Sent backspace x$beforeLength")
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                    xServer.injectKeyPress(com.winlator.xserver.XKeycode.KEY_BKSP, 0)
                    xServer.injectKeyRelease(com.winlator.xserver.XKeycode.KEY_BKSP)
                    Timber.v("IMEInputReceiver: Sent backspace from sendKeyEvent")
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Timber.d("IMEInputReceiver: onWindowFocusChanged: $hasWindowFocus")
        if (hasWindowFocus && imeSessionActive && !hasFocus()) {
            post { requestFocus() }
        }
    }

    fun typeText(text: CharSequence) {
        text.forEach(::sendCharacterToGame)
    }

    private fun sendCharacterToGame(char: Char) {
        val keyDispatch = GameTextKeyMapping.map(char)
        if (keyDispatch != null) {
            val metaState = if (keyDispatch.requiresShift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            val downEvent = KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_DOWN,
                keyDispatch.keyCode,
                0,
                metaState,
            )
            xServer.keyboard.onKeyEvent(downEvent)

            val upEvent = KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_UP,
                keyDispatch.keyCode,
                0,
                metaState,
            )
            xServer.keyboard.onKeyEvent(upEvent)
        } else {
            Timber.w("IMEInputReceiver: Unsupported text input character")
        }
    }

    fun showKeyboard() {
        post {
            imeSessionActive = true
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            val imm = displayContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            Timber.d("IMEInputReceiver: Requested to show keyboard")
        }
    }

    fun hideKeyboard() {
        post {
            val imm = displayContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
            imeSessionActive = false
            isFocusable = false
            isFocusableInTouchMode = false
            Timber.d("IMEInputReceiver: Requested to hide keyboard")
        }
    }
}
