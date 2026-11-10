package app.gamenative.ui.screen.xserver

import android.content.pm.ActivityInfo
import kotlin.math.ceil

internal object WoWDisplayPolicy {
    fun orientation(smallestScreenWidthDp: Int): Int =
        if (smallestScreenWidthDp >= 600) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    data class Split(val gameBottom: Int, val controlsTop: Int)

    fun split(
        width: Int,
        height: Int,
        smallestScreenWidthDp: Int,
        screenSize: String,
        hingeTop: Int? = null,
        hingeBottom: Int? = null,
    ): Split? {
        if (width <= 0 || height <= 0) return null
        if (hingeTop != null && hingeBottom != null && hingeTop > 0 && hingeTop <= hingeBottom && hingeBottom < height) {
            return Split(hingeTop, hingeBottom)
        }
        if (smallestScreenWidthDp < 600 || width >= height) return null
        val dimensions = screenSize.split('x').map { it.toIntOrNull() }
        val renderWidth = dimensions.getOrNull(0)?.takeIf { it > 0 } ?: return null
        val renderHeight = dimensions.getOrNull(1)?.takeIf { it > 0 } ?: return null
        val bottom = ceil(width.toDouble() * renderHeight / renderWidth).toInt()
        return if (bottom in 1 until height) Split(bottom, bottom) else null
    }
}
