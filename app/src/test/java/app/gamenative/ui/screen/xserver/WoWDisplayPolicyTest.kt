package app.gamenative.ui.screen.xserver

import android.content.pm.ActivityInfo
import org.junit.Assert.*
import org.junit.Test

class WoWDisplayPolicyTest {
    @Test fun panelBeginsAtMeasuredSurfaceBottomAndTracksResize() {
        assertEquals(WoWDisplayPolicy.Split(720, 720), WoWDisplayPolicy.panelEdges(40, 700, 20, 0))
        assertEquals(WoWDisplayPolicy.Split(620, 620), WoWDisplayPolicy.panelEdges(40, 600, 20, 0))
        assertEquals(WoWDisplayPolicy.Split(720, 740), WoWDisplayPolicy.panelEdges(40, 700, 20, 20))
    }
    @Test fun coverStaysSensorLandscape() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, WoWDisplayPolicy.orientation(320))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, WoWDisplayPolicy.orientation(599))
    }
    @Test fun innerScreenUsesFullSensorInEitherOrientation() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, WoWDisplayPolicy.orientation(600))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, WoWDisplayPolicy.orientation(673))
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, WoWDisplayPolicy.orientation(673))
    }
    @Test fun portraitInnerFitsGameAtFullWidth() {
        assertEquals(WoWDisplayPolicy.Split(1107, 1107), WoWDisplayPolicy.split(1968, 2184, 673, "1920x1080"))
        assertEquals(WoWDisplayPolicy.Split(1476, 1476), WoWDisplayPolicy.split(1968, 2184, 673, "1280x960"))
    }
    @Test fun landscapeInnerAndCoverDoNotSplit() {
        assertNull(WoWDisplayPolicy.split(2184, 1968, 673, "1920x1080"))
        assertNull(WoWDisplayPolicy.split(900, 2100, 320, "1920x1080"))
        assertNull(WoWDisplayPolicy.split(2100, 900, 320, "1920x1080"))
    }
    @Test fun hingeWinsAndReservesItsGap() {
        assertEquals(WoWDisplayPolicy.Split(1000, 1020), WoWDisplayPolicy.split(1968, 2184, 673, "1920x1080", 1000, 1020))
        assertEquals(WoWDisplayPolicy.Split(900, 900), WoWDisplayPolicy.split(2184, 1968, 673, "1920x1080", 900, 900))
    }
    @Test fun leavingFlexReturnsToPortraitSplitOrLandscapeOverlay() {
        assertEquals(WoWDisplayPolicy.Split(1107, 1107), WoWDisplayPolicy.split(1968, 2184, 673, "1920x1080"))
        assertNull(WoWDisplayPolicy.split(2184, 1968, 673, "1920x1080"))
    }
    @Test fun invalidHingeFallsBackToPortraitAspectRatio() {
        assertEquals(WoWDisplayPolicy.Split(1107, 1107), WoWDisplayPolicy.split(1968, 2184, 673, "1920x1080", 0, 5000))
    }
    @Test fun unavailableSizeAndMalformedResolutionDoNotSplit() {
        assertNull(WoWDisplayPolicy.split(0, 2184, 673, "1920x1080"))
        assertNull(WoWDisplayPolicy.split(1968, 2184, 673, "bad"))
        assertNull(WoWDisplayPolicy.split(1968, 2184, 673, "0x1080"))
        assertNull(WoWDisplayPolicy.split(1968, 2184, 673, "1080x4000"))
    }
}
