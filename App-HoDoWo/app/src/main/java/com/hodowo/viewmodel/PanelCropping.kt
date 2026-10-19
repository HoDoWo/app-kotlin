package com.hodowo.viewmodel

import android.net.Uri
import kotlin.math.roundToInt

/**
 * Rectangle of a crop, in pixels, with the origin at the top-left of the image.
 *
 * A plain data class (instead of [android.graphics.Rect]) so the crop maths
 * can be unit-tested without an Android device.
 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Translates a normalised panel [box] (0..1) into pixels for an image of
 * [imageWidth] x [imageHeight].
 *
 * The result is always inside the image and at least one pixel wide and tall,
 * so a slightly out-of-range box returned by the model can never produce an
 * empty crop.
 */
fun cropRectOf(box: PanelBox, imageWidth: Int, imageHeight: Int): PixelRect {

    if (imageWidth <= 0 || imageHeight <= 0) {
        return PixelRect(0, 0, 0, 0)
    }

    val left = (box.x * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
    val top = (box.y * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)

    val right = ((box.x + box.w) * imageWidth)
        .roundToInt()
        .coerceIn(left + 1, imageWidth)

    val bottom = ((box.y + box.h) * imageHeight)
        .roundToInt()
        .coerceIn(top + 1, imageHeight)

    return PixelRect(left = left, top = top, right = right, bottom = bottom)
}

/**
 * Cuts just the control panel out of a photo (S1-16).
 *
 * Implementations return the [Uri] of the cropped image, or null when they
 * cannot crop; the caller then keeps the original photo.
 */
fun interface PanelCropper {

    suspend fun crop(uri: Uri, box: PanelBox): Uri?

    companion object {

        /**
         * Keeps the original photo. Used when there is nothing to crop with
         * (for example the demo service, which finds no panel).
         */
        val None = PanelCropper { _, _ -> null }
    }
}
