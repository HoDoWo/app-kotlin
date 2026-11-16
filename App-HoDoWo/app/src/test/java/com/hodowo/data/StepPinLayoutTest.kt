package com.hodowo.data

import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.StepPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every step of a guide with pins gets a number on the image, joined to its control. */
class StepPinLayoutTest {

    private val delta = 0.0001f

    private fun step(number: Int, pin: StepPin? = null) = ManualStep(number, "Step $number", "", pin)

    @Test
    fun aGuideWithoutPinsShowsNoNumbers() {
        assertEquals(emptyList<PinMarker>(), StepPinLayout.markers(listOf(step(1), step(2))))
    }

    @Test
    fun theNumberSitsOutsideItsControlAndPointsAtIt() {
        val marker = StepPinLayout.markers(listOf(step(1, StepPin(0.2f, 0.5f)))).single()

        assertTrue(marker.placed)
        assertEquals(0.2f, marker.anchorX ?: -1f, delta)
        assertEquals(0.5f, marker.anchorY ?: -1f, delta)
        // Left of the centre: the number goes further left.
        assertEquals(0.2f - StepPinLayout.CALLOUT_DISTANCE, marker.x, delta)
        assertEquals(0.5f, marker.y, delta)
    }

    @Test
    fun aControlInTheMiddleHasItsNumberAbove() {
        val marker = StepPinLayout.markers(listOf(step(1, StepPin(0.5f, 0.5f)))).single()

        assertEquals(0.5f, marker.x, delta)
        assertEquals(0.5f - StepPinLayout.CALLOUT_DISTANCE, marker.y, delta)
    }

    @Test
    fun onAWideImageTheLineKeepsItsLengthUpwards() {
        val marker = StepPinLayout.markers(listOf(step(1, StepPin(0.5f, 0.4f))), ratio = 2f).single()

        assertEquals(0.4f - 2 * StepPinLayout.CALLOUT_DISTANCE, marker.y, delta)
    }

    @Test
    fun stepsOnTheSameControlFanOutAroundIt() {
        val markers = StepPinLayout.markers(
            listOf(step(1, StepPin(0.3f, 0.5f)), step(2, StepPin(0.31f, 0.5f)), step(3, StepPin(0.3f, 0.52f)))
        )

        assertEquals(3, markers.map { it.x to it.y }.distinct().size)
        assertTrue(markers.all { it.placed })
        // The second turns above the first and the third below it.
        assertTrue(markers[1].y < markers[0].y)
        assertTrue(markers[2].y > markers[0].y)
    }

    @Test
    fun numbersStayInsideTheImage() {
        val marker = StepPinLayout.markers(listOf(step(1, StepPin(0.98f, 0.5f)))).single()

        assertEquals(1f - StepPinLayout.MARGIN, marker.x, delta)
        assertEquals(0.98f, marker.anchorX ?: -1f, delta)
    }

    @Test
    fun aStepWithoutAPinPointsWhereTheStepBeforeItDoes() {
        val markers = StepPinLayout.markers(
            listOf(step(1), step(2, StepPin(0.3f, 0.5f)), step(3), step(4, StepPin(0.7f, 0.2f)), step(5))
        )

        assertEquals(listOf(1, 2, 3, 4, 5), markers.map { it.number })
        assertTrue(markers.all { it.placed })
        // The first step has nothing before it: it takes the first pin.
        assertEquals(listOf(0.3f, 0.3f, 0.3f, 0.7f, 0.7f), markers.map { it.anchorX ?: -1f })
        assertEquals(listOf(0.5f, 0.5f, 0.5f, 0.2f, 0.2f), markers.map { it.anchorY ?: -1f })
        // Numbers on the same control do not cover each other.
        assertEquals(5, markers.map { it.x to it.y }.distinct().size)
    }
}
