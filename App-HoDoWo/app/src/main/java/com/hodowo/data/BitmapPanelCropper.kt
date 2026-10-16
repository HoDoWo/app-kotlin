package com.hodowo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hodowo.viewmodel.PanelBox
import com.hodowo.viewmodel.PanelCropper
import com.hodowo.viewmodel.cropRectOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Cuts the control panel out of a photo with [Bitmap] (S1-16) and stores it as
 * a JPEG in the app cache.
 *
 * The overlay then shows that same file, so the boxes detected by the backend
 * always line up with the image on screen.
 */
class BitmapPanelCropper(private val context: Context) : PanelCropper {

    override suspend fun crop(uri: Uri, box: PanelBox): Uri? = withContext(Dispatchers.IO) {

        val source = decode(uri) ?: return@withContext null

        val rect = cropRectOf(box, source.width, source.height)

        val cropped = runCatching {
            Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height)
        }.getOrNull() ?: return@withContext null

        val file = newCropFile()

        runCatching {
            file.outputStream().use { output ->
                cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
        }.getOrElse { return@withContext null }

        Uri.fromFile(file)
    }

    /**
     * Decodes a smaller copy of the photo: a full-size camera picture can be
     * several megapixels and would not fit comfortably in memory.
     */
    private fun decode(uri: Uri): Bitmap? {

        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE) {
            sampleSize *= 2
        }

        return runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            }
        }.getOrNull()
    }

    /**
     * A fresh file per crop: the overlay reloads the photo only when its Uri
     * changes. The previous crops are dropped so the cache does not grow.
     */
    private fun newCropFile(): File {
        context.cacheDir
            .listFiles { file -> file.name.startsWith(CROP_PREFIX) }
            ?.forEach { it.delete() }

        return File(context.cacheDir, "$CROP_PREFIX${System.currentTimeMillis()}.jpg")
    }

    private companion object {
        const val CROP_PREFIX = "panel_crop_"
        const val JPEG_QUALITY = 85
        const val MAX_SIDE = 1600
    }
}
