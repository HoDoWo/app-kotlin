package com.hodowo.viewmodel

/**
 * Every sentence and label the AR screen shows, as a key: the UI turns it into
 * the user's language with string resources (`ar_*` in strings.xml), so the
 * ViewModel stays plain Kotlin and the AR screen follows the app language.
 */
enum class ArText {
    // Permission and session
    CAMERA_NEEDED,
    CAMERA_OFF,
    INSTALL_NEEDED,
    NOT_COMPATIBLE,
    CAMERA_BUSY,
    AR_STOPPED,

    // Tracking
    TOO_DARK,
    TOO_FAST,
    LOW_DETAIL,
    CAMERA_BUSY_TRACKING,
    TRACKING_STOPPED,
    ANCHOR_LOST,

    // Placing the arrow / the guide points
    POINT_AT_APPLIANCE,
    SURFACE_FOUND,
    ALIGN_MISMATCH,
    AIM_FIRST,
    AIM_SECOND,
    AIM_SINGLE,
    ARROW_PLACED,
    ARROW_PLACED_GUIDE,
    STEP_MARKED,
    STEP_NO_POINT,

    // Automatic alignment
    LOOKING_FOR_CONTROLS,
    LOOKING_FOR_STEPS,
    AUTO_NONE,
    AUTO_ERROR,
    AUTO_FIRST,

    // AR tab scan
    SCAN_IDLE,
    SCAN_SEARCHING,
    SCAN_NOTHING,
    SCAN_ERROR,
    SCAN_NO_CONTROLS,
    SCAN_ALL,
    SCAN_SOME,

    // Button labels that depend on the state
    PLACE_ARROW,
    PLACE_POINT,
    MOVE_ARROW,
    REALIGN
}

/** One sentence: its key and the values it needs (step numbers, titles, device name, counts). */
data class ArLine(val text: ArText, val args: List<Any> = emptyList())
