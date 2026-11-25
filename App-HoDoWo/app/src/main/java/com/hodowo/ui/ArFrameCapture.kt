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
import android.view.WindowManager
import coil.imageLoader
import coil.request.ImageRequest
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.DeadlineExceededException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import com.hodowo.viewmodel.FrameGeometry
import com.hodowo.viewmodel.Vec3
import com.hodowo.viewmodel.cameraRay
import com.hodowo.viewmodel.minus
import com.hodowo.viewmodel.plus
import com.hodowo.viewmodel.rayPlaneDistance
import com.hodowo.viewmodel.times
import com.hodowo.viewmodel.uprightRotation
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.sqrt

private const val JPEG_QUALITY = 85
private const val REFERENCE_MAX_SIDE = 1024
private const val DEFAULT_SENSOR_ORIENTATION = 90
private const val DEGREES_PER_ROTATION_STEP = 90

/** A plane point outside the tracked polygon still counts when this close to it (meters). */
private const val PLANE_EDGE_MARGIN_M = 0.25f

/**
 * One camera frame kept for AR auto-alignment: the image (NV21, as the sensor
 * reads it), its geometry, and where the camera was when it was taken.
 */
internal class CapturedFrame(val nv21: ByteArray, val geometry: FrameGeometry, val cameraPose: Pose)

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
        return CapturedFrame(image.toNv21(), geometry, frame.camera.pose)
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
internal fun Session.surfacePointFor(frame: CapturedFrame, x: Float, y: Float): SurfacePoint? {
    val local = cameraRay(x, y, frame.geometry)
    val origin = frame.cameraPose.translation.toVec3()
    val direction = frame.cameraPose.rotateVector(floatArrayOf(local.x, local.y, local.z)).toVec3()
    val hits = getAllTrackables(Plane::class.java)
        .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
        .mapNotNull { plane -> planeHit(plane, origin, direction) }
    val best = hits.filter { it.inside }.minByOrNull { it.distance }
        ?: hits.filter { it.nearEdge }.minByOrNull { it.distance }
    return best?.let { SurfacePoint(it.plane, it.pose) }
}

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
