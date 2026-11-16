package com.hodowo.data

import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.StepPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every step of a guide with pins gets a number to tap on the image. */
class StepPinLayoutTest {

    private fun step(number: Int, pin: StepPin? = null) = ManualStep(number, "Step $number", "", pin)

    @Test
    fun aGuideWithoutPinsShowsNoNumbers() {
        assertEquals(emptyList<PinMarker>(), StepPinLayout.markers(listOf(step(1), step(2))))
    }

    @Test
    fun pinsStayOnTheirControl() {
        val markers = StepPinLayout.markers(listOf(step(1, StepPin(0.2f, 0.3f)), step(2, StepPin(0.7f, 0.6f))))

        assertEquals(
            listOf(PinMarker(0, 1, 0.2f, 0.3f), PinMarker(1, 2, 0.7f, 0.6f)),
            markers
        )
    }

    @Test
    fun stepsOnTheSameControlAreSetSideBySide() {
        val markers = StepPinLayout.markers(
            listOf(step(1, StepPin(0.4f, 0.5f)), step(2, StepPin(0.41f, 0.5f)), step(3, StepPin(0.4f, 0.52f)))
        )

        assertEquals(listOf(0.4f, 0.41f + StepPinLayout.SPACING, 0.4f + 2 * StepPinLayout.SPACING), markers.map { it.x })
        assertTrue(markers.all { it.placed })
    }

    @Test
    fun nearTheRightEdgeTheExtraPinGoesLeft() {
        val markers = StepPinLayout.markers(listOf(step(1, StepPin(0.97f, 0.5f)), step(2, StepPin(0.97f, 0.5f))))

        assertEquals(0.97f - StepPinLayout.SPACING, markers[1].x, 0.0001f)
    }

    @Test
    fun stepsWithoutAPinGoInARowAtTheBottom() {
        val markers = StepPinLayout.markers(
            listOf(step(1, StepPin(0.5f, 0.2f)), step(2), step(3, StepPin(0.1f, 0.1f)), step(4))
        )

        assertEquals(listOf(1, 2, 3, 4), markers.map { it.number })
        val unplaced = markers.filterNot { it.placed }
        assertEquals(listOf(1, 3), unplaced.map { it.index })
        assertEquals(listOf(StepPinLayout.SPACING, 2 * StepPinLayout.SPACING), unplaced.map { it.x })
        assertTrue(unplaced.all { it.y == StepPinLayout.UNPLACED_ROW_Y })
        assertFalse(markers[0].x == markers[1].x && markers[0].y == markers[1].y)
    }
}
