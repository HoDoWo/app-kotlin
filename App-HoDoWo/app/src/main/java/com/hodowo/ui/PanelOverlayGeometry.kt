package com.hodowo.ui

import com.hodowo.viewmodel.ControlBox
import com.hodowo.viewmodel.PanelControl

/**
 * Where the panel photo is drawn inside its container (S1-15).
 *
 * The photo keeps its aspect ratio (like `ContentScale.Fit`), so it is centred
 * and may leave empty bands on the sides or above and below. All values are
 * pixels relative to the container, with the origin at its top-left corner.
 */
data class ImagePlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** A rectangle in container pixels (origin at the top-left corner). */
data class ScreenRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {

    /** True when the point ([x], [y]) falls inside this rectangle (borders included). */
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    /** Area in square pixels; used to prefer the smallest control when they overlap. */
    val area: Float
        get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
}

/**
 * Computes where an image of [imageWidth] x [imageHeight] pixels is drawn inside
 * a container of [containerWidth] x [containerHeight] pixels with `ContentScale.Fit`.
 *
 * The image is scaled uniformly (never distorted) and centred. A degenerate size
 * (zero or negative) gives an empty placement at the origin instead of dividing
 * by zero.
 */
fun fitImagePlacement(
    imageWidth: Int,
    imageHeight: Int,
    containerWidth: Float,
    containerHeight: Float
): ImagePlacement {

    if (imageWidth <= 0 || imageHeight <= 0 ||
        containerWidth <= 0f || containerHeight <= 0f
    ) {
        return ImagePlacement(0f, 0f, 0f, 0f)
    }

    // The photo fits when its largest dimension matches the container.
    val scale = minOf(
        containerWidth / imageWidth,
        containerHeight / imageHeight
    )

    val width = imageWidth * scale
    val height = imageHeight * scale

    return ImagePlacement(
        left = (containerWidth - width) / 2f,
        top = (containerHeight - height) / 2f,
        width = width,
        height = height
    )
}

/**
 * Maps a normalised [box] (0..1, relative to the photo) to a rectangle in
 * container pixels, using the [placement] of the photo.
 */
fun controlScreenRect(box: ControlBox, placement: ImagePlacement): ScreenRect {

    val left = placement.left + box.x * placement.width
    val top = placement.top + box.y * placement.height

    return ScreenRect(
        left = left,
        top = top,
        right = left + box.w * placement.width,
        bottom = top + box.h * placement.height
    )
}

/**
 * Returns the control whose rectangle contains the point ([x], [y]), or null
 * when the user tapped outside every control.
 *
 * When several controls overlap, the smallest one wins (the most specific), so
 * a small button on top of a big display is still selectable.
 */
fun findControlAt(
    x: Float,
    y: Float,
    controls: List<PanelControl>,
    placement: ImagePlacement
): PanelControl? = controls
    .map { control -> control to controlScreenRect(control.box, placement) }
    .filter { (_, rect) -> rect.contains(x, y) }
    .minByOrNull { (_, rect) -> rect.area }
    ?.first
