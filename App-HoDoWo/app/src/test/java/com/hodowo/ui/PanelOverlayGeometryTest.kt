package com.hodowo.ui

import com.hodowo.viewmodel.ControlBox
import com.hodowo.viewmodel.ControlType
import com.hodowo.viewmodel.PanelControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the maths behind the interactive overlay (S1-15):
 * fitting the photo and mapping the normalised control boxes onto it.
 */
class PanelOverlayGeometryTest {

    private val delta = 0.001f

    // ---------- fitImagePlacement ----------

    @Test
    fun widePhotoIsLetterboxedVertically() {
        // 200x100 photo inside a 100x100 container: full width, centred vertically.
        val placement = fitImagePlacement(200, 100, 100f, 100f)

        assertEquals(0f, placement.left, delta)
        assertEquals(25f, placement.top, delta)
        assertEquals(100f, placement.width, delta)
        assertEquals(50f, placement.height, delta)
    }

    @Test
    fun tallPhotoIsLetterboxedHorizontally() {
        // 100x200 photo inside a 100x100 container: full height, centred horizontally.
        val placement = fitImagePlacement(100, 200, 100f, 100f)

        assertEquals(25f, placement.left, delta)
        assertEquals(0f, placement.top, delta)
        assertEquals(50f, placement.width, delta)
        assertEquals(100f, placement.height, delta)
    }

    @Test
    fun squarePhotoInAWideContainerIsCentred() {
        val placement = fitImagePlacement(100, 100, 200f, 100f)

        assertEquals(50f, placement.left, delta)
        assertEquals(0f, placement.top, delta)
        assertEquals(100f, placement.width, delta)
        assertEquals(100f, placement.height, delta)
    }

    @Test
    fun photoIsScaledUpToFillTheContainer() {
        // 50x50 photo inside 200x100: it grows to the height limit.
        val placement = fitImagePlacement(50, 50, 200f, 100f)

        assertEquals(50f, placement.left, delta)
        assertEquals(100f, placement.width, delta)
        assertEquals(100f, placement.height, delta)
    }

    @Test
    fun degenerateSizesGiveAnEmptyPlacement() {
        listOf(
            fitImagePlacement(0, 100, 100f, 100f),
            fitImagePlacement(100, 0, 100f, 100f),
            fitImagePlacement(100, 100, 0f, 100f),
            fitImagePlacement(100, 100, 100f, 0f)
        ).forEach { placement ->
            assertEquals(0f, placement.left, delta)
            assertEquals(0f, placement.top, delta)
            assertEquals(0f, placement.width, delta)
            assertEquals(0f, placement.height, delta)
        }
    }

    // ---------- controlScreenRect ----------

    @Test
    fun normalisedBoxIsMappedOntoThePhoto() {
        // Photo occupies x 0..100, y 25..75.
        val placement = ImagePlacement(left = 0f, top = 25f, width = 100f, height = 50f)

        // Bottom-right quarter of the photo.
        val rect = controlScreenRect(
            ControlBox(x = 0.5f, y = 0.5f, w = 0.5f, h = 0.5f),
            placement
        )

        assertEquals(50f, rect.left, delta)
        assertEquals(50f, rect.top, delta)
        assertEquals(100f, rect.right, delta)
        assertEquals(75f, rect.bottom, delta)
    }

    @Test
    fun boxIsOffsetByTheLetterbox() {
        // Photo occupies x 25..75, y 0..100 (tall photo in a square container).
        val placement = ImagePlacement(left = 25f, top = 0f, width = 50f, height = 100f)

        // Full-photo box: it must cover exactly the photo, not the whole container.
        val rect = controlScreenRect(
            ControlBox(x = 0f, y = 0f, w = 1f, h = 1f),
            placement
        )

        assertEquals(25f, rect.left, delta)
        assertEquals(0f, rect.top, delta)
        assertEquals(75f, rect.right, delta)
        assertEquals(100f, rect.bottom, delta)
    }

    // ---------- findControlAt ----------

    @Test
    fun tapInsideABoxSelectsThatControl() {
        val placement = ImagePlacement(left = 0f, top = 0f, width = 100f, height = 100f)
        val button = control("c1", 0.2f, 0.2f, 0.2f, 0.2f)

        assertEquals(button, findControlAt(30f, 30f, listOf(button), placement))
    }

    @Test
    fun tapOutsideEveryBoxSelectsNothing() {
        val placement = ImagePlacement(left = 0f, top = 0f, width = 100f, height = 100f)
        val button = control("c1", 0.2f, 0.2f, 0.2f, 0.2f)

        assertNull(findControlAt(90f, 90f, listOf(button), placement))
    }

    @Test
    fun theSmallestControlWinsWhenTheyOverlap() {
        val placement = ImagePlacement(left = 0f, top = 0f, width = 100f, height = 100f)

        // A big display with a small button on top of it.
        val display = control("c1", 0f, 0f, 1f, 1f, ControlType.DISPLAY)
        val button = control("c2", 0.4f, 0.4f, 0.1f, 0.1f, ControlType.BUTTON)

        val hit = findControlAt(45f, 45f, listOf(display, button), placement)

        assertEquals(button, hit)
    }

    @Test
    fun aTapOnTheBigControlOutsideTheSmallOneStillSelectsTheBigOne() {
        val placement = ImagePlacement(left = 0f, top = 0f, width = 100f, height = 100f)

        val display = control("c1", 0f, 0f, 1f, 1f, ControlType.DISPLAY)
        val button = control("c2", 0.4f, 0.4f, 0.1f, 0.1f, ControlType.BUTTON)

        val hit = findControlAt(10f, 10f, listOf(display, button), placement)

        assertEquals(display, hit)
    }

    @Test
    fun emptyControlListNeverMatches() {
        val placement = ImagePlacement(left = 0f, top = 0f, width = 100f, height = 100f)

        assertNull(findControlAt(10f, 10f, emptyList(), placement))
    }

    /** Builds a control whose box is given in normalised coordinates. */
    private fun control(
        id: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        type: ControlType = ControlType.BUTTON
    ) = PanelControl(
        id = id,
        label = id,
        type = type,
        description = "",
        box = ControlBox(x, y, w, h)
    )
}
