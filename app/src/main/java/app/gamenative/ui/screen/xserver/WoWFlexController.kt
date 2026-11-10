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
    fun install(activity: Activity, root: View, gameHost: View, controls: InputControlsView, screenSize: String, restore: () -> Unit) {
        var scope: CoroutineScope? = null
        var hinge: Rect? = null
        var activeSplit: WoWDisplayPolicy.Split? = null
        val surface = (gameHost as? android.view.ViewGroup)?.getChildAt(0) ?: gameHost
        (gameHost as? android.view.ViewGroup)?.clipChildren = true
        fun syncPanelToSurface() {
            val split = activeSplit ?: return
            if (surface.height <= 0) return
            val surfaceLocation = IntArray(2)
            val controlsLocation = IntArray(2)
            surface.getLocationInWindow(surfaceLocation)
            controls.getLocationInWindow(controlsLocation)
            val edges = WoWDisplayPolicy.panelEdges(surfaceLocation[1], surface.height, controlsLocation[1], split.controlsTop - split.gameBottom)
            controls.setWoWPanelBounds(edges.gameBottom, edges.controlsTop)
        }
        fun applyLayout() {
            val bounds = hinge
            val location = IntArray(2)
            gameHost.getLocationInWindow(location)
            val rootLocation = IntArray(2)
            root.getLocationInWindow(rootLocation)
            val split = WoWDisplayPolicy.split(
                width = root.width,
                height = root.height,
                smallestScreenWidthDp = root.resources.configuration.smallestScreenWidthDp,
                screenSize = screenSize,
                hingeTop = bounds?.top?.minus(rootLocation[1]),
                hingeBottom = bounds?.bottom?.minus(rootLocation[1]),
            )
            activeSplit = split
            if (split != null) {
                val gameHeight = (rootLocation[1] + split.gameBottom - location[1]).coerceAtLeast(1)
                gameHost.setTag(R.id.wow_flex_height, gameHeight)
                if (gameHost.layoutParams.height != gameHeight) {
                    gameHost.layoutParams = gameHost.layoutParams.apply { height = gameHeight }
                }
                gameHost.post { syncPanelToSurface() }
            } else {
                gameHost.setTag(R.id.wow_flex_height, null)
                restore()
                controls.setWoWPanelBounds(-1, -1)
            }
        }
        val surfaceListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncPanelToSurface() }
        surface.addOnLayoutChangeListener(surfaceListener)
        controls.addOnLayoutChangeListener(surfaceListener)
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
                surface.removeOnLayoutChangeListener(surfaceListener)
                controls.removeOnLayoutChangeListener(surfaceListener)
            }
        })
        if (root.isAttachedToWindow) start()
    }
}
