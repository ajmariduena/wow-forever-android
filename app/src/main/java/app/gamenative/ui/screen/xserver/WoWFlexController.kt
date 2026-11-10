package app.gamenative.ui.screen.xserver

import android.app.Activity
import android.graphics.Rect
import android.view.View
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import app.gamenative.R
import com.winlator.widget.InputControlsView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal object WoWFlexController {
    fun install(activity: Activity, root: View, gameHost: View, controls: InputControlsView, restore: () -> Unit) {
        var scope: CoroutineScope? = null
        var hinge: Rect? = null
        fun applyLayout() {
            val bounds = hinge
            val location = IntArray(2)
            gameHost.getLocationInWindow(location)
            val controlLocation = IntArray(2)
            controls.getLocationInWindow(controlLocation)
            if (bounds != null && bounds.top > location[1]) {
                val top = bounds.top - location[1]
                gameHost.setTag(R.id.wow_flex_height, top)
                if (gameHost.layoutParams.height != top) {
                    gameHost.layoutParams = gameHost.layoutParams.apply { height = top }
                }
                controls.setWoWHinge(bounds.top - controlLocation[1], bounds.bottom - controlLocation[1])
            } else {
                gameHost.setTag(R.id.wow_flex_height, null)
                restore()
                controls.setWoWHinge(-1, -1)
            }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyLayout() }
        fun start() {
            if (scope != null) return
            scope = CoroutineScope(Dispatchers.Main.immediate + Job()).also { current ->
                current.launch {
                    WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
                        hinge = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull {
                            it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL
                        }?.bounds
                        applyLayout()
                    }
                }
            }
        }
        root.addOnLayoutChangeListener(layoutListener)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = start()
            override fun onViewDetachedFromWindow(v: View) {
                scope?.cancel()
                scope = null
                root.removeOnLayoutChangeListener(layoutListener)
            }
        })
        if (root.isAttachedToWindow) start()
    }
}
