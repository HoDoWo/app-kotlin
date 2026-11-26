package com.hodowo.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hodowo.R
import com.hodowo.viewmodel.ArLine
import com.hodowo.viewmodel.ArText

/** The string resource of each AR sentence: English in values/, Italian in values-it/. */
@StringRes
internal fun ArText.res(): Int = when (this) {
    ArText.CAMERA_NEEDED -> R.string.ar_camera_needed
    ArText.CAMERA_OFF -> R.string.ar_camera_off
    ArText.INSTALL_NEEDED -> R.string.ar_install_needed
    ArText.NOT_COMPATIBLE -> R.string.ar_not_compatible
    ArText.CAMERA_BUSY -> R.string.ar_camera_busy
    ArText.AR_STOPPED -> R.string.ar_stopped
    ArText.TOO_DARK -> R.string.ar_too_dark
    ArText.TOO_FAST -> R.string.ar_too_fast
    ArText.LOW_DETAIL -> R.string.ar_low_detail
    ArText.CAMERA_BUSY_TRACKING -> R.string.ar_camera_busy_tracking
    ArText.TRACKING_STOPPED -> R.string.ar_tracking_stopped
    ArText.ANCHOR_LOST -> R.string.ar_anchor_lost
    ArText.POINT_AT_APPLIANCE -> R.string.ar_point_at_appliance
    ArText.SURFACE_FOUND -> R.string.ar_surface_found
    ArText.ALIGN_MISMATCH -> R.string.ar_align_mismatch
    ArText.AIM_FIRST -> R.string.ar_aim_first
    ArText.AIM_SECOND -> R.string.ar_aim_second
    ArText.AIM_SINGLE -> R.string.ar_aim_single
    ArText.ARROW_PLACED -> R.string.ar_arrow_placed
    ArText.ARROW_PLACED_GUIDE -> R.string.ar_arrow_placed_guide
    ArText.STEP_MARKED -> R.string.ar_step_marked
    ArText.STEP_NO_POINT -> R.string.ar_step_no_point
    ArText.LOOKING_FOR_CONTROLS -> R.string.ar_looking_for_controls
    ArText.LOOKING_FOR_STEPS -> R.string.ar_looking_for_steps
    ArText.AUTO_NONE -> R.string.ar_auto_none
    ArText.AUTO_ERROR -> R.string.ar_auto_error
    ArText.AUTO_FIRST -> R.string.ar_auto_first
    ArText.SCAN_IDLE -> R.string.ar_scan_idle
    ArText.SCAN_SEARCHING -> R.string.ar_scan_searching
    ArText.SCAN_NOTHING -> R.string.ar_scan_nothing
    ArText.SCAN_ERROR -> R.string.ar_scan_error
    ArText.SCAN_NO_CONTROLS -> R.string.ar_scan_no_controls
    ArText.SCAN_ALL -> R.string.ar_scan_all
    ArText.SCAN_SOME -> R.string.ar_scan_some
    ArText.PLACE_ARROW -> R.string.ar_place_arrow
    ArText.PLACE_POINT -> R.string.ar_place_point
    ArText.MOVE_ARROW -> R.string.ar_move_arrow
    ArText.REALIGN -> R.string.ar_realign
}

@Composable
internal fun ArText.text(): String = stringResource(res())

@Composable
internal fun ArLine.text(): String = stringResource(text.res(), *args.toTypedArray())

/** The status line: its sentences joined, in the app language. */
@Composable
internal fun List<ArLine>.text(): String = map { it.text() }.joinToString(" ")
