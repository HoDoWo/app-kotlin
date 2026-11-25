package com.hodowo.data

/**
 * How to turn a decoded photo so it looks the way it was taken. Cameras save
 * the pixels as the sensor reads them and only *tag* the turn in EXIF;
 * BitmapFactory ignores that tag, so without this a portrait photo is
 * uploaded lying on its side (and the AI pins and reads it sideways).
 *
 * [degrees]: clockwise turn; [mirror]: then flip left-right.
 */
data class PhotoOrientation(val degrees: Int, val mirror: Boolean) {
    val isIdentity: Boolean get() = degrees == 0 && !mirror

    companion object {
        // EXIF orientation values (TIFF tag 0x0112).
        private const val NORMAL = 1
        private const val FLIP_HORIZONTAL = 2
        private const val ROTATE_180 = 3
        private const val FLIP_VERTICAL = 4
        private const val TRANSPOSE = 5
        private const val ROTATE_90 = 6
        private const val TRANSVERSE = 7
        private const val ROTATE_270 = 8

        private const val QUARTER = 90
        private const val HALF = 180
        private const val THREE_QUARTERS = 270

        /** From the EXIF orientation value; unknown values leave the photo as it is. */
        fun fromExif(orientation: Int): PhotoOrientation = when (orientation) {
            FLIP_HORIZONTAL -> PhotoOrientation(0, mirror = true)
            ROTATE_180 -> PhotoOrientation(HALF, mirror = false)
            FLIP_VERTICAL -> PhotoOrientation(HALF, mirror = true)
            TRANSPOSE -> PhotoOrientation(QUARTER, mirror = true)
            ROTATE_90 -> PhotoOrientation(QUARTER, mirror = false)
            TRANSVERSE -> PhotoOrientation(THREE_QUARTERS, mirror = true)
            ROTATE_270 -> PhotoOrientation(THREE_QUARTERS, mirror = false)
            NORMAL -> PhotoOrientation(0, mirror = false)
            else -> PhotoOrientation(0, mirror = false)
        }
    }
}
