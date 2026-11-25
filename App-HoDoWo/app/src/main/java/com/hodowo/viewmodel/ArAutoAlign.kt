package com.hodowo.viewmodel

import kotlin.math.abs

/*
 * Geometry of AR auto-alignment. The AI answers seconds after the frame was
 * taken, when the phone has moved: so the answer is not hit-tested on the
 * screen, but turned into a ray from the camera *as it was* when the frame was
 * taken, and that ray meets the planes ARCore tracks now.
 */

private const val QUARTER = 90
private const val HALF = 180
private const val THREE_QUARTERS = 270
private const val FULL_TURN = 360

/** Rays almost parallel to a plane do not meet it at a usable point. */
private const val MIN_RAY_PLANE_COS = 1e-3f

operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
operator fun Vec3.times(factor: Float) = Vec3(x * factor, y * factor, z * factor)
infix fun Vec3.dot(other: Vec3) = x * other.x + y * other.y + z * other.z

/**
 * The CPU camera image of the frame sent to the AI. [width] × [height] and the
 * intrinsics are those of the image as the sensor reads it (usually landscape);
 * [rotation] is how much it was turned clockwise to be upright on the screen.
 */
data class FrameGeometry(
    val width: Int,
    val height: Int,
    val focalX: Float,
    val focalY: Float,
    val principalX: Float,
    val principalY: Float,
    val rotation: Int
)

/** Clockwise turn that makes a back-camera image upright, from the sensor and display rotations. */
fun uprightRotation(sensorOrientation: Int, displayRotation: Int): Int =
    ((sensorOrientation - displayRotation) % FULL_TURN + FULL_TURN) % FULL_TURN

/**
 * A point of the upright image (what the AI saw) back in the sensor image,
 * both normalised 0..1 from the left and top edges.
 */
fun uprightToSensor(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
    QUARTER -> y to 1f - x
    HALF -> 1f - x to 1f - y
    THREE_QUARTERS -> 1f - y to x
    else -> x to y
}

/**
 * Direction, in the camera's own frame, of the ray through the point the AI
 * found. ARCore's camera pose looks down -Z with +X right and +Y up relative
 * to the sensor image, so image rows going down are -Y.
 */
fun cameraRay(x: Float, y: Float, frame: FrameGeometry): Vec3 {
    val (u, v) = uprightToSensor(x, y, frame.rotation)
    val pixelX = u * frame.width
    val pixelY = v * frame.height
    return Vec3((pixelX - frame.principalX) / frame.focalX, -(pixelY - frame.principalY) / frame.focalY, -1f)
}

/**
 * Distance along [direction] (in its units) from [origin] to the plane through
 * [planePoint] with [normal]; null when the plane is behind or parallel.
 */
fun rayPlaneDistance(origin: Vec3, direction: Vec3, planePoint: Vec3, normal: Vec3): Float? {
    val facing = direction dot normal
    if (abs(facing) < MIN_RAY_PLANE_COS) return null
    val distance = ((planePoint - origin) dot normal) / facing
    return distance.takeIf { it > 0f }
}

/** Where the AI search for two controls ended (#18 auto-alignment). */
enum class AutoAlignResult {
    /** Both controls found: every pin is on the appliance. */
    BOTH,

    /** Only the first control: the user aims at the second. */
    FIRST_ONLY,

    /** Nothing usable: the user aims at both. */
    NONE,

    /** No answer (network, server): the user aims at both. */
    ERROR
}
