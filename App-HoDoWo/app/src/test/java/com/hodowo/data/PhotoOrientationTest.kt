package com.hodowo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** EXIF orientation → turn applied before upload (portrait photos stay portrait). */
class PhotoOrientationTest {

    @Test
    fun everyExifOrientation() {
        val expected = mapOf(
            1 to PhotoOrientation(0, false),
            2 to PhotoOrientation(0, true),
            3 to PhotoOrientation(180, false),
            4 to PhotoOrientation(180, true),
            5 to PhotoOrientation(90, true),
            6 to PhotoOrientation(90, false),
            7 to PhotoOrientation(270, true),
            8 to PhotoOrientation(270, false)
        )
        expected.forEach { (exif, orientation) -> assertEquals(orientation, PhotoOrientation.fromExif(exif)) }
    }

    @Test
    fun aPortraitPhonePhotoIsTurnedAQuarterClockwise() {
        // What CameraX writes for a phone held upright: ORIENTATION_ROTATE_90.
        assertEquals(90, PhotoOrientation.fromExif(6).degrees)
    }

    @Test
    fun unknownOrMissingValuesLeaveThePhotoAsItIs() {
        assertTrue(PhotoOrientation.fromExif(0).isIdentity)
        assertTrue(PhotoOrientation.fromExif(99).isIdentity)
        assertTrue(PhotoOrientation.fromExif(1).isIdentity)
    }
}
