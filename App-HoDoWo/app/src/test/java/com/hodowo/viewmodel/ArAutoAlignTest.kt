package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Geometry of AR auto-alignment: image rotation, camera rays, ray-plane hits. */
class ArAutoAlignTest {

    private val eps = 1e-4f

    /** A 640 × 480 sensor image with the optical centre in the middle. */
    private fun geometry(rotation: Int) = FrameGeometry(
        width = 640, height = 480, focalX = 500f, focalY = 500f, principalX = 320f, principalY = 240f, rotation = rotation
    )

    @Test
    fun uprightRotationOfABackCamera() {
        assertEquals(90, uprightRotation(sensorOrientation = 90, displayRotation = 0))
        assertEquals(0, uprightRotation(90, 90))
        assertEquals(180, uprightRotation(90, 270))
        assertEquals(270, uprightRotation(270, 0))
    }

    @Test
    fun uprightPointsGoBackToTheSensorImage() {
        // Turned 90 degrees clockwise: the sensor's top-left corner is the upright top-right one.
        assertEquals(0f to 0f, uprightToSensor(1f, 0f, 90))
        assertEquals(0.3f to 0.8f, uprightToSensor(0.2f, 0.3f, 90).round())
        assertEquals(0.8f to 0.7f, uprightToSensor(0.2f, 0.3f, 180).round())
        assertEquals(0.7f to 0.2f, uprightToSensor(0.2f, 0.3f, 270).round())
        assertEquals(0.2f to 0.3f, uprightToSensor(0.2f, 0.3f, 0))
    }

    @Test
    fun theImageCentreLooksStraightAhead() {
        val ray = cameraRay(0.5f, 0.5f, geometry(90))
        assertEquals(0f, ray.x, eps)
        assertEquals(0f, ray.y, eps)
        assertEquals(-1f, ray.z, eps)
    }

    @Test
    fun rightAndUpInTheSensorImageAreRightAndUpForTheCamera() {
        // Right edge, middle row of the sensor image.
        val right = cameraRay(1f, 0.5f, geometry(0))
        assertEquals(320f / 500f, right.x, eps)
        assertEquals(0f, right.y, eps)
        // Top row: up is +Y.
        val top = cameraRay(0.5f, 0f, geometry(0))
        assertEquals(240f / 500f, top.y, eps)
    }

    @Test
    fun anUprightPointIsRotatedBeforeTheRay() {
        // Upright top-centre with a 90 degree turn is the sensor's middle of the left edge... (u=0, v=0.5).
        val ray = cameraRay(0.5f, 0f, geometry(90))
        assertEquals(-320f / 500f, ray.x, eps)
        assertEquals(0f, ray.y, eps)
    }

    @Test
    fun rayMeetsAPlaneInFront() {
        val distance = rayPlaneDistance(
            origin = Vec3(0f, 1f, 0f), direction = Vec3(0f, -1f, -1f),
            planePoint = Vec3(0f, 0f, 0f), normal = Vec3(0f, 1f, 0f)
        )
        assertEquals(1f, distance!!, eps)
        val hit = Vec3(0f, 1f, 0f) + Vec3(0f, -1f, -1f) * distance
        assertEquals(Vec3(0f, 0f, -1f), hit)
    }

    @Test
    fun planesBehindOrParallelAreMissed() {
        assertNull(rayPlaneDistance(Vec3(0f, 1f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f)))
        assertNull(rayPlaneDistance(Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f)))
    }

    @Test
    fun vectorHelpers() {
        val a = Vec3(1f, 2f, 3f)
        val b = Vec3(4f, 5f, 6f)
        assertEquals(Vec3(5f, 7f, 9f), a + b)
        assertEquals(Vec3(-3f, -3f, -3f), a - b)
        assertEquals(32f, a dot b, eps)
    }

    private fun depthMap(width: Int, height: Int, millimeters: (Int, Int) -> Int) = DepthMap(
        width, height, ShortArray(width * height) { i -> millimeters(i % width, i / width).toShort() }
    )

    @Test
    fun depthIsTheMedianAroundThePointInMeters() {
        // A front 800 mm away with one bad pixel (5 m) right on the point.
        val map = depthMap(160, 120) { col, row -> if (col == 80 && row == 60) 5000 else 800 }
        assertEquals(0.8f, map.metersAt(80f / 159f, 60f / 119f)!!, eps)
    }

    @Test
    fun noiseAndMissingDepthGiveNothing() {
        assertNull(depthMap(160, 120) { _, _ -> 0 }.metersAt(0.5f, 0.5f)) // no data
        assertNull(depthMap(160, 120) { _, _ -> 50 }.metersAt(0.5f, 0.5f)) // closer than 10 cm
        assertNull(depthMap(160, 120) { _, _ -> 800 }.metersAt(1.5f, 0.5f)) // outside the image
        assertNull(DepthMap(0, 0, ShortArray(0)).metersAt(0.5f, 0.5f))
    }

    @Test
    fun farDepthsAboveThirtyTwoMetersAreReadUnsigned() {
        // 40000 mm does not fit a signed short: it must not turn into a negative (valid-looking) value.
        assertNull(depthMap(10, 10) { _, _ -> 40000 }.metersAt(0.5f, 0.5f))
    }

    @Test
    fun aDepthPointIsTheRayScaledByTheDepth() {
        val map = depthMap(160, 120) { _, _ -> 600 }
        val centre = depthPoint(0.5f, 0.5f, geometry(90), map)!!
        assertEquals(0f, centre.x, eps)
        assertEquals(0f, centre.y, eps)
        assertEquals(-0.6f, centre.z, eps)
        // Right edge of the sensor image, 60 cm away: 0.64 × 0.6 to the right.
        val right = depthPoint(1f, 0.5f, geometry(0), map)!!
        assertEquals(0.384f, right.x, eps)
        assertEquals(-0.6f, right.z, eps)
    }

    @Test
    fun noDepthNoPoint() {
        assertNull(depthPoint(0.5f, 0.5f, geometry(90), depthMap(160, 120) { _, _ -> 0 }))
    }

    private fun Pair<Float, Float>.round() =
        (Math.round(first * 1000) / 1000f) to (Math.round(second * 1000) / 1000f)
}
