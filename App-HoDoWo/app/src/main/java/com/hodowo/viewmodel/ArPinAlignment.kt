package com.hodowo.viewmodel

import kotlin.math.abs
import kotlin.math.hypot

/** A point in meters in the frame of the first anchor: +Y is the surface normal (#241). */
data class Vec3(val x: Float, val y: Float, val z: Float)

/**
 * Height over width of the guide photo when it is not known yet: phone photos
 * are usually taken in portrait, 3:4.
 */
const val DEFAULT_PHOTO_ASPECT = 4f / 3f

/** Pins closer than this in the photo (in photo widths) are one control. */
private const val SAME_POINT = 0.02f

/** Two alignment points must be at least this far apart in the photo (in photo widths). */
private const val MIN_ALIGN_DISTANCE = 0.12f

/** A photo of an appliance panel covers about 8 cm to 3 m of the real appliance across. */
private const val MIN_METERS_PER_WIDTH = 0.08f
private const val MAX_METERS_PER_WIDTH = 3f

/** The second point must lie on the surface of the first (meters along the normal). */
private const val MAX_OFF_SURFACE = 0.1f

/** How the user puts the guide pins on the appliance (#241). */
sealed interface AlignPlan {
    /** No step has a pin: the user places one arrow where they like. */
    data object Free : AlignPlan

    /** All pins are on one control: one point puts it on the appliance. */
    data class OnePoint(val first: Int) : AlignPlan

    /** Two controls far apart in the photo fix the position, size and turn of every pin. */
    data class TwoPoints(val first: Int, val second: Int) : AlignPlan

    companion object {
        /**
         * The first pinned step, then the pinned step farthest from it in the photo:
         * the farther apart the two points, the smaller the error on the other pins.
         */
        fun of(steps: List<ManualStep>, aspect: Float = DEFAULT_PHOTO_ASPECT): AlignPlan {
            val pinned = steps.mapIndexedNotNull { index, step -> step.pin?.let { index to it } }
            val (first, origin) = pinned.firstOrNull() ?: return Free
            val (farthest, far) = pinned.maxBy { (_, pin) -> distance(origin, pin, aspect) }
            return if (distance(origin, far, aspect) >= MIN_ALIGN_DISTANCE) {
                TwoPoints(first, farthest)
            } else {
                OnePoint(first)
            }
        }
    }
}

/** Two pins on the same control (within about 2% of the photo width). */
fun samePoint(a: StepPin, b: StepPin, aspect: Float = DEFAULT_PHOTO_ASPECT): Boolean =
    distance(a, b, aspect) < SAME_POINT

private fun distance(a: StepPin, b: StepPin, aspect: Float): Float =
    hypot(b.x - a.x, (b.y - a.y) * aspect)

/**
 * Places photo pins on the real surface (#241). The photo and the surface are
 * related by a similarity (shift, turn, scale), found from two pins the user
 * aimed at: the first is the anchor origin, the second is [align]'s
 * `secondLocal`. Every position is in the anchor frame, so pins move with the
 * anchor and do not drift away from it.
 */
class PinMapper private constructor(
    private val origin: StepPin,
    private val aspect: Float,
    /** Complex factor photo -> surface: real part, imaginary part. */
    private val re: Float,
    private val im: Float,
    /** +1 when the camera is on the +Y side of the anchor, -1 otherwise. */
    val side: Float
) {
    /** Meters on the appliance for one photo width: the scale the user's two points gave. */
    val metersPerPhotoWidth: Float get() = hypot(re, im)

    /** Where [pin] is on the surface, [lift] meters in front of it (towards the camera). */
    fun localPosition(pin: StepPin, lift: Float = 0f): Vec3 {
        val vx = pin.x - origin.x
        val vy = (pin.y - origin.y) * aspect
        val right = re * vx - im * vy
        val down = re * vy + im * vx
        // Surface axes: right = +X, down = X × normal = side * Z.
        return Vec3(right, side * lift, side * down)
    }

    companion object {
        /** Only the first pin: it is the anchor itself, at real scale 0. */
        fun single(origin: StepPin, cameraLocal: Vec3): PinMapper =
            PinMapper(origin, DEFAULT_PHOTO_ASPECT, 0f, 0f, sideOf(cameraLocal))

        /**
         * The mapping from [first] (at the anchor) and [second] (aimed at
         * [secondLocal]), or null when the two points cannot be the same two
         * controls of the photo: too close, implausible size, or not on one surface.
         */
        fun align(
            first: StepPin,
            second: StepPin,
            secondLocal: Vec3,
            cameraLocal: Vec3,
            aspect: Float = DEFAULT_PHOTO_ASPECT
        ): PinMapper? {
            val side = sideOf(cameraLocal)
            val ex = second.x - first.x
            val ey = (second.y - first.y) * aspect
            val photoLength = hypot(ex, ey)
            // The real offset in surface axes: right = X, down = side * Z.
            val wx = secondLocal.x
            val wy = side * secondLocal.z
            val realLength = hypot(wx, wy)
            if (photoLength < MIN_ALIGN_DISTANCE || abs(secondLocal.y) > MAX_OFF_SURFACE) return null
            val scale = realLength / photoLength
            if (scale !in MIN_METERS_PER_WIDTH..MAX_METERS_PER_WIDTH) return null
            // Complex division w / e.
            val norm = ex * ex + ey * ey
            val re = (wx * ex + wy * ey) / norm
            val im = (wy * ex - wx * ey) / norm
            return PinMapper(first, aspect, re, im, side)
        }

        private fun sideOf(cameraLocal: Vec3): Float = if (cameraLocal.y >= 0f) 1f else -1f
    }
}

/** Steps whose pins are on the same control share one marker (#241). */
data class PinGroup(val pin: StepPin, val stepIndices: List<Int>)

/** The markers of a guide: one per control, in the order of the first step that uses it. */
fun pinGroups(steps: List<ManualStep>, aspect: Float = DEFAULT_PHOTO_ASPECT): List<PinGroup> {
    val groups = mutableListOf<PinGroup>()
    steps.forEachIndexed { index, step ->
        val pin = step.pin ?: return@forEachIndexed
        val at = groups.indexOfFirst { samePoint(it.pin, pin, aspect) }
        if (at < 0) {
            groups += PinGroup(pin, listOf(index))
        } else {
            groups[at] = groups[at].copy(stepIndices = groups[at].stepIndices + index)
        }
    }
    return groups
}

/** The label of a marker: the numbers of its steps, e.g. "2" or "2, 5". */
fun PinGroup.label(steps: List<ManualStep>): String =
    stepIndices.mapNotNull { steps.getOrNull(it)?.number }.distinct().joinToString(", ")
