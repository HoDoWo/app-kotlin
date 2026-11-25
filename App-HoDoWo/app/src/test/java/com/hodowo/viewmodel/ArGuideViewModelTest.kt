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
}
