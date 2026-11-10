package com.winlator.inputcontrols

import org.junit.Assert.*
import org.junit.Test

class WoWCameraVelocityTest {
    private fun camera(x: Float = 0f, y: Float = 0f, sensitivity: Float = 1f) = WoWCameraVelocity().apply {
        begin(x, y, 0, 1000f, sensitivity)
    }
    @Test fun noMovementProducesZero() {
        val camera = camera()
        camera.move(0f, 0f, 16)
        assertArrayEquals(floatArrayOf(0f, 0f), camera.frame(16), 0f)
    }
    private fun constantDrag(delta: Float, interval: Long = 16): Float {
        val camera = camera()
        var output = 0f
        for (i in 1..20) {
            camera.move(i * delta, 0f, i * interval)
            output = camera.frame(i * interval)[0]
        }
        return output
    }
    @Test fun constantDragIsProportionalWithMildAcceleration() {
        val slow = constantDrag(1.6f)
        val twice = constantDrag(3.2f)
        assertTrue(slow > .1f)
        assertEquals(2f, twice / slow, .05f)
    }
    @Test fun sampleRateDoesNotChangeConstantVelocity() {
        assertEquals(constantDrag(1.6f), constantDrag(3.2f, 32), .001f)
    }
    @Test fun diagonalFlickClampsToUnitCircle() {
        val camera = camera()
        camera.move(500f, -500f, 16)
        val output = camera.frame(16)
        assertEquals(1.0, Math.hypot(output[0].toDouble(), output[1].toDouble()), .0001)
        assertTrue(output[0] > 0 && output[1] < 0)
    }
    @Test fun stationaryFingerDecaysAndReachesZeroWithin60ms() {
        val camera = camera()
        camera.move(5f, 0f, 16)
        val initial = camera.frame(16)[0]
        camera.move(5f, 0f, 40)
        val decaying = camera.frame(46)[0]
        assertEquals(initial / 2, decaying, .0001f)
        assertArrayEquals(floatArrayOf(0f, 0f), camera.frame(76), 0f)
        assertFalse(camera.isActive(76))
    }
    @Test fun sensitivityScalesFineAimAndReleaseStopsImmediately() {
        fun sample(sensitivity: Float) = camera(sensitivity = sensitivity).apply { move(1f, 0f, 16) }
        val camera = sample(2f)
        assertEquals(sample(1f).frame(16)[0] * 2, camera.frame(16)[0], .0001f)
        camera.stop()
        assertArrayEquals(floatArrayOf(0f, 0f), camera.frame(17), 0f)
    }
    @Test fun translatedOriginDoesNotChangeTouchLook() {
        val first = camera().apply { move(2f, -1f, 16) }.frame(16)
        val translated = camera(100f, 400f).apply { move(102f, 399f, 16) }.frame(16)
        assertArrayEquals(first, translated, 0f)
    }
    @Test fun fortyPercentWidthDragIsApproximatelyQuarterTurnAtChosenYaw() {
        val camera = camera()
        var angle = 0f
        for (i in 1..64) {
            camera.move(i * 6.25f, 0f, i * 16L)
            angle += camera.frame(i * 16L)[0] * 3 * .016f
        }
        assertEquals(Math.PI / 2, angle.toDouble(), .15)
    }
}
