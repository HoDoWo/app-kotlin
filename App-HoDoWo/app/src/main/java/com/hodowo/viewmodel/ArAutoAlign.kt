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

/** ARCore depth readings closer or farther than these (millimeters) are noise. */
private const val MIN_DEPTH_MM = 100
private const val MAX_DEPTH_MM = 8000
private const val MM_PER_M = 1000f
private const val UNSIGNED_SHORT = 0xFFFF

/** Half size of the window around a point whose depth readings are pooled (5 × 5). */
private const val DEPTH_WINDOW = 2

/**
 * ARCore's depth image of a frame (`acquireDepthImage16Bits`): millimeters along the
 * camera's optical axis, row by row, in the sensor orientation of the CPU image.
 * Glossy or plain fronts that give ARCore no plane still get depth.
 */
class DepthMap(val width: Int, val height: Int, private val millimeters: ShortArray) {

    /**
     * Meters at ([u], [v]) of the sensor image (0..1): the median of the valid readings
     * in a 5 × 5 window, so one bad pixel does not move the label; null without data.
     */
    fun metersAt(u: Float, v: Float): Float? {
        if (width <= 0 || height <= 0 || u !in 0f..1f || v !in 0f..1f) return null
        val column = (u * (width - 1)).toInt()
        val row = (v * (height - 1)).toInt()
        val readings = mutableListOf<Int>()
        for (r in (row - DEPTH_WINDOW)..(row + DEPTH_WINDOW)) {
            for (c in (column - DEPTH_WINDOW)..(column + DEPTH_WINDOW)) {
                if (r !in 0 until height || c !in 0 until width) continue
                val mm = millimeters[r * width + c].toInt() and UNSIGNED_SHORT
                if (mm in MIN_DEPTH_MM..MAX_DEPTH_MM) readings += mm
            }
        }
        if (readings.isEmpty()) return null
        readings.sort()
        return readings[readings.size / 2] / MM_PER_M
    }
}

/**
 * The point the AI found, in the camera's own frame (meters), from the depth map:
 * depth is along the optical axis, so the ray with z = -1 is scaled by it.
 */
fun depthPoint(x: Float, y: Float, frame: FrameGeometry, depth: DepthMap): Vec3? {
    val (u, v) = uprightToSensor(x, y, frame.rotation)
    val meters = depth.metersAt(u, v) ?: return null
    return cameraRay(x, y, frame) * meters
}
