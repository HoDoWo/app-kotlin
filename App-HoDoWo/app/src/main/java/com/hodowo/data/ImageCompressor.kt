package com.hodowo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a camera photo before it is uploaded.
 *
 * Listing photos are stored in Firestore, where one document cannot exceed
 * 1 MiB, so the longest side is reduced to [MAX_SIDE] pixels and the image is
 * saved as JPEG at [JPEG_QUALITY]. That keeps a photo around 100-300 KB,
 * which is also well under Vercel's request-size limit.
 *
 * The EXIF orientation of the camera is applied to the pixels, because the
 * JPEG written here has no EXIF: a portrait photo stays portrait.
 */
class ImageCompressor(private val context: Context) {

    suspend fun compress(
        uri: Uri,
        maxSide: Int = MAX_SIDE,
        quality: Int = JPEG_QUALITY
    ): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. Read only the image size, without loading the pixels.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        // 2. Decode a smaller copy (power-of-two sampling saves memory).
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) {
            sampleSize *= 2
        }

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        } ?: throw IOException("Could not read the photo")

        // 3. Turn it the way it was taken, scale to the exact maximum size and encode as JPEG.
        val orientation = readOrientation(uri)
        val scaled = upright(scaleDown(decoded, maxSide), orientation)

        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
    }

    /** The EXIF orientation of the photo; a photo without EXIF is left as it is. */
    private fun readOrientation(uri: Uri): PhotoOrientation {
        val value = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: IOException) {
            null
        }
        return PhotoOrientation.fromExif(value ?: ExifInterface.ORIENTATION_NORMAL)
    }

    private fun upright(bitmap: Bitmap, orientation: PhotoOrientation): Bitmap {
        if (orientation.isIdentity) return bitmap
        val matrix = Matrix().apply {
            postRotate(orientation.degrees.toFloat())
            if (orientation.mirror) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)

        if (longestSide <= maxSide) {
            return bitmap
        }

        val ratio = maxSide.toFloat() / longestSide

        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt(),
            (bitmap.height * ratio).roundToInt(),
            true
        )
    }

    companion object {
        const val MAX_SIDE = 1280
        const val JPEG_QUALITY = 75
    }
}
