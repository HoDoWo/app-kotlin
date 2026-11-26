package com.hodowo.viewmodel

import kotlin.math.max
import kotlin.math.sqrt

/** Features closer than this to the camera are noise (meters). */
private const val MIN_FEATURE_DISTANCE_M = 0.1f

/** A feature counts for a ray when it is this close to it (meters), or this share of its distance. */
private const val FEATURE_RADIUS_M = 0.02f
private const val FEATURE_RADIUS_SHARE = 0.04f

/** Fewer features than this near a ray is not enough to trust a distance. */
private const val MIN_FEATURES = 3

/** ARCore point clouds: x, y, z, confidence per point. */
private const val POINT_STRIDE = 4
private const val MIN_CONFIDENCE = 0.2f

/** Points slightly outside the view still get a label, clamped to its edge by the UI. */
private const val VIEW_MARGIN = 0.15f

/** Where a point shows on screen, in pixels from the top-left corner of the view. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * How far along the ray from [origin] towards [direction] the real surface is, from the
 * feature points ARCore tracks ([points]: x, y, z, confidence in world space): the median
 * distance of the features close to the ray. Works on fronts with texture but no plane and
 * no depth (steel panels, printed labels). Null when too few features are near the ray.
 */
fun featureDistanceAlongRay(origin: Vec3, direction: Vec3, points: FloatArray): Float? {
    val length = sqrt(direction dot direction)
    if (length == 0f) return null
    val unit = direction * (1f / length)
    val distances = mutableListOf<Float>()
    var i = 0
    while (i + POINT_STRIDE <= points.size) {
        if (points[i + 3] >= MIN_CONFIDENCE) {
            val offset = Vec3(points[i], points[i + 1], points[i + 2]) - origin
            val along = offset dot unit
            if (along >= MIN_FEATURE_DISTANCE_M) {
                val aside = offset - unit * along
                val radius = max(FEATURE_RADIUS_M, FEATURE_RADIUS_SHARE * along)
                if ((aside dot aside) <= radius * radius) distances += along
            }
        }
        i += POINT_STRIDE
    }
    if (distances.size < MIN_FEATURES) return null
    distances.sort()
    return distances[distances.size / 2]
}

/**
 * Distance for every control: the measured one, or for those with none the median of the
 * others (the controls of one device sit at about the same distance), or [fallback].
 */
fun fillMissingDistances(measured: List<Float?>, fallback: Float): List<Float> {
    val known = measured.filterNotNull().sorted()
    val shared = if (known.isEmpty()) fallback else known[known.size / 2]
    return measured.map { it ?: shared }
}

/**
 * Where [point] (world space) shows in a view of [width] × [height] pixels, from the
 * camera's view-projection matrix ([viewProjection], column-major as ARCore and OpenGL
 * give it). Null behind the camera or well outside the view.
 */
fun projectToView(viewProjection: FloatArray, point: Vec3, width: Float, height: Float): ScreenPoint? {
    val clipX = row(viewProjection, 0, point)
    val clipY = row(viewProjection, 1, point)
    val clipW = row(viewProjection, 3, point)
    if (clipW <= 0f) return null
    val ndcX = clipX / clipW
    val ndcY = clipY / clipW
    val limit = 1f + VIEW_MARGIN
    if (ndcX !in -limit..limit || ndcY !in -limit..limit) return null
    return ScreenPoint((ndcX + 1f) / 2f * width, (1f - ndcY) / 2f * height)
}

/** [a] × [b], both 4 × 4 column-major. */
fun multiplyMatrices(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(MATRIX_SIZE)
    for (column in 0 until 4) {
        for (r in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[k * 4 + r] * b[column * 4 + k]
            out[column * 4 + r] = sum
        }
    }
    return out
}

private const val MATRIX_SIZE = 16

private fun row(m: FloatArray, r: Int, p: Vec3) = m[r] * p.x + m[4 + r] * p.y + m[8 + r] * p.z + m[12 + r]

/** Looking down more steeply than this (cos of ~35° from straight down), the surface is taken as flat. */
private const val LOOKING_DOWN = -0.82f

/**
 * The likely normal of a surface seen along [ray] when ARCore has no plane for it:
 * straight up when looking down at it (a hob, a desk), else horizontal and facing the
 * camera (an oven, a microwave, a washing machine front: appliance fronts are upright).
 */
fun facingNormal(ray: Vec3): Vec3 {
    val length = sqrt(ray dot ray)
    if (length == 0f || ray.y / length < LOOKING_DOWN) return Vec3(0f, 1f, 0f)
    val flat = sqrt(ray.x * ray.x + ray.z * ray.z)
    if (flat == 0f) return Vec3(0f, 1f, 0f)
    return Vec3(-ray.x / flat, 0f, -ray.z / flat)
}

/** The rotation (quaternion x, y, z, w) that turns +Y into [normal] (unit length). */
fun rotationFromUp(normal: Vec3): FloatArray {
    val w = 1f + normal.y
    if (w < 1e-6f) return floatArrayOf(1f, 0f, 0f, 0f)
    // cross((0, 1, 0), normal) = (normal.z, 0, -normal.x)
    val x = normal.z
    val z = -normal.x
    val norm = sqrt(x * x + z * z + w * w)
    return floatArrayOf(x / norm, 0f, z / norm, w / norm)
}
