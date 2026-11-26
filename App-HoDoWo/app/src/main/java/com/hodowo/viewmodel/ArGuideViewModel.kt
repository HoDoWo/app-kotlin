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
    val scanLabels: List<ScanLabel> = emptyList()
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
    val placeLabel: String get() = if (plan == AlignPlan.Free) "Place arrow" else "Place point"

    /** "Re-align" for guide pins, "Move arrow" for the free arrow. */
    val recenterLabel: String get() = if (plan == AlignPlan.Free) "Move arrow" else "Re-align"

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

    /** One sentence for the status bar; also announced by TalkBack when it changes. */
    val message: String
        get() = if (searching && showsCamera) searchingMessage() else scanMessage() ?: when (phase) {
            ArGuidePhase.ASKING_PERMISSION -> "AR needs the camera to show the guide on your appliance."
            ArGuidePhase.PERMISSION_DENIED -> if (permission == ArCameraPermission.PERMANENTLY_DENIED) {
                "Camera access is off. Turn it on in Settings to use AR."
            } else {
                "AR needs the camera to show the guide on your appliance."
            }
            ArGuidePhase.SESSION_ERROR -> sessionProblemMessage(sessionProblem)
            ArGuidePhase.TRACKING_LOST -> trackingIssueMessage(trackingIssue)
            ArGuidePhase.SCANNING -> "Point the camera at the appliance and move the phone slowly."
            ArGuidePhase.READY_TO_PLACE -> aimMessage()
            ArGuidePhase.PLACED -> placedMessage()
        }

    /** The scan's own line in the AR tab; null when the phase message applies. */
    private fun scanMessage(): String? {
        val scanPhases = setOf(ArGuidePhase.SCANNING, ArGuidePhase.READY_TO_PLACE, ArGuidePhase.PLACED)
        if (!scanMode || phase !in scanPhases) return null
        val placed = scanLabels.count { it.placed }
        return when (scanStatus) {
            ScanStatus.IDLE -> "Point the camera at an appliance or a keyboard: it will be named and labelled."
            ScanStatus.SEARCHING -> "Looking at what's in front of you… Keep it in view."
            ScanStatus.NOTHING -> "No appliance or keyboard found. Point at one and tap Scan again."
            ScanStatus.ERROR -> "The scan didn't work (check the connection). Tap Scan again."
            ScanStatus.DONE -> when {
                scanLabels.isEmpty() -> "$scanDevice. No controls visible: get closer and tap Scan again."
                placed == scanLabels.size -> "$scanDevice: ${scanLabels.size} main controls labelled."
                else -> "$scanDevice: $placed of ${scanLabels.size} controls labelled; the others are off the surface."
            }
        }
    }

    private fun searchingMessage(): String {
        val plan = plan as? AlignPlan.TwoPoints ?: return "Looking for the controls on the appliance…"
        val first = steps.getOrNull(plan.first)?.number
        val second = steps.getOrNull(plan.second)?.number
        return "Looking for steps $first and $second on the appliance… Keep it in view."
    }

    private fun autoResultPrefix(): String = when (autoResult) {
        AutoAlignResult.NONE -> "The AI couldn't find the controls. "
        AutoAlignResult.ERROR -> "Automatic search didn't work (check the connection). "
        AutoAlignResult.FIRST_ONLY -> "Step ${steps.getOrNull((plan as? AlignPlan.TwoPoints)?.first ?: -1)?.number} found. "
        AutoAlignResult.BOTH, null -> ""
    }

    private fun aimMessage(): String {
        val step = aimStep ?: return "Surface found. Aim at the control and tap Place arrow."
        val target = "the control for step ${step.number} (${step.title})"
        return autoResultPrefix() + when {
            alignFailed -> "Those two points don't match the photo. Aim exactly at $target and try again."
            needsSecondPoint -> "Now aim the dot at $target and tap Place point (2 of 2)."
            plan is AlignPlan.TwoPoints -> "Aim the dot at $target and tap Place point (1 of 2)."
            else -> "Aim the dot at $target and tap Place point."
        }
    }

    private fun placedMessage(): String {
        val step = currentStep ?: return "Arrow placed. It stays on the appliance while you move."
        return when {
            plan == AlignPlan.Free -> "Arrow placed. Use Previous and Next to follow the steps."
            step.pin != null -> "Step ${step.number} of ${steps.size} is marked on the appliance."
            else -> "Step ${step.number} has no point on the appliance: follow the text below."
        }
    }
}

private fun sessionProblemMessage(problem: ArSessionProblem?): String = when (problem) {
    ArSessionProblem.INSTALL_DECLINED ->
        "AR needs Google Play Services for AR. Install it to continue, or use the 2D guide."
    ArSessionProblem.NOT_COMPATIBLE -> "AR can't run on this phone. The 2D guide works everywhere."
    ArSessionProblem.CAMERA_UNAVAILABLE -> "The camera is busy or unavailable. Close other camera apps and try again."
    ArSessionProblem.OTHER, null -> "AR stopped unexpectedly. Try again, or use the 2D guide."
}

private fun trackingIssueMessage(issue: ArTrackingIssue): String = when (issue) {
    ArTrackingIssue.INSUFFICIENT_LIGHT -> "Too dark to track. Turn on a light."
    ArTrackingIssue.EXCESSIVE_MOTION -> "Moving too fast. Move the phone slowly."
    ArTrackingIssue.INSUFFICIENT_FEATURES ->
        "Can't see enough detail. Include the edges or controls of the appliance in view."
    ArTrackingIssue.CAMERA_UNAVAILABLE -> "The camera is busy. Close other camera apps."
    ArTrackingIssue.BAD_STATE -> "Tracking stopped. Tap Reset to start again."
    ArTrackingIssue.NONE -> "The arrow lost its place. Point at the appliance again, or tap Move arrow."
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
            it.copy(
                scanStatus = if (device == null) ScanStatus.NOTHING else ScanStatus.DONE,
                scanDevice = device,
                scanLabels = if (device == null) emptyList() else labels
            )
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
