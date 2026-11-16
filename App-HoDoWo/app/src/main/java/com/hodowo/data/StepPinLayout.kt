package com.hodowo.data

import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.StepPin
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A numbered pin to draw on the guide image: [index] is the step's position in
 * the guide. [x] and [y] (0 to 1 over the image) are the centre of the number;
 * [anchorX] and [anchorY] are the exact point of the control, where a line from
 * the number ends, like the arrows of the annotated images. A step whose control
 * is not on the photo (a safety note, for example) has no anchor: its number
 * appears in a row at the bottom of the image.
 */
data class PinMarker(
    val index: Int,
    val number: Int,
    val x: Float,
    val y: Float,
    val anchorX: Float? = null,
    val anchorY: Float? = null
) {
    val placed: Boolean get() = anchorX != null && anchorY != null
}

/**
 * Where the numbers of a guide go on its image, so every step can be tapped.
 *
 * Each number sits a little away from its control, towards the outside of the
 * image, joined to it by a line, so it does not cover the control. Steps that
 * use the same control fan out around it. Steps without a pin are shown in a
 * row at the bottom left. A guide with no pin at all shows none: its image
 * already has the numbers drawn in it.
 */
object StepPinLayout {

    /** Two pins closer than this (on both axes) are on the same control. */
    const val OVERLAP = 0.06f

    /** Distance between a number and its control, as a fraction of the image width. */
    const val CALLOUT_DISTANCE = 0.13f

    /** Angle between the numbers of steps that share a control. */
    const val FAN_DEGREES = 40.0

    /** Numbers stay this far from the edges of the image. */
    const val MARGIN = 0.06f

    /** Distance between two numbers of the row of steps without a pin. */
    const val SPACING = 0.08f

    /** Height of the row of steps without a pin. */
    const val UNPLACED_ROW_Y = 0.9f

    private const val CENTRE = 0.5f

    /** A control this close to the centre of the image gets its number above it. */
    private const val CENTRE_RADIUS = 0.05f

    /**
     * The numbers of [steps] on an image [ratio] times as wide as it is tall
     * (the ratio keeps the line the same length in every direction).
     */
    fun markers(steps: List<ManualStep>, ratio: Float = 1f): List<PinMarker> {
        if (steps.none { it.pin != null }) return emptyList()
        val markers = mutableListOf<PinMarker>()
        val spots = mutableListOf<StepPin>()
        steps.forEachIndexed { index, step ->
            val pin = step.pin ?: return@forEachIndexed
            val sameSpot = spots.count { abs(it.x - pin.x) < OVERLAP && abs(it.y - pin.y) < OVERLAP }
            spots += pin
            val (x, y) = calloutPosition(pin, sameSpot, ratio)
            markers += PinMarker(index, step.number, x, y, pin.x, pin.y)
        }
        val unplaced = steps.withIndex().filter { it.value.pin == null }
        unplaced.forEachIndexed { slot, (index, step) ->
            markers += PinMarker(index, step.number, SPACING * (slot + 1), UNPLACED_ROW_Y)
        }
        return markers.sortedBy { it.index }
    }

    /**
     * The centre of the [nth] number of a control: away from the centre of the
     * image (straight up for a control in the middle), the next ones turned
     * alternately to one side and the other.
     */
    private fun calloutPosition(pin: StepPin, nth: Int, ratio: Float): Pair<Float, Float> {
        val safeRatio = if (ratio > 0f) ratio else 1f
        // In width units, so that one step right and one step down are the same distance.
        val dx = pin.x - CENTRE
        val dy = (pin.y - CENTRE) / safeRatio
        val nearCentre = sqrt(dx * dx + dy * dy) < CENTRE_RADIUS
        val outward = if (nearCentre) -Math.PI / 2 else atan2(dy.toDouble(), dx.toDouble())
        val turn = ((nth + 1) / 2) * (if (nth % 2 == 1) 1 else -1) * Math.toRadians(FAN_DEGREES)
        val angle = outward + turn
        val x = pin.x + CALLOUT_DISTANCE * cos(angle).toFloat()
        val y = pin.y + CALLOUT_DISTANCE * sin(angle).toFloat() * safeRatio
        return x.coerceIn(MARGIN, 1f - MARGIN) to y.coerceIn(MARGIN, 1f - MARGIN)
    }
}
