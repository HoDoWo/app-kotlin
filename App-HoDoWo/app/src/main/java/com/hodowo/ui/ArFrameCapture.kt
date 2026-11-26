package com.hodowo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.drawable.BitmapDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import android.os.Build
import android.view.Surface
import android.util.Log
import android.view.WindowManager
import coil.imageLoader
import coil.request.ImageRequest
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.DeadlineExceededException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import com.hodowo.viewmodel.DepthMap
import com.hodowo.viewmodel.FrameGeometry
import com.hodowo.viewmodel.Vec3
import com.hodowo.viewmodel.cameraRay
import com.hodowo.viewmodel.depthPoint
import com.hodowo.viewmodel.minus
import com.hodowo.viewmodel.plus
import com.hodowo.viewmodel.rayPlaneDistance
import com.hodowo.viewmodel.times
import com.hodowo.viewmodel.uprightRotation
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

private const val JPEG_QUALITY = 85
private const val REFERENCE_MAX_SIDE = 1024
private const val DEFAULT_SENSOR_ORIENTATION = 90
private const val DEGREES_PER_ROTATION_STEP = 90

/** A plane point outside the tracked polygon still counts when this close to it (meters). */
private const val PLANE_EDGE_MARGIN_M = 0.25f

/** No plane at all: the label goes this far along the ray, so it still shows near the control. */
private const val NO_PLANE_DEPTH_M = 0.5f

private const val TAG = "ArScan"

/**
 * One camera frame kept for AR auto-alignment: the image (NV21, as the sensor
 * reads it), its geometry, and where the camera was when it was taken.
 */
internal class CapturedFrame(
    val nv21: ByteArray,
    val geometry: FrameGeometry,
    val cameraPose: Pose,
    /** Null when depth is off, not supported or not ready yet. */
    val depth: DepthMap? = null
)

/** A point on a tracked plane, ready to anchor. */
internal class SurfacePoint(val plane: Plane, val pose: Pose)

/** The CPU image of [frame] with its geometry, or null when ARCore has none right now. */
internal fun captureFrame(frame: Frame, rotation: Int): CapturedFrame? {
    val image = try {
        frame.acquireCameraImage()
    } catch (e: NotYetAvailableException) {
        return null
    } catch (e: DeadlineExceededException) {
        return null
    } catch (e: ResourceExhaustedException) {
        return null
    }
    try {
        val intrinsics = frame.camera.imageIntrinsics
        val focal = intrinsics.focalLength
        val principal = intrinsics.principalPoint
        val dimensions = intrinsics.imageDimensions
        // Intrinsics are for the CPU image; scale them in case the sizes differ.
        val scaleX = image.width.toFloat() / dimensions[0]
        val scaleY = image.height.toFloat() / dimensions[1]
        val geometry = FrameGeometry(
            width = image.width,
            height = image.height,
            focalX = focal[0] * scaleX,
            focalY = focal[1] * scaleY,
            principalX = principal[0] * scaleX,
            principalY = principal[1] * scaleY,
            rotation = rotation
        )
        return CapturedFrame(image.toNv21(), geometry, frame.camera.pose, captureDepth(frame))
    } finally {
        image.close()
    }
}

/** The frame's depth map (millimeters), or null when ARCore has none. */
private fun captureDepth(frame: Frame): DepthMap? {
    val image = try {
        frame.acquireDepthImage16Bits()
    } catch (e: NotYetAvailableException) {
        return null
    } catch (e: IllegalStateException) {
        // Depth is off or not supported on this phone.
        return null
    }
    try {
        val plane = image.planes[0]
        val buffer = plane.buffer.order(ByteOrder.nativeOrder())
        val millimeters = ShortArray(image.width * image.height)
        for (row in 0 until image.height) {
            for (col in 0 until image.width) {
                millimeters[row * image.width + col] = buffer.getShort(row * plane.rowStride + col * plane.pixelStride)
            }
        }
        return DepthMap(image.width, image.height, millimeters)
    } finally {
        image.close()
    }
}

/** YUV_420_888 → NV21 (Y, then interleaved V and U), whatever the row and pixel strides. */
private fun Image.toNv21(): ByteArray {
    val chromaWidth = (width + 1) / 2
    val chromaHeight = (height + 1) / 2
    val out = ByteArray(width * height + 2 * chromaWidth * chromaHeight)
    val yPlane = planes[0]
    val yBuffer = yPlane.buffer
    var position = 0
    for (row in 0 until height) {
        yBuffer.position(row * yPlane.rowStride)
        yBuffer.get(out, position, width)
        position += width
    }
    val u = planes[1]
    val v = planes[2]
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            out[position++] = v.buffer.get(row * v.rowStride + col * v.pixelStride)
            out[position++] = u.buffer.get(row * u.rowStride + col * u.pixelStride)
        }
    }
    return out
}

/** The frame as an upright JPEG, the way the user sees it: what the AI looks at. */
internal fun CapturedFrame.toUprightJpeg(): ByteArray {
    val width = geometry.width
    val height = geometry.height
    val raw = ByteArrayOutputStream().also {
        YuvImage(nv21, ImageFormat.NV21, width, height, null).compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, it)
    }.toByteArray()
    if (geometry.rotation == 0) return raw
    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val upright = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { postRotate(geometry.rotation.toFloat()) }, true
    )
    return ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
}

/** Clockwise turn of the back camera image to be upright on this screen. */
internal fun Context.cameraUprightRotation(session: Session): Int {
    val sensor = runCatching {
        getSystemService(CameraManager::class.java)
            ?.getCameraCharacteristics(session.cameraConfig.cameraId)
            ?.get(CameraCharacteristics.SENSOR_ORIENTATION)
    }.getOrNull() ?: DEFAULT_SENSOR_ORIENTATION
    val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation
    } else {
        @Suppress("DEPRECATION")
        getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation
    } ?: Surface.ROTATION_0
    return uprightRotation(sensor, displayRotation * DEGREES_PER_ROTATION_STEP)
}

/**
 * Where the point the AI found in [frame] is on a plane ARCore tracks now: the ray
 * from the camera as it was when the frame was taken, to the nearest plane it meets
 * inside (or just outside) the tracked area.
 */
internal fun Session.surfacePointFor(frame: CapturedFrame, x: Float, y: Float, insideOnly: Boolean = false): SurfacePoint? {
    val local = cameraRay(x, y, frame.geometry)
    val origin = frame.cameraPose.translation.toVec3()
    val direction = frame.cameraPose.rotateVector(floatArrayOf(local.x, local.y, local.z)).toVec3()
    val planes = getAllTrackables(Plane::class.java)
        .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
    val hits = planes.mapNotNull { plane -> planeHit(plane, origin, direction) }
    val rayLength = length(direction)
    val best = hits.filter { it.inside }.minByOrNull { it.distance }
        ?: hits.filter { it.nearEdge && !insideOnly }.minByOrNull { it.distance }
    Log.d(
        TAG,
        "point (%.2f, %.2f) rot=%d planes=%d hits=%d inside=%d near=%d -> %s".format(
            Locale.US, x, y, frame.geometry.rotation, planes.size, hits.size,
            hits.count { it.inside }, hits.count { it.nearEdge },
            best?.let { "%.2f m".format(Locale.US, it.distance * rayLength) } ?: "none"
        )
    )
    return best?.let { SurfacePoint(it.plane, it.pose) }
}

/**
 * An anchor for the point the AI found, from the best source available:
 * 1. a tracked plane the ray meets inside its area (exact, e.g. a hob or a desk);
 * 2. ARCore depth at that point (vertical, glossy or plain fronts with no plane);
 * 3. a plane met just outside its area;
 * 4. [NO_PLANE_DEPTH_M] along the ray, so the label still shows near the control.
 */
internal fun Session.anchorFor(frame: CapturedFrame, x: Float, y: Float): Anchor? {
    planeAnchor(frame, x, y, insideOnly = true)?.let { return it.also { log(x, y, "plane") } }
    frame.depth?.let { depth ->
        depthPoint(x, y, frame.geometry, depth)?.let { local ->
            val world = frame.cameraPose.transformPoint(floatArrayOf(local.x, local.y, local.z))
            uprightAnchor(world.toVec3())?.let { return it.also { log(x, y, "depth") } }
        }
    }
    planeAnchor(frame, x, y, insideOnly = false)?.let { return it.also { log(x, y, "plane edge") } }
    val local = cameraRay(x, y, frame.geometry)
    val origin = frame.cameraPose.translation.toVec3()
    val direction = frame.cameraPose.rotateVector(floatArrayOf(local.x, local.y, local.z)).toVec3()
    return uprightAnchor(origin + direction * (NO_PLANE_DEPTH_M / length(direction)))
        .also { log(x, y, if (it != null) "fixed distance" else "none") }
}

private fun Session.planeAnchor(frame: CapturedFrame, x: Float, y: Float, insideOnly: Boolean): Anchor? =
    surfacePointFor(frame, x, y, insideOnly)?.let { point ->
        runCatching { point.plane.createAnchor(point.pose) }.getOrNull()
    }

/** Upright (world +Y up), so the label above the dot points up. */
private fun Session.uprightAnchor(point: Vec3): Anchor? =
    runCatching { createAnchor(Pose(floatArrayOf(point.x, point.y, point.z), floatArrayOf(0f, 0f, 0f, 1f))) }
        .onFailure { Log.w(TAG, "no anchor at $point", it) }
        .getOrNull()

private fun log(x: Float, y: Float, source: String) {
    Log.d(TAG, "label (%.2f, %.2f) placed by %s".format(Locale.US, x, y, source))
}

private fun length(vector: Vec3) = sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)

private class PlaneHit(val plane: Plane, val pose: Pose, val distance: Float, val inside: Boolean, val nearEdge: Boolean)

private fun planeHit(plane: Plane, origin: Vec3, direction: Vec3): PlaneHit? {
    val center = plane.centerPose
    val centerPoint = center.translation.toVec3()
    val distance = rayPlaneDistance(origin, direction, centerPoint, center.yAxis.toVec3()) ?: return null
    val point = origin + direction * distance
    // Same orientation as the plane: +Y is its normal, like an ARCore hit pose.
    val pose = Pose(floatArrayOf(point.x, point.y, point.z), center.rotationQuaternion)
    val offset = point - centerPoint
    val fromCenter = sqrt(offset.x * offset.x + offset.y * offset.y + offset.z * offset.z)
    val reach = max(plane.extentX, plane.extentZ) / 2 + PLANE_EDGE_MARGIN_M
    return PlaneHit(plane, pose, distance, plane.isPoseInPolygon(pose), fromCenter <= reach)
}

private fun FloatArray.toVec3() = Vec3(this[0], this[1], this[2])

/** The guide photo as a JPEG of at most 1024 px, from Coil's cache when the viewer already showed it. */
internal suspend fun loadReferenceJpeg(context: Context, url: String): ByteArray? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(REFERENCE_MAX_SIDE)
        .allowHardware(false)
        .build()
    val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return null
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
}
