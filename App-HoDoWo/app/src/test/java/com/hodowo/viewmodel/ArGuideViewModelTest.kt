package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AR screen state (#240): permission, session errors, tracking, placement and reset. */
class ArGuideViewModelTest {

    private fun trackingWithSurface() = ArGuideViewModel().apply {
        onPermissionResult(granted = true)
        onCameraTracking(true)
        onSurfaceUnderReticle(true)
    }

    @Test
    fun startsByAskingForTheCameraWithoutShowingIt() {
        val state = ArGuideViewModel().state.value
        assertEquals(ArGuidePhase.ASKING_PERMISSION, state.phase)
        assertFalse(state.showsCamera)
    }

    @Test
    fun grantedPermissionShowsTheCameraAndScans() {
        val viewModel = ArGuideViewModel()
        viewModel.onPermissionResult(granted = true)

        assertTrue(viewModel.state.value.showsCamera)
        assertEquals(ArGuidePhase.SCANNING, viewModel.state.value.phase)
        assertFalse(viewModel.state.value.canPlace)
    }

    @Test
    fun deniedPermissionCanBeAskedAgain() {
        val viewModel = ArGuideViewModel()
        viewModel.onPermissionResult(granted = false)

        val state = viewModel.state.value
        assertEquals(ArCameraPermission.DENIED, state.permission)
        assertEquals(ArGuidePhase.PERMISSION_DENIED, state.phase)
        assertFalse(state.showsCamera)
    }

    @Test
    fun permanentlyDeniedPermissionPointsToSettings() {
        val viewModel = ArGuideViewModel()
        viewModel.onPermissionResult(granted = false, permanentlyDenied = true)

        assertEquals(ArCameraPermission.PERMANENTLY_DENIED, viewModel.state.value.permission)
        assertTrue(viewModel.state.value.message.contains("Settings"))
    }

    @Test
    fun permissionGrantedLaterRecovers() {
        val viewModel = ArGuideViewModel()
        viewModel.onPermissionResult(granted = false, permanentlyDenied = true)
        viewModel.onPermissionResult(granted = true)

        assertEquals(ArGuidePhase.SCANNING, viewModel.state.value.phase)
    }

    @Test
    fun aSurfaceIsNeededOnlyWhileTheCameraTracks() {
        val viewModel = ArGuideViewModel()
        viewModel.onPermissionResult(granted = true)
        viewModel.onSurfaceUnderReticle(true)
        assertEquals(ArGuidePhase.SCANNING, viewModel.state.value.phase)

        viewModel.onCameraTracking(true)
        assertEquals(ArGuidePhase.READY_TO_PLACE, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.canPlace)
    }

    @Test
    fun placingTheArrowAnnouncesIt() {
        val viewModel = trackingWithSurface()
        viewModel.onArrowPlaced()

        val state = viewModel.state.value
        assertEquals(ArGuidePhase.PLACED, state.phase)
        assertFalse(state.canPlace)
        assertTrue(state.message.startsWith("Arrow placed"))
    }

    @Test
    fun cameraTrackingIssueIsExplainedAndClearsWhenItEnds() {
        val viewModel = trackingWithSurface()
        viewModel.onArrowPlaced()

        viewModel.onTrackingIssue(ArTrackingIssue.INSUFFICIENT_LIGHT)
        assertEquals(ArGuidePhase.TRACKING_LOST, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.message.contains("dark"))

        viewModel.onTrackingIssue(ArTrackingIssue.NONE)
        assertEquals(ArGuidePhase.PLACED, viewModel.state.value.phase)
    }

    @Test
    fun anArrowThatLosesItsAnchorIsTrackingLost() {
        val viewModel = trackingWithSurface()
        viewModel.onArrowPlaced()
        viewModel.onAnchorTracking(false)
        assertEquals(ArGuidePhase.TRACKING_LOST, viewModel.state.value.phase)

        viewModel.onAnchorTracking(true)
        assertEquals(ArGuidePhase.PLACED, viewModel.state.value.phase)
    }

    @Test
    fun moveArrowKeepsTheSessionAndAimsAgain() {
        val viewModel = trackingWithSurface()
        viewModel.onArrowPlaced()
        val key = viewModel.state.value.sessionKey

        viewModel.moveArrow()

        assertEquals(ArGuidePhase.READY_TO_PLACE, viewModel.state.value.phase)
        assertEquals(key, viewModel.state.value.sessionKey)
    }

    @Test
    fun sessionErrorHidesTheCameraAndExplains() {
        val viewModel = trackingWithSurface()
        viewModel.onArrowPlaced()
        viewModel.onSessionFailed(ArSessionProblem.INSTALL_DECLINED)

        val state = viewModel.state.value
        assertEquals(ArGuidePhase.SESSION_ERROR, state.phase)
        assertFalse(state.showsCamera)
        assertFalse(state.placed)
        assertTrue(state.message.contains("Google Play Services for AR"))
    }

    @Test
    fun resetRebuildsTheSessionAndKeepsThePermission() {
        val viewModel = trackingWithSurface()
        viewModel.onArrowPlaced()
        viewModel.onSessionFailed(ArSessionProblem.CAMERA_UNAVAILABLE)

        viewModel.resetSession()

        val state = viewModel.state.value
        assertEquals(1, state.sessionKey)
        assertEquals(ArCameraPermission.GRANTED, state.permission)
        assertNull(state.sessionProblem)
        assertFalse(state.placed)
        assertFalse(state.cameraTracking)
        assertEquals(ArGuidePhase.SCANNING, state.phase)
    }

    @Test
    fun everyPhaseAndProblemHasAMessage() {
        val states = listOf(
            ArGuideUiState(),
            ArGuideUiState(permission = ArCameraPermission.DENIED),
            ArGuideUiState(permission = ArCameraPermission.GRANTED)
        ) + ArSessionProblem.values().map {
            ArGuideUiState(permission = ArCameraPermission.GRANTED, sessionProblem = it)
        } + ArTrackingIssue.values().map {
            ArGuideUiState(permission = ArCameraPermission.GRANTED, trackingIssue = it, placed = true)
        }
        states.forEach { assertTrue(it.message.isNotBlank()) }
    }

    // Guide in AR (#241)

    private val guide = listOf(
        ManualStep(1, "Open the door", "", StepPin(0.2f, 0.3f)),
        ManualStep(2, "Wait", "", null),
        ManualStep(3, "Press Start", "", StepPin(0.8f, 0.9f))
    )

    private fun guided(steps: List<ManualStep> = guide, initial: Int = 0) = ArGuideViewModel().apply {
        onPermissionResult(granted = true)
        start(steps, initial)
        onCameraTracking(true)
        onSurfaceUnderReticle(true)
    }

    @Test
    fun startOpensTheGuideOnTheChosenStepAndForgetsThePreviousAnchor() {
        val viewModel = trackingWithSurface()
        viewModel.onArrowPlaced()

        viewModel.start(guide, initialStepIndex = 2)

        val state = viewModel.state.value
        assertTrue(state.guided)
        assertEquals(2, state.stepIndex)
        assertFalse(state.placed)
        assertEquals(ArCameraPermission.GRANTED, state.permission)
        assertEquals(AlignPlan.TwoPoints(0, 2), state.plan)
    }

    @Test
    fun startClampsAnOutOfRangeStep() {
        val viewModel = ArGuideViewModel()
        viewModel.start(guide, initialStepIndex = 9)
        assertEquals(2, viewModel.state.value.stepIndex)
    }

    @Test
    fun twoPointAlignmentAsksForEachControlInTurn() {
        val viewModel = guided()
        assertEquals("Place point", viewModel.state.value.placeLabel)
        assertTrue(viewModel.state.value.message.contains("step 1 (Open the door)"))
        assertTrue(viewModel.state.value.message.contains("1 of 2"))

        viewModel.onArrowPlaced()
        val second = viewModel.state.value
        assertTrue(second.needsSecondPoint)
        assertEquals(ArGuidePhase.READY_TO_PLACE, second.phase)
        assertTrue(second.message.contains("step 3 (Press Start)"))
        assertTrue(second.message.contains("2 of 2"))

        viewModel.onArrowPlaced()
        val aligned = viewModel.state.value
        assertTrue(aligned.aligned)
        assertEquals(ArGuidePhase.PLACED, aligned.phase)
        assertTrue(aligned.message.contains("marked on the appliance"))
        assertEquals("Re-align", aligned.recenterLabel)
    }

    @Test
    fun aSecondPointThatDoesNotFitAsksAgain() {
        val viewModel = guided()
        viewModel.onArrowPlaced()
        viewModel.onAlignmentFailed()

        val state = viewModel.state.value
        assertTrue(state.needsSecondPoint)
        assertTrue(state.message.contains("don't match the photo"))

        viewModel.onArrowPlaced()
        assertFalse(viewModel.state.value.alignFailed)
        assertEquals(ArGuidePhase.PLACED, viewModel.state.value.phase)
    }

    @Test
    fun everyStepCanBeReachedWithoutLeavingAr() {
        val viewModel = guided()
        assertFalse(viewModel.state.value.canGoBack)
        viewModel.previousStep()
        assertEquals(0, viewModel.state.value.stepIndex)

        viewModel.nextStep()
        viewModel.nextStep()
        assertEquals(2, viewModel.state.value.stepIndex)
        assertFalse(viewModel.state.value.canGoForward)
        viewModel.nextStep()
        assertEquals(2, viewModel.state.value.stepIndex)

        viewModel.selectStep(1)
        assertEquals(1, viewModel.state.value.stepIndex)
        viewModel.selectStep(7)
        assertEquals(1, viewModel.state.value.stepIndex)
    }

    @Test
    fun aStepWithoutPinSaysSo() {
        val viewModel = guided(initial = 1)
        viewModel.onArrowPlaced()
        viewModel.onArrowPlaced()
        assertTrue(viewModel.state.value.message.contains("no point on the appliance"))
    }

    @Test
    fun realignAndResetKeepTheGuideAndTheStep() {
        val viewModel = guided(initial = 2)
        viewModel.onArrowPlaced()
        viewModel.onArrowPlaced()

        viewModel.moveArrow()
        assertFalse(viewModel.state.value.placed)
        assertFalse(viewModel.state.value.aligned)
        assertEquals(2, viewModel.state.value.stepIndex)

        viewModel.onPhotoAspect(1.5f)
        viewModel.resetSession()
        val state = viewModel.state.value
        assertEquals(guide, state.steps)
        assertEquals(2, state.stepIndex)
        assertEquals(1.5f, state.photoAspect, 0f)
    }

    @Test
    fun oneControlNeedsOnePoint() {
        val steps = listOf(ManualStep(1, "Turn the dial", "", StepPin(0.5f, 0.5f)), ManualStep(2, "Wait", "", null))
        val viewModel = guided(steps)
        assertEquals(AlignPlan.OnePoint(0), viewModel.state.value.plan)
        assertFalse(viewModel.state.value.message.contains("1 of 2"))

        viewModel.onArrowPlaced()
        assertEquals(ArGuidePhase.PLACED, viewModel.state.value.phase)
    }

    @Test
    fun aGuideWithoutPinsUsesTheFreeArrow() {
        val steps = listOf(ManualStep(1, "Unplug", "", null))
        val viewModel = guided(steps)
        assertEquals(AlignPlan.Free, viewModel.state.value.plan)
        assertEquals("Place arrow", viewModel.state.value.placeLabel)

        viewModel.onArrowPlaced()
        assertTrue(viewModel.state.value.message.contains("Previous and Next"))
    }

    @Test
    fun badPhotoAspectIsIgnored() {
        val viewModel = ArGuideViewModel()
        viewModel.onPhotoAspect(0f)
        assertEquals(DEFAULT_PHOTO_ASPECT, viewModel.state.value.photoAspect, 0f)
    }

    // AR auto-alignment (#18)

    private fun autoGuided() = ArGuideViewModel().apply {
        onPermissionResult(granted = true)
        start(guide, 0, autoAlignAvailable = true)
        onCameraTracking(true)
    }

    @Test
    fun findAutomaticallyNeedsTheBackendTwoControlsAndTracking() {
        assertTrue(autoGuided().state.value.canAutoAlign)
        assertFalse(guided().state.value.canAutoAlign) // no backend
        val oneControl = ArGuideViewModel().apply {
            onPermissionResult(granted = true)
            start(listOf(ManualStep(1, "Turn", "", StepPin(0.5f, 0.5f))), 0, autoAlignAvailable = true)
            onCameraTracking(true)
        }
        assertFalse(oneControl.state.value.canAutoAlign)
        val notTracking = autoGuided().apply { onCameraTracking(false) }
        assertFalse(notTracking.state.value.canAutoAlign)
    }

    @Test
    fun whileSearchingNothingElseCanBePlaced() {
        val viewModel = autoGuided()
        viewModel.onSurfaceUnderReticle(true)
        viewModel.onAutoAlignStarted()

        val state = viewModel.state.value
        assertTrue(state.searching)
        assertFalse(state.canAutoAlign)
        assertFalse(state.canPlace)
        assertTrue(state.message.contains("Looking for steps 1 and 3"))
    }

    @Test
    fun bothControlsFoundPutThePinsOnTheAppliance() {
        val viewModel = autoGuided()
        viewModel.onAutoAlignStarted()
        viewModel.onAutoAlignFinished(AutoAlignResult.BOTH)

        val state = viewModel.state.value
        assertFalse(state.searching)
        assertTrue(state.aligned)
        assertEquals(ArGuidePhase.PLACED, state.phase)
    }

    @Test
    fun onlyTheFirstFoundAsksForTheSecond() {
        val viewModel = autoGuided()
        viewModel.onSurfaceUnderReticle(true)
        viewModel.onAutoAlignStarted()
        viewModel.onAutoAlignFinished(AutoAlignResult.FIRST_ONLY)

        val state = viewModel.state.value
        assertTrue(state.needsSecondPoint)
        assertTrue(state.message.startsWith("Step 1 found."))
        assertTrue(state.message.contains("step 3"))

        viewModel.onArrowPlaced()
        assertEquals(ArGuidePhase.PLACED, viewModel.state.value.phase)
    }

    @Test
    fun nothingFoundOrAnErrorFallsBackToAiming() {
        val viewModel = autoGuided()
        viewModel.onSurfaceUnderReticle(true)
        viewModel.onAutoAlignStarted()
        viewModel.onAutoAlignFinished(AutoAlignResult.NONE)
        assertFalse(viewModel.state.value.placed)
        assertTrue(viewModel.state.value.message.startsWith("The AI couldn't find the controls."))
        assertTrue(viewModel.state.value.canAutoAlign)

        viewModel.onAutoAlignStarted()
        viewModel.onAutoAlignFinished(AutoAlignResult.ERROR)
        assertTrue(viewModel.state.value.message.startsWith("Automatic search didn't work"))

        viewModel.onArrowPlaced()
        assertFalse(viewModel.state.value.message.contains("didn't work"))
    }

    @Test
    fun aLateAnswerAfterResetIsIgnored() {
        val viewModel = autoGuided()
        viewModel.onAutoAlignStarted()
        viewModel.resetSession()
        viewModel.onAutoAlignFinished(AutoAlignResult.BOTH)

        val state = viewModel.state.value
        assertFalse(state.placed)
        assertFalse(state.searching)
        assertTrue(state.autoAlignAvailable)
    }

    // AR tab scan (#18)

    private fun scanning(surface: Boolean = true) = ArGuideViewModel().apply {
        onPermissionResult(granted = true)
        start(scanAvailable = true)
        onCameraTracking(true)
        onSurfaceUnderReticle(surface)
    }

    @Test
    fun theFirstScanStartsByItselfOnceASurfaceIsInView() {
        assertFalse(scanning(surface = false).state.value.shouldAutoScan)
        val viewModel = scanning()
        assertTrue(viewModel.state.value.scanMode)
        assertTrue(viewModel.state.value.shouldAutoScan)
        assertTrue(viewModel.state.value.message.contains("appliance or a keyboard"))

        viewModel.onScanStarted()
        assertFalse(viewModel.state.value.shouldAutoScan)
        assertFalse(viewModel.state.value.canScan)
        assertTrue(viewModel.state.value.message.startsWith("Looking at what's in front of you"))
    }

    @Test
    fun noScanWithAGuideOrWithoutTheBackend() {
        val guide = ArGuideViewModel().apply { start(guide, 0, scanAvailable = true) }
        assertFalse(guide.state.value.scanMode)
        val offline = ArGuideViewModel().apply {
            onPermissionResult(granted = true)
            start()
            onCameraTracking(true)
        }
        assertFalse(offline.state.value.canScan)
    }

    @Test
    fun aNamedDeviceShowsItsLabels() {
        val viewModel = scanning()
        viewModel.onScanStarted()
        viewModel.onScanFinished(
            "Keyboard",
            listOf(ScanLabel("Enter", "Confirms", placed = true), ScanLabel("Esc", "Cancels", placed = true))
        )

        val state = viewModel.state.value
        assertEquals(ScanStatus.DONE, state.scanStatus)
        assertEquals("Keyboard: 2 main controls labelled.", state.message)
        assertTrue(state.canScan)
        assertFalse(state.shouldAutoScan)
    }

    @Test
    fun labelsOffTheSurfaceAreCounted() {
        val viewModel = scanning()
        viewModel.onScanStarted()
        viewModel.onScanFinished("Oven", listOf(ScanLabel("Light", "", true), ScanLabel("Timer", "", false)))
        assertTrue(viewModel.state.value.message.contains("1 of 2 controls labelled"))
    }

    @Test
    fun aDeviceWithoutVisibleControlsAsksToGetCloser() {
        val viewModel = scanning()
        viewModel.onScanStarted()
        viewModel.onScanFinished("Fridge", emptyList())
        assertTrue(viewModel.state.value.message.contains("get closer"))
    }

    @Test
    fun nothingFoundOrNoNetworkOffersScanAgain() {
        val viewModel = scanning()
        viewModel.onScanStarted()
        viewModel.onScanFinished(null, listOf(ScanLabel("x", "", true)))
        assertEquals(ScanStatus.NOTHING, viewModel.state.value.scanStatus)
        assertTrue(viewModel.state.value.scanLabels.isEmpty())
        assertTrue(viewModel.state.value.message.startsWith("No appliance or keyboard found"))

        viewModel.onScanStarted()
        viewModel.onScanError()
        assertEquals(ScanStatus.ERROR, viewModel.state.value.scanStatus)
        assertTrue(viewModel.state.value.canScan)
    }

    @Test
    fun aLateScanAfterResetIsIgnoredAndTheNextScanStartsByItself() {
        val viewModel = scanning()
        viewModel.onScanStarted()
        viewModel.resetSession()
        viewModel.onScanFinished("Keyboard", emptyList())
        viewModel.onScanError()

        val state = viewModel.state.value
        assertEquals(ScanStatus.IDLE, state.scanStatus)
        assertTrue(state.scanAvailable)
    }
}
