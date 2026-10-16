package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the panel crop maths (S1-16).
 *
 * [cropRectOf] is pure, so it is checked here without an Android device.
 */
class PanelCroppingTest {

    @Test
    fun aFullBoxCoversTheWholeImage() {
        val rect = cropRectOf(PanelBox(0f, 0f, 1f, 1f), imageWidth = 1000, imageHeight = 800)

        assertEquals(PixelRect(0, 0, 1000, 800), rect)
    }

    @Test
    fun aNormalisedBoxIsTranslatedToPixels() {
        val rect = cropRectOf(
            PanelBox(0.1f, 0.2f, 0.5f, 0.25f),
            imageWidth = 1000,
            imageHeight = 800
        )

        assertEquals(PixelRect(100, 160, 600, 360), rect)
        assertEquals(500, rect.width)
        assertEquals(200, rect.height)
    }

    @Test
    fun aBoxThatSticksOutIsClampedToTheImage() {
        val rect = cropRectOf(
            PanelBox(0.8f, 0.9f, 0.5f, 0.5f),
            imageWidth = 1000,
            imageHeight = 800
        )

        assertEquals(PixelRect(800, 720, 1000, 800), rect)
    }

    @Test
    fun aBoxWithNegativeCoordinatesStillProducesAPixel() {
        val rect = cropRectOf(
            PanelBox(-0.5f, -0.5f, 0.4f, 0.4f),
            imageWidth = 1000,
            imageHeight = 800
        )

        assertEquals(PixelRect(0, 0, 1, 1), rect)
        assertEquals(1, rect.width)
        assertEquals(1, rect.height)
    }

    @Test
    fun aTinyBoxIsAtLeastOnePixel() {
        val rect = cropRectOf(
            PanelBox(0.5f, 0.5f, 0.0001f, 0.0001f),
            imageWidth = 1000,
            imageHeight = 800
        )

        assertEquals(1, rect.width)
        assertEquals(1, rect.height)
    }

    @Test
    fun anImageWithoutSizeGivesAnEmptyRect() {
        val box = PanelBox(0.1f, 0.1f, 0.5f, 0.5f)

        assertEquals(PixelRect(0, 0, 0, 0), cropRectOf(box, imageWidth = 0, imageHeight = 800))
        assertEquals(PixelRect(0, 0, 0, 0), cropRectOf(box, imageWidth = 1000, imageHeight = 0))
    }
}
