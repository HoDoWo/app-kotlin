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
 * Vercel rejects requests larger than about 4.5 MB, while a full-size camera
 * photo can be bigger. The longest side is reduced to [MAX_SIDE] pixels and
 * the image is saved as JPEG, which keeps it well under the limit.
 */
class ImageCompressor(private val context: Context) {

    suspend fun compress(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. Read only the image size, without loading the pixels.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        // 2. Decode a smaller copy (power-of-two sampling saves memory).
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE) {
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
        val scaled = scaleDown(decoded)

        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longestSide = max(bitmap.width, bitmap.height)

        if (longestSide <= MAX_SIDE) {
            return bitmap
        }

        val ratio = MAX_SIDE.toFloat() / longestSide

        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt(),
            (bitmap.height * ratio).roundToInt(),
            true
        )
    }

    private companion object {
        const val MAX_SIDE = 1600
        const val JPEG_QUALITY = 80
    }
}