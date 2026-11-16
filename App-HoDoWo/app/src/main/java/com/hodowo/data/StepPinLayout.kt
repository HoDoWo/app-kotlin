package com.hodowo.data

import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.StepPin
import kotlin.math.abs

/**
 * A numbered pin to draw on the guide image: [index] is the step's position in
 * the guide, [x] and [y] go from 0 to 1 over the image. [placed] is false for a
 * step whose control is not on the photo (a safety note, for example): its
 * number still appears, in a row at the bottom of the image.
 */
data class PinMarker(
    val index: Int,
    val number: Int,
    val x: Float,
    val y: Float,
    val placed: Boolean = true
)

/**
 * Where the numbers of a guide go on its image, so every step can be tapped.
 *
 * Steps that use the same control get pins on the same point: they are set side
 * by side instead of one hiding the other. Steps without a pin are shown in a
 * row at the bottom left. A guide with no pin at all shows none: its image
 * already has the numbers drawn in it.
 */
object StepPinLayout {

    /** Two pins closer than this (on both axes) would cover each other. */
    const val OVERLAP = 0.06f

    /** Distance between the centres of two pins set side by side. */
    const val SPACING = 0.08f

    /** Height of the row of steps without a pin. */
    const val UNPLACED_ROW_Y = 0.9f

    fun markers(steps: List<ManualStep>): List<PinMarker> {
        if (steps.none { it.pin != null }) return emptyList()
        val markers = mutableListOf<PinMarker>()
        val spots = mutableListOf<StepPin>()
        steps.forEachIndexed { index, step ->
            val pin = step.pin ?: return@forEachIndexed
            // Compared with where the earlier pins were asked, not where they were moved.
            val sameSpot = spots.count { abs(it.x - pin.x) < OVERLAP && abs(it.y - pin.y) < OVERLAP }
            spots += pin
            markers += PinMarker(index, step.number, sideBySide(pin.x, sameSpot), pin.y)
        }
        val unplaced = steps.withIndex().filter { it.value.pin == null }
        unplaced.forEachIndexed { slot, (index, step) ->
            markers += PinMarker(index, step.number, SPACING * (slot + 1), UNPLACED_ROW_Y, placed = false)
        }
        return markers.sortedBy { it.index }
    }

    /** The [nth] extra pin on a spot goes to the right, or to the left near the right edge. */
    private fun sideBySide(x: Float, nth: Int): Float {
        if (nth == 0) return x
        val right = x + SPACING * nth
        return if (right <= 1f - SPACING / 2) right else (x - SPACING * nth).coerceAtLeast(SPACING / 2)
    }
}
