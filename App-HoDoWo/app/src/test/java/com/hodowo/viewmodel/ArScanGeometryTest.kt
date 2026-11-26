package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArScanGeometryTest {

    private val origin = Vec3(0f, 0f, 0f)
    private val forward = Vec3(0f, 0f, -1f)

    private fun cloud(vararg points: Vec3, confidence: Float = 1f) =
        points.flatMap { listOf(it.x, it.y, it.z, confidence) }.toFloatArray()

    @Test
    fun featuresOnTheRayGiveTheirMedianDistance() {
        val points = cloud(Vec3(0f, 0f, -0.80f), Vec3(0.005f, 0f, -0.82f), Vec3(0f, 0.01f, -0.81f))
        assertEquals(0.81f, featureDistanceAlongRay(origin, forward, points)!!, 1e-4f)
    }

    @Test
    fun featuresFarFromTheRayAreIgnored() {
        val points = cloud(Vec3(0.3f, 0f, -0.8f), Vec3(-0.3f, 0f, -0.8f), Vec3(0f, 0.3f, -0.8f))
        assertNull(featureDistanceAlongRay(origin, forward, points))
    }

    @Test
    fun tooFewOrUnsureFeaturesGiveNoDistance() {
        assertNull(featureDistanceAlongRay(origin, forward, cloud(Vec3(0f, 0f, -0.8f), Vec3(0f, 0f, -0.81f))))
        val unsure = cloud(Vec3(0f, 0f, -0.8f), Vec3(0f, 0f, -0.8f), Vec3(0f, 0f, -0.8f), confidence = 0.05f)
        assertNull(featureDistanceAlongRay(origin, forward, unsure))
    }

    @Test
    fun featuresBehindTheCameraAreIgnored() {
        val points = cloud(Vec3(0f, 0f, 0.8f), Vec3(0f, 0f, 0.81f), Vec3(0f, 0f, 0.82f))
        assertNull(featureDistanceAlongRay(origin, forward, points))
    }

    @Test
    fun theRayLengthDoesNotMatter() {
        val points = cloud(Vec3(0f, 0f, -0.80f), Vec3(0f, 0f, -0.80f), Vec3(0f, 0f, -0.80f))
        assertEquals(0.8f, featureDistanceAlongRay(origin, Vec3(0f, 0f, -5f), points)!!, 1e-4f)
    }

    @Test
    fun controlsWithoutADistanceTakeTheOthersMedian() {
        assertEquals(listOf(0.6f, 0.7f, 0.7f, 0.9f), fillMissingDistances(listOf(0.6f, null, 0.7f, 0.9f), 0.5f))
    }

    @Test
    fun withNoDistanceAtAllTheFallbackIsUsed() {
        assertEquals(listOf(0.5f, 0.5f), fillMissingDistances(listOf(null, null), 0.5f))
    }

    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** Perspective looking down -Z: w = -z. */
    private val perspective = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, -1f, -1f, 0f, 0f, -0.2f, 0f)

    @Test
    fun theCentreOfTheViewIsStraightAhead() {
        val p = projectToView(perspective, Vec3(0f, 0f, -1f), 1000f, 2000f)
        assertNotNull(p)
        assertEquals(500f, p!!.x, 1e-3f)
        assertEquals(1000f, p.y, 1e-3f)
    }

    @Test
    fun upInTheWorldIsUpOnScreen() {
        val p = projectToView(perspective, Vec3(0.5f, 0.5f, -1f), 1000f, 2000f)!!
        assertEquals(750f, p.x, 1e-3f)
        assertEquals(500f, p.y, 1e-3f)
    }

    @Test
    fun pointsBehindTheCameraOrFarOutsideAreHidden() {
        assertNull(projectToView(perspective, Vec3(0f, 0f, 1f), 1000f, 2000f))
        assertNull(projectToView(perspective, Vec3(5f, 0f, -1f), 1000f, 2000f))
    }

    @Test
    fun multiplyingByTheIdentityChangesNothing() {
        assertEquals(perspective.toList(), multiplyMatrices(perspective, identity).toList())
        assertEquals(perspective.toList(), multiplyMatrices(identity, perspective).toList())
    }

    @Test
    fun aViewMatrixMovesTheCamera() {
        // The camera 1 m to the right: a point straight ahead of it lands in the centre.
        val view = identity.copyOf().also { it[12] = -1f }
        val p = projectToView(multiplyMatrices(perspective, view), Vec3(1f, 0f, -2f), 1000f, 1000f)!!
        assertEquals(500f, p.x, 1e-3f)
        assertEquals(500f, p.y, 1e-3f)
    }
}

class ArSurfaceNormalTest {

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-4f)
        assertEquals(expected.y, actual.y, 1e-4f)
        assertEquals(expected.z, actual.z, 1e-4f)
    }

    /** Rotates [v] by the quaternion [q] (x, y, z, w). */
    private fun rotate(q: FloatArray, v: Vec3): Vec3 {
        val (x, y, z, w) = q.toList()
        val u = Vec3(x, y, z)
        val t = Vec3(u.y * v.z - u.z * v.y, u.z * v.x - u.x * v.z, u.x * v.y - u.y * v.x) * 2f
        val c = Vec3(u.y * t.z - u.z * t.y, u.z * t.x - u.x * t.z, u.x * t.y - u.y * t.x)
        return v + t * w + c
    }

    @Test
    fun anUprightFrontFacesTheCamera() {
        assertVec(Vec3(0f, 0f, 1f), facingNormal(Vec3(0f, -0.2f, -1f)))
        assertVec(Vec3(-1f, 0f, 0f), facingNormal(Vec3(2f, 0.1f, 0f)))
    }

    @Test
    fun lookingDownTheSurfaceIsFlat() {
        assertVec(Vec3(0f, 1f, 0f), facingNormal(Vec3(0f, -1f, -0.3f)))
    }

    @Test
    fun theRotationTurnsUpIntoTheNormal() {
        listOf(Vec3(0f, 0f, 1f), Vec3(-1f, 0f, 0f), Vec3(0.6f, 0f, -0.8f), Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f))
            .forEach { normal -> assertVec(normal, rotate(rotationFromUp(normal), Vec3(0f, 1f, 0f))) }
    }
}
