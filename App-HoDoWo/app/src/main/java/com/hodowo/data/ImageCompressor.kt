package com.hodowo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

        // 3. Scale to the exact maximum size and encode as JPEG.
        val scaled = scaleDown(decoded, maxSide)

        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
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
