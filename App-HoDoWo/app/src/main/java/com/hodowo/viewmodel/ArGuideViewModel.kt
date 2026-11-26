package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Camera permission, as far as the AR screen knows it (#240). */
enum class ArCameraPermission {
    /** Not asked yet in this screen. */
    UNKNOWN,
    GRANTED,

    /** Denied once: the system dialog can be shown again. */
    DENIED,

    /** Denied with "Don't ask again": only the app settings can grant it. */
    PERMANENTLY_DENIED
}

/** Why the AR session could not start or stopped (#240). */
enum class ArSessionProblem {
    /** The user declined to install or update Google Play Services for AR. */
    INSTALL_DECLINED,

    /** The phone, or this version of Android or of the app, cannot run ARCore. */
    NOT_COMPATIBLE,

    /** Another app is using the camera, or the camera failed. */
    CAMERA_UNAVAILABLE,

    /** Any other ARCore error. */
    OTHER
}

/** Why ARCore lost tracking, mirroring `TrackingFailureReason` (#240). */
enum class ArTrackingIssue {
    NONE,
    INSUFFICIENT_LIGHT,
    EXCESSIVE_MOTION,
    INSUFFICIENT_FEATURES,
    CAMERA_UNAVAILABLE,
    BAD_STATE
}

/** What the AR screen is doing now; it decides the message and the actions shown. */
enum class ArGuidePhase {
    /** Waiting for the system camera dialog. */
    ASKING_PERMISSION,

    /** Camera denied: explain why it is needed and how to grant it. */
    PERMISSION_DENIED,

    /** The session failed: explain and offer a retry or the 2D guide. */
    SESSION_ERROR,

    /** Camera running, no surface under the reticle yet. */
    SCANNING,

    /** A surface is under the reticle: the user can place the arrow. */
    READY_TO_PLACE,

    /** The arrow is placed and tracked. */
    PLACED,

    /** Tracking lost (camera or arrow): explain how to recover. */
    TRACKING_LOST
}

/** One control of the AR tab scan, as the panel lists it; [placed]: its label is on the device. */
data class ScanLabel(val label: String, val description: String, val placed: Boolean)

/** Where the AR tab scan is: name the device and its main controls (#18). */
enum class ScanStatus { IDLE, SEARCHING, DONE, NOTHING, ERROR }

data class ArGuideUiState(
    val permission: ArCameraPermission = ArCameraPermission.UNKNOWN,
    val sessionProblem: ArSessionProblem? = null,
    val cameraTracking: Boolean = false,
    val trackingIssue: ArTrackingIssue = ArTrackingIssue.NONE,
    val surfaceUnderReticle: Boolean = false,
    val placed: Boolean = false,
    val anchorTracking: Boolean = true,
    /** Changes on every session reset, so the AR view is rebuilt from scratch. */
    val sessionKey: Int = 0,
    /** The repair guide shown in AR (#241); empty when AR is opened without a guide. */
    val steps: List<ManualStep> = emptyList(),
    /** The step shown, as a position in [steps] (numbers can repeat). */
    val stepIndex: Int = 0,
    /** Height over width of the guide photo, for the pin positions. */
    val photoAspect: Float = DEFAULT_PHOTO_ASPECT,
    /** Both alignment points are placed: every pin is on the appliance. */
    val aligned: Boolean = false,
    /** The last second point did not match the photo. */
    val alignFailed: Boolean = false,
    /** The backend can find the controls in a camera frame (AR auto-alignment). */
    val autoAlignAvailable: Boolean = false,
    /** The AI is looking for the two controls. */
    val searching: Boolean = false,
    /** How the last AI search ended; cleared when the user places a point. */
    val autoResult: AutoAlignResult? = null,
    /** The backend can name what the camera sees (AR tab scan). */
    val scanAvailable: Boolean = false,
    val scanStatus: ScanStatus = ScanStatus.IDLE,
    val scanDevice: String? = null,
    val scanLabels: List<ScanLabel> = emptyList(),
    /** The control whose name is shown on the device (the others show their number). */
    val selectedScan: Int? = null
) {
    /** AR tab without a guide: the AI names the device and labels its main controls. */
    val scanMode: Boolean get() = scanAvailable && !guided

    val canScan: Boolean
        get() = scanMode && scanStatus != ScanStatus.SEARCHING && cameraTracking &&
            trackingIssue == ArTrackingIssue.NONE && permission == ArCameraPermission.GRANTED &&
            sessionProblem == null

    /** The first scan starts by itself as soon as ARCore has a surface in view. */
    val shouldAutoScan: Boolean get() = canScan && scanStatus == ScanStatus.IDLE && surfaceUnderReticle

    val guided: Boolean get() = steps.isNotEmpty()

    /** How the pins go on the appliance; [AlignPlan.Free] without a guide or without pins. */
    val plan: AlignPlan get() = if (guided) AlignPlan.of(steps, photoAspect) else AlignPlan.Free

    /** The first point is placed and a second one is needed to align the pins. */
    val needsSecondPoint: Boolean get() = placed && !aligned && plan is AlignPlan.TwoPoints

    val currentStep: ManualStep? get() = steps.getOrNull(stepIndex)
    val canGoBack: Boolean get() = stepIndex > 0
    val canGoForward: Boolean get() = stepIndex < steps.lastIndex

    /** The step whose control the user must aim at now, while aligning. */
    val aimStep: ManualStep?
        get() = when (val plan = plan) {
            is AlignPlan.TwoPoints -> when {
                !placed -> steps.getOrNull(plan.first)
                needsSecondPoint -> steps.getOrNull(plan.second)
                else -> null
            }
            is AlignPlan.OnePoint -> if (!placed) steps.getOrNull(plan.first) else null
            AlignPlan.Free -> null
        }

    /** "Find automatically": the AI looks for both alignment controls in the camera frame. */
    val canAutoAlign: Boolean
        get() = autoAlignAvailable && plan is AlignPlan.TwoPoints && !placed && !searching &&
            cameraTracking && trackingIssue == ArTrackingIssue.NONE &&
            permission == ArCameraPermission.GRANTED && sessionProblem == null

    /** "Place point" while aligning guide pins, "Place arrow" otherwise. */
    val placeLabel: ArText get() = if (plan == AlignPlan.Free) ArText.PLACE_ARROW else ArText.PLACE_POINT

    /** "Re-align" for guide pins, "Move arrow" for the free arrow. */
    val recenterLabel: ArText get() = if (plan == AlignPlan.Free) ArText.MOVE_ARROW else ArText.REALIGN

    val phase: ArGuidePhase
        get() = when {
            permission == ArCameraPermission.DENIED ||
                permission == ArCameraPermission.PERMANENTLY_DENIED -> ArGuidePhase.PERMISSION_DENIED
            permission != ArCameraPermission.GRANTED -> ArGuidePhase.ASKING_PERMISSION
            sessionProblem != null -> ArGuidePhase.SESSION_ERROR
            trackingIssue != ArTrackingIssue.NONE -> ArGuidePhase.TRACKING_LOST
            placed && !anchorTracking -> ArGuidePhase.TRACKING_LOST
            placed && !needsSecondPoint -> ArGuidePhase.PLACED
            cameraTracking && surfaceUnderReticle -> ArGuidePhase.READY_TO_PLACE
            else -> ArGuidePhase.SCANNING
        }

    /** The AR camera view is shown only while it can run. */
    val showsCamera: Boolean
        get() = permission == ArCameraPermission.GRANTED && sessionProblem == null

    /** "Place arrow here" works only with a tracked surface under the reticle. */
    val canPlace: Boolean get() = phase == ArGuidePhase.READY_TO_PLACE && !searching

    /**
     * The status line, as one or two sentences (keys the UI translates); TalkBack
     * announces it when it changes.
     */
    val message: List<ArLine>
        get() = when {
            searching && showsCamera -> listOf(searchingMessage())
            else -> scanMessage()?.let(::listOf) ?: phaseMessage()
        }

    private fun phaseMessage(): List<ArLine> = when (phase) {
        ArGuidePhase.ASKING_PERMISSION -> listOf(ArLine(ArText.CAMERA_NEEDED))
        ArGuidePhase.PERMISSION_DENIED -> listOf(
            ArLine(if (permission == ArCameraPermission.PERMANENTLY_DENIED) ArText.CAMERA_OFF else ArText.CAMERA_NEEDED)
        )
        ArGuidePhase.SESSION_ERROR -> listOf(ArLine(sessionProblemText(sessionProblem)))
        ArGuidePhase.TRACKING_LOST -> listOf(ArLine(trackingIssueText(trackingIssue)))
        ArGuidePhase.SCANNING -> listOf(ArLine(ArText.POINT_AT_APPLIANCE))
        ArGuidePhase.READY_TO_PLACE -> aimMessage()
        ArGuidePhase.PLACED -> listOf(placedMessage())
    }

    /** The scan's own line in the AR tab; null when the phase message applies. */
    private fun scanMessage(): ArLine? {
        val scanPhases = setOf(ArGuidePhase.SCANNING, ArGuidePhase.READY_TO_PLACE, ArGuidePhase.PLACED)
        if (!scanMode || phase !in scanPhases) return null
        val placed = scanLabels.count { it.placed }
        val device = scanDevice.orEmpty()
        return when (scanStatus) {
            ScanStatus.IDLE -> ArLine(ArText.SCAN_IDLE)
            ScanStatus.SEARCHING -> ArLine(ArText.SCAN_SEARCHING)
            ScanStatus.NOTHING -> ArLine(ArText.SCAN_NOTHING)
            ScanStatus.ERROR -> ArLine(ArText.SCAN_ERROR)
            ScanStatus.DONE -> when {
                scanLabels.isEmpty() -> ArLine(ArText.SCAN_NO_CONTROLS, listOf(device))
                placed == scanLabels.size -> ArLine(ArText.SCAN_ALL, listOf(device, scanLabels.size))
                else -> ArLine(ArText.SCAN_SOME, listOf(device, placed, scanLabels.size))
            }
        }
    }

    private fun searchingMessage(): ArLine {
        val plan = plan as? AlignPlan.TwoPoints ?: return ArLine(ArText.LOOKING_FOR_CONTROLS)
        val first = steps.getOrNull(plan.first)?.number ?: 0
        val second = steps.getOrNull(plan.second)?.number ?: 0
        return ArLine(ArText.LOOKING_FOR_STEPS, listOf(first, second))
    }

    private fun autoResultPrefix(): ArLine? = when (autoResult) {
        AutoAlignResult.NONE -> ArLine(ArText.AUTO_NONE)
        AutoAlignResult.ERROR -> ArLine(ArText.AUTO_ERROR)
        AutoAlignResult.FIRST_ONLY -> ArLine(
            ArText.AUTO_FIRST,
            listOf(steps.getOrNull((plan as? AlignPlan.TwoPoints)?.first ?: -1)?.number ?: 0)
        )
        AutoAlignResult.BOTH, null -> null
    }

    private fun aimMessage(): List<ArLine> {
        val step = aimStep ?: return listOf(ArLine(ArText.SURFACE_FOUND))
        val target = listOf<Any>(step.number, step.title)
        val main = when {
            alignFailed -> ArLine(ArText.ALIGN_MISMATCH, target)
            needsSecondPoint -> ArLine(ArText.AIM_SECOND, target)
            plan is AlignPlan.TwoPoints -> ArLine(ArText.AIM_FIRST, target)
            else -> ArLine(ArText.AIM_SINGLE, target)
        }
        return listOfNotNull(autoResultPrefix(), main)
    }

    private fun placedMessage(): ArLine {
        val step = currentStep ?: return ArLine(ArText.ARROW_PLACED)
        return when {
            plan == AlignPlan.Free -> ArLine(ArText.ARROW_PLACED_GUIDE)
            step.pin != null -> ArLine(ArText.STEP_MARKED, listOf(step.number, steps.size))
            else -> ArLine(ArText.STEP_NO_POINT, listOf(step.number))
        }
    }
}

private fun sessionProblemText(problem: ArSessionProblem?): ArText = when (problem) {
    ArSessionProblem.INSTALL_DECLINED -> ArText.INSTALL_NEEDED
    ArSessionProblem.NOT_COMPATIBLE -> ArText.NOT_COMPATIBLE
    ArSessionProblem.CAMERA_UNAVAILABLE -> ArText.CAMERA_BUSY
    ArSessionProblem.OTHER, null -> ArText.AR_STOPPED
}

private fun trackingIssueText(issue: ArTrackingIssue): ArText = when (issue) {
    ArTrackingIssue.INSUFFICIENT_LIGHT -> ArText.TOO_DARK
    ArTrackingIssue.EXCESSIVE_MOTION -> ArText.TOO_FAST
    ArTrackingIssue.INSUFFICIENT_FEATURES -> ArText.LOW_DETAIL
    ArTrackingIssue.CAMERA_UNAVAILABLE -> ArText.CAMERA_BUSY_TRACKING
    ArTrackingIssue.BAD_STATE -> ArText.TRACKING_STOPPED
    ArTrackingIssue.NONE -> ArText.ANCHOR_LOST
}

/**
 * State of the AR screen (#240): camera permission, session errors, tracking and
 * the placed arrow. The AR view reports what ARCore says; this class decides what
 * the user sees, so it can be tested without a phone.
 */
class ArGuideViewModel : ViewModel() {

    private val _state = MutableStateFlow(ArGuideUiState())
    val state: StateFlow<ArGuideUiState> = _state.asStateFlow()

    /** [permanentlyDenied]: the system will not show the dialog again. */
    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        _state.update {
            it.copy(
                permission = when {
                    granted -> ArCameraPermission.GRANTED
                    permanentlyDenied -> ArCameraPermission.PERMANENTLY_DENIED
                    else -> ArCameraPermission.DENIED
                }
            )
        }
    }

    fun onSessionFailed(problem: ArSessionProblem) {
        _state.update { it.copy(sessionProblem = problem, placed = false, surfaceUnderReticle = false) }
    }

    fun onCameraTracking(tracking: Boolean) {
        _state.update { it.copy(cameraTracking = tracking) }
    }

    fun onTrackingIssue(issue: ArTrackingIssue) {
        _state.update { it.copy(trackingIssue = issue) }
    }

    fun onSurfaceUnderReticle(found: Boolean) {
        _state.update { it.copy(surfaceUnderReticle = found) }
    }

    /**
     * Opens the screen fresh, with [steps] of a guide (#241) or none (#240).
     * Keeps only the permission: an anchor of a previous visit no longer exists.
     */
    fun start(
        steps: List<ManualStep> = emptyList(),
        initialStepIndex: Int = 0,
        autoAlignAvailable: Boolean = false,
        scanAvailable: Boolean = false
    ) {
        _state.update {
            ArGuideUiState(
                permission = it.permission,
                sessionKey = it.sessionKey + 1,
                steps = steps,
                stepIndex = initialStepIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0)),
                autoAlignAvailable = autoAlignAvailable,
                scanAvailable = scanAvailable
            )
        }
    }

    /** A camera frame went to the AI to be named (AR tab). */
    fun onScanStarted() {
        _state.update { it.copy(scanStatus = ScanStatus.SEARCHING) }
    }

    /** The AI answered; [device] is null when it saw no device. Ignored after a reset. */
    fun onScanFinished(device: String?, labels: List<ScanLabel>) {
        _state.update {
            if (it.scanStatus != ScanStatus.SEARCHING) return@update it
            val shown = if (device == null) emptyList() else labels
            it.copy(
                scanStatus = if (device == null) ScanStatus.NOTHING else ScanStatus.DONE,
                scanDevice = device,
                scanLabels = shown,
                // The first control on the device starts selected, so one name is always visible.
                selectedScan = shown.indexOfFirst { label -> label.placed }.takeIf { index -> index >= 0 }
            )
        }
    }

    /** Shows the name of control [index] on the device; tapping the selected one again hides it. */
    fun selectScan(index: Int) {
        _state.update {
            when {
                index !in it.scanLabels.indices -> it
                it.selectedScan == index -> it.copy(selectedScan = null)
                else -> it.copy(selectedScan = index)
            }
        }
    }

    /** No answer (network, server): keep the previous labels, offer Scan again. */
    fun onScanError() {
        _state.update { if (it.scanStatus == ScanStatus.SEARCHING) it.copy(scanStatus = ScanStatus.ERROR) else it }
    }

    /** "Find automatically" was tapped: the frame goes to the AI. */
    fun onAutoAlignStarted() {
        _state.update { it.copy(searching = true, autoResult = null) }
    }

    /**
     * The AI search ended; with [AutoAlignResult.BOTH] the pins are already on the appliance.
     * A late answer after Reset or Close (no search running) is ignored.
     */
    fun onAutoAlignFinished(result: AutoAlignResult) {
        _state.update {
            if (!it.searching) return@update it
            when (result) {
                AutoAlignResult.BOTH -> it.copy(
                    searching = false, autoResult = result, placed = true, aligned = true,
                    anchorTracking = true, alignFailed = false
                )
                AutoAlignResult.FIRST_ONLY -> it.copy(
                    searching = false, autoResult = result, placed = true, aligned = false,
                    anchorTracking = true, alignFailed = false
                )
                AutoAlignResult.NONE, AutoAlignResult.ERROR -> it.copy(searching = false, autoResult = result)
            }
        }
    }

    fun onPhotoAspect(aspect: Float) {
        if (aspect > 0f) _state.update { it.copy(photoAspect = aspect) }
    }

    /** The first point (the anchor) or, when aligning, the second point was placed. */
    fun onArrowPlaced() {
        _state.update {
            if (it.needsSecondPoint) {
                it.copy(aligned = true, alignFailed = false, autoResult = null)
            } else {
                it.copy(placed = true, anchorTracking = true, aligned = false, alignFailed = false, autoResult = null)
            }
        }
    }

    /** The second point does not fit the photo: keep the first, ask for the second again. */
    fun onAlignmentFailed() {
        _state.update { it.copy(alignFailed = true) }
    }

    fun nextStep() = selectStep(_state.value.stepIndex + 1)

    fun previousStep() = selectStep(_state.value.stepIndex - 1)

    fun selectStep(index: Int) {
        _state.update { if (index in it.steps.indices) it.copy(stepIndex = index) else it }
    }

    fun onAnchorTracking(tracking: Boolean) {
        _state.update { it.copy(anchorTracking = tracking) }
    }

    /** Recenter: remove the arrow (or the aligned pins) and keep the session, so the user aims again. */
    fun moveArrow() {
        _state.update {
            it.copy(placed = false, anchorTracking = true, aligned = false, alignFailed = false, autoResult = null)
        }
    }

    /** Reset: rebuild the session from scratch (also the retry after an error). */
    fun resetSession() {
        _state.update {
            ArGuideUiState(
                permission = it.permission,
                sessionKey = it.sessionKey + 1,
                steps = it.steps,
                stepIndex = it.stepIndex,
                photoAspect = it.photoAspect,
                autoAlignAvailable = it.autoAlignAvailable,
                scanAvailable = it.scanAvailable
            )
        }
    }
}
