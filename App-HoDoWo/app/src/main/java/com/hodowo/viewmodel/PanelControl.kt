package com.hodowo.viewmodel

/**
 * Kind of physical control detected on an appliance panel
 * (matches the `type` values returned by the backend, S1-14).
 */
enum class ControlType {
    BUTTON,
    DIAL,
    SWITCH,
    DISPLAY,
    OTHER;

    companion object {
        /** Maps the backend value ("button", "dial"...) to the enum. Unknown values become [OTHER]. */
        fun fromApi(value: String?): ControlType = when (value?.trim()?.lowercase()) {
            "button" -> BUTTON
            "dial" -> DIAL
            "switch" -> SWITCH
            "display" -> DISPLAY
            else -> OTHER
        }
    }
}

/**
 * Bounding box of a control, normalised to the panel image.
 *
 * All values are between 0 and 1, so the overlay (S1-15) can draw them at
 * any resolution: [x], [y] is the top-left corner and [w], [h] the size.
 */
data class ControlBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

/**
 * A single control detected on the control panel.
 *
 * [id] is stable across calls (`c1`, `c2`, ...), so the overlay can rely on it.
 */
data class PanelControl(
    val id: String,
    val label: String,
    val type: ControlType,
    val description: String,
    val box: ControlBox
)
