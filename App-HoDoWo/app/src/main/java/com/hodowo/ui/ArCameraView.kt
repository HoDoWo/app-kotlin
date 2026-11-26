package com.hodowo.ui

import com.hodowo.ui.theme.OnOverlay
import com.hodowo.ui.theme.OverlayBrand
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.android.filament.MaterialInstance
import com.hodowo.data.FrameTimeMeter
import com.hodowo.viewmodel.AlignPlan
import com.hodowo.viewmodel.ArGuideUiState
import com.hodowo.viewmodel.ArSessionProblem
import com.hodowo.viewmodel.ArTrackingIssue
import com.hodowo.viewmodel.PinGroup
import com.hodowo.viewmodel.PinMapper
import com.hodowo.viewmodel.StepPin
import com.hodowo.viewmodel.ScreenPoint
import com.hodowo.viewmodel.Vec3
import com.hodowo.viewmodel.multiplyMatrices
import com.hodowo.viewmodel.projectToView
import com.hodowo.viewmodel.label
import com.hodowo.viewmodel.markerText
import com.hodowo.viewmodel.pinGroups
import io.github.sceneview.SceneScope
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.ARSessionFailure
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.placementHit
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberOnGestureListener

/** Distance used for instant placement before ARCore has found a plane: about an arm's length. */
private const val INSTANT_PLACEMENT_DISTANCE_M = 0.6f

private const val RETICLE_RADIUS = 0.035f
private const val RETICLE_HEIGHT = 0.002f

/** Guide pins (#241): a dot on each control; its step numbers are drawn flat on screen above it. */
private const val PIN_RADIUS = 0.006f
private const val CURRENT_PIN_RADIUS = 0.009f

/** AR tab scan: a small dot on each control, smaller than guide pins (keys are small). */
private const val SCAN_DOT_RADIUS = 0.005f

/** What the AR view reports while it runs. */
internal class ArCameraEvents(
    val onCameraTracking: (Boolean) -> Unit,
    val onTrackingIssue: (ArTrackingIssue) -> Unit,
    val onSurfaceUnderReticle: (Boolean) -> Unit,
    val onTap: (HitResult?) -> Unit,
    val onAnchorTracking: (Boolean) -> Unit,
    val onSessionFailed: (ArSessionProblem) -> Unit,
    val onSelectScan: (Int) -> Unit = {},
    val onSelectStep: (Int) -> Unit = {}
)

/** A control of the AR tab scan, anchored on the real device. */
internal class ScanMarker(val anchor: Anchor, val index: Int, val label: String)

/**
 * The anchor, the pin mapping and what the last frame saw. ARCore frames and
 * button taps both arrive on the main thread, so no locking is needed.
 */
internal class ArPlacement {
    /** Latest surface hit at the screen centre, refreshed every frame. */
    var centerHit: HitResult? = null

    /** Latest camera pose, for the side of the surface the user is on. */
    var cameraPose: Pose? = null

    /** The running session, for the planes it tracks (auto-alignment). */
    var session: Session? = null

    /** Set to receive the next camera frame (auto-alignment); cleared once delivered. */
    var captureRequest: ((CapturedFrame?) -> Unit)? = null

    /** Frame time of the AR view (#241 performance check). */
    val meter = FrameTimeMeter()

    var anchor by mutableStateOf<Anchor?>(null)
        private set

    /** Labels of the AR tab scan, one anchor each. */
    var scanMarkers by mutableStateOf<List<ScanMarker>>(emptyList())
        private set

    /** Where each scan marker shows on screen in the latest frame (null: not in view). */
    var scanScreenPoints by mutableStateOf<List<ScreenPoint?>>(emptyList())

    /** Where each guide marker shows on screen in the latest frame (null: not in view). */
    var guideScreenPoints by mutableStateOf<List<ScreenPoint?>>(emptyList())

    /** When the pending capture was first asked for (it waits briefly for depth). */
    var captureSince: Long? = null

    /** Replaces the scan labels (the old anchors are detached). */
    fun showScan(markers: List<ScanMarker>) {
        clearScan()
        scanMarkers = markers
    }

    fun clearScan() {
        scanMarkers.forEach { runCatching { it.anchor.detach() } }
        scanMarkers = emptyList()
        scanScreenPoints = emptyList()
    }

    /** Where the guide pins go, once the user has aimed at the alignment points. */
    var mapper by mutableStateOf<PinMapper?>(null)
        private set

    /** Anchors the first point on [hit]; false when ARCore cannot create an anchor there. */
    fun place(hit: HitResult?): Boolean {
        val created = hit?.createAnchorOrNull() ?: return false
        clear()
        anchor = created
        return true
    }

    /** Anchors the first point on a surface point found by the AI (on its plane when it has one). */
    fun anchorAt(point: SurfacePoint): Boolean {
        val created = runCatching {
            point.plane?.createAnchor(point.pose) ?: session?.createAnchor(point.pose)
        }.getOrNull() ?: return false
        clear()
        anchor = created
        return true
    }

    /** All pins are on one control: it is the anchor itself. */
    fun alignSingle(origin: StepPin) {
        mapper = PinMapper.single(origin, cameraLocal() ?: Vec3(0f, 1f, 0f))
    }

    /** Fits the photo to the surface with the second point; false when it does not fit. */
    fun alignSecond(hit: HitResult?, first: StepPin, second: StepPin, aspect: Float): Boolean {
        val point = hit ?: return false
        return alignSecondAt(point.hitPose.translation, first, second, aspect)
    }

    /** Same as [alignSecond], for a world point (auto-alignment). */
    fun alignSecondAt(worldPoint: FloatArray, first: StepPin, second: StepPin, aspect: Float): Boolean {
        val origin = anchor ?: return false
        val camera = cameraLocal() ?: return false
        val local = origin.pose.inverse().transformPoint(worldPoint).toVec3()
        mapper = PinMapper.align(first, second, local, camera, aspect)
        return mapper != null
    }

    private fun cameraLocal(): Vec3? {
        val origin = anchor ?: return null
        val camera = cameraPose ?: return null
        return origin.pose.inverse().transformPoint(camera.translation).toVec3()
    }

    /** Detaches the anchor, so ARCore stops tracking it and frees its resources. */
    fun clear() {
        captureRequest = null
        captureSince = null
        guideScreenPoints = emptyList()
        anchor?.let { runCatching { it.detach() } }
        anchor = null
        mapper = null
    }
}

private fun FloatArray.toVec3() = Vec3(this[0], this[1], this[2])

/** Wait at most this long for ARCore depth before taking the scan photo (nanoseconds). */
private const val DEPTH_WAIT_NS = 1_500_000_000L

private const val PROJECTION_NEAR_M = 0.05f
private const val PROJECTION_FAR_M = 100f

/** Where each marker shows in a [viewport] of the latest [frame]; null when it is not in view. */
private fun screenPoints(frame: Frame, markers: List<ScanMarker>, viewport: IntSize): List<ScreenPoint?> {
    val projection = FloatArray(MATRIX_FLOATS).also {
        frame.camera.getProjectionMatrix(it, 0, PROJECTION_NEAR_M, PROJECTION_FAR_M)
    }
    val view = FloatArray(MATRIX_FLOATS).also { frame.camera.getViewMatrix(it, 0) }
    val viewProjection = multiplyMatrices(projection, view)
    return markers.map { marker ->
        if (marker.anchor.trackingState != TrackingState.TRACKING) return@map null
        val pose = marker.anchor.pose
        projectToView(
            viewProjection,
            Vec3(pose.tx(), pose.ty(), pose.tz()),
            viewport.width.toFloat(),
            viewport.height.toFloat()
        )
    }
}

/** Where points given in [anchor]'s space ([locals]) show in a [viewport] of the latest [frame]. */
private fun guideScreenPoints(frame: Frame, anchor: Anchor, locals: List<Vec3>, viewport: IntSize): List<ScreenPoint?> {
    if (anchor.trackingState != TrackingState.TRACKING) return locals.map { null }
    val viewProjection = viewProjection(frame)
    val pose = anchor.pose
    return locals.map { local ->
        val world = pose.transformPoint(floatArrayOf(local.x, local.y, local.z))
        projectToView(
            viewProjection,
            Vec3(world[0], world[1], world[2]),
            viewport.width.toFloat(),
            viewport.height.toFloat()
        )
    }
}

private fun viewProjection(frame: Frame): FloatArray {
    val projection = FloatArray(MATRIX_FLOATS).also {
        frame.camera.getProjectionMatrix(it, 0, PROJECTION_NEAR_M, PROJECTION_FAR_M)
    }
    val view = FloatArray(MATRIX_FLOATS).also { frame.camera.getViewMatrix(it, 0) }
    return multiplyMatrices(projection, view)
}

private const val MATRIX_FLOATS = 16

/**
 * The ARCore camera view. [planeOnly]: guide pins need a measured surface (a plane, or a
 * depth or feature point with its normal), so instant placement is used only for the free arrow.
 */
@Composable
internal fun ArCameraView(
    placement: ArPlacement,
    state: ArGuideUiState,
    planeOnly: Boolean,
    events: ArCameraEvents
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    // The gesture listener is remembered: read the latest callbacks through these.
    val currentEvents by rememberUpdatedState(events)
    val currentPlaneOnly by rememberUpdatedState(planeOnly)
    val groups = remember(state.steps, state.photoAspect) { pinGroups(state.steps, state.photoAspect) }
    val currentGroups by rememberUpdatedState(groups)
    val context = LocalContext.current
    val engine = rememberEngine()
    val cameraNode = rememberARCameraNode(engine)
    val placing = !state.placed || state.needsSecondPoint

    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it },
            engine = engine,
            cameraNode = cameraNode,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            // Depth (where the phone supports it) places scan labels on fronts with no plane:
            // glossy steel, plain white panels. SceneView turns it off on phones without depth.
            depthMode = Config.DepthMode.AUTOMATIC,
            instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP,
            planeRenderer = placing,
            onSessionFailure = { failure -> currentEvents.onSessionFailed(failure.toProblem()) },
            onTrackingFailureChanged = { reason -> currentEvents.onTrackingIssue(reason.toIssue()) },
            onSessionUpdated = { session, frame ->
                placement.meter.onFrame(System.nanoTime())
                latestFrame = frame
                placement.session = session
                placement.cameraPose = frame.camera.pose
                placement.captureRequest?.let { deliver ->
                    // Depth is what puts a point at the right distance on a front with no plane:
                    // give ARCore a moment to have it (it needs a little movement first).
                    val now = System.nanoTime()
                    val since = placement.captureSince ?: now.also { placement.captureSince = it }
                    val waitForDepth = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC) &&
                        now - since < DEPTH_WAIT_NS && !hasDepth(frame)
                    if (!waitForDepth) {
                        placement.captureRequest = null
                        placement.captureSince = null
                        deliver(captureFrame(frame, context.cameraUprightRotation(session)))
                    }
                }
                if (placement.scanMarkers.isNotEmpty() && viewport != IntSize.Zero) {
                    placement.scanScreenPoints = screenPoints(frame, placement.scanMarkers, viewport)
                }
                val guideAnchor = placement.anchor
                if (guideAnchor != null && viewport != IntSize.Zero) {
                    val mapper = placement.mapper
                    val locals = if (mapper != null) {
                        currentGroups.map { mapper.localPosition(it.pin) }
                    } else {
                        listOf(Vec3(0f, 0f, 0f))
                    }
                    placement.guideScreenPoints = guideScreenPoints(frame, guideAnchor, locals, viewport)
                }
                val tracking = frame.camera.trackingState == TrackingState.TRACKING
                currentEvents.onCameraTracking(tracking)
                val hit = if (tracking && viewport != IntSize.Zero) {
                    surfaceHit(frame, viewport.width / 2f, viewport.height / 2f, currentPlaneOnly)
                } else {
                    null
                }
                placement.centerHit = hit
                currentEvents.onSurfaceUnderReticle(hit != null)
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { event, node ->
                    // Tapping the camera view places the point there, like the Place button.
                    if (node != null) return@rememberOnGestureListener
                    val frame = latestFrame ?: return@rememberOnGestureListener
                    currentEvents.onTap(surfaceHit(frame, event.x, event.y, currentPlaneOnly))
                }
            )
        ) {
            val arrowMaterial = remember(materialLoader) { materialLoader.createColorInstance(OverlayBrand) }
            val pinMaterial = remember(materialLoader) { materialLoader.createColorInstance(OnOverlay) }
            val reticleMaterial = remember(materialLoader) {
                materialLoader.createUnlitColorInstance(OnOverlay.copy(alpha = 0.7f))
            }

            if (placing) {
                // Reticle: shows where Place will put the point.
                HitResultNode(hitTest = { placement.centerHit }) {
                    CylinderNode(radius = RETICLE_RADIUS, height = RETICLE_HEIGHT, materialInstance = reticleMaterial)
                }
            }

            placement.scanMarkers.forEach { marker ->
                // The dot is 3D, anchored on the control; its label is drawn flat on screen
                // above it (ScanLabelsOverlay): always sharp, the same size, never adrift.
                key(marker) {
                    AnchorNode(anchor = marker.anchor, apply = { isPositionEditable = false }) {
                        SphereNode(radius = SCAN_DOT_RADIUS, materialInstance = arrowMaterial)
                    }
                }
            }

            placement.anchor?.let { anchor ->
                AnchorNode(
                    anchor = anchor,
                    onTrackingStateChanged = { currentEvents.onAnchorTracking(it == TrackingState.TRACKING) }
                ) {
                    val mapper = placement.mapper
                    if (mapper != null) {
                        GuidePins(state, groups, mapper, current = arrowMaterial, other = pinMaterial)
                    } else {
                        // The free point, or the first alignment point while the second is missing.
                        SphereNode(radius = CURRENT_PIN_RADIUS, materialInstance = arrowMaterial)
                    }
                }
            }
        }
        if (state.scanMode) {
            ScanLabelsOverlay(
                markers = placement.scanMarkers,
                points = placement.scanScreenPoints,
                selected = state.selectedScan,
                onSelect = { currentEvents.onSelectScan(it) }
            )
        } else if (placement.anchor != null) {
            ArLabelsOverlay(
                guideLabels(state, groups, placement.mapper != null, placement.guideScreenPoints) {
                    currentEvents.onSelectStep(it)
                }
            )
        }
    }
}

/** A dot on every control of the guide; the current step's is bigger and brand blue. */
@Composable
private fun SceneScope.GuidePins(
    state: ArGuideUiState,
    groups: List<PinGroup>,
    mapper: PinMapper,
    current: MaterialInstance,
    other: MaterialInstance
) {
    groups.forEach { group ->
        key(group.pin) {
            val isCurrent = state.stepIndex in group.stepIndices
            SphereNode(
                radius = if (isCurrent) CURRENT_PIN_RADIUS else PIN_RADIUS,
                materialInstance = if (isCurrent) current else other,
                position = mapper.localPosition(group.pin).toPosition()
            )
        }
    }
}

/**
 * The guide's labels: one per control with its step numbers, the current step's with
 * what to do. Before the pins are aligned only the anchored point has one (the first
 * alignment step, or the current step for the free point). Tapping a label opens its
 * step (the next of its steps when it is already current).
 */
private fun guideLabels(
    state: ArGuideUiState,
    groups: List<PinGroup>,
    aligned: Boolean,
    points: List<ScreenPoint?>,
    onSelectStep: (Int) -> Unit
): List<OverlayLabel> {
    if (!aligned) {
        val point = points.firstOrNull() ?: return emptyList()
        val index = (state.plan as? AlignPlan.TwoPoints)?.first ?: state.stepIndex
        val step = state.steps.getOrNull(index) ?: return emptyList()
        return listOf(OverlayLabel("anchor", point, "${step.number}", step.markerText(), true) {})
    }
    return groups.mapIndexedNotNull { i, group ->
        val point = points.getOrNull(i) ?: return@mapIndexedNotNull null
        val isCurrent = state.stepIndex in group.stepIndices
        val shown = if (isCurrent) state.stepIndex else group.stepIndices.first()
        OverlayLabel(
            key = group.pin,
            point = point,
            badge = group.label(state.steps),
            text = state.steps.getOrNull(shown)?.markerText().orEmpty(),
            highlighted = isCurrent,
            onClick = {
                val position = group.stepIndices.indexOf(state.stepIndex)
                onSelectStep(group.stepIndices[(position + 1) % group.stepIndices.size])
            }
        )
    }
}

private fun Vec3.toPosition() = Position(x, y, z)

/**
 * A tracked plane under the point if there is one; else a surface ARCore measured there
 * with its normal (a depth point, or a feature point with an estimated normal: appliance
 * fronts, which rarely get a plane); otherwise, unless [planeOnly], an instant-placement
 * point about an arm's length away, so the free arrow can be placed before a surface is
 * found. Guide pins ([planeOnly]) need a surface with a normal to lie on.
 */
private fun surfaceHit(frame: Frame, x: Float, y: Float, planeOnly: Boolean): HitResult? {
    val hits = frame.hitTest(x, y)
    val planeHit = placementHit(frame, hits.filter { it.trackable is Plane }, instantPlacement = false)
    if (planeHit != null) return planeHit
    val measured = hits.firstOrNull { it.isSurfaceWithNormal() }
    if (measured != null || planeOnly) return measured
    return placementHit(
        frame,
        frame.hitTestInstantPlacement(x, y, INSTANT_PLACEMENT_DISTANCE_M),
        instantPlacement = true
    )
}

private fun HitResult.isSurfaceWithNormal(): Boolean {
    val trackable = trackable
    if (trackable.trackingState != TrackingState.TRACKING) return false
    return trackable is DepthPoint ||
        (trackable is Point && trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
}

private fun ARSessionFailure.toProblem(): ArSessionProblem = when (this) {
    is ARSessionFailure.UserDeclinedInstall,
    is ARSessionFailure.ArCoreNotInstalled,
    is ARSessionFailure.ApkTooOld -> ArSessionProblem.INSTALL_DECLINED
    is ARSessionFailure.SdkTooOld,
    is ARSessionFailure.DeviceNotCompatible,
    is ARSessionFailure.SessionUnsupported,
    is ARSessionFailure.UnsupportedConfiguration -> ArSessionProblem.NOT_COMPATIBLE
    is ARSessionFailure.CameraNotAvailable -> ArSessionProblem.CAMERA_UNAVAILABLE
    else -> ArSessionProblem.OTHER
}

private fun TrackingFailureReason?.toIssue(): ArTrackingIssue = when (this) {
    TrackingFailureReason.INSUFFICIENT_LIGHT -> ArTrackingIssue.INSUFFICIENT_LIGHT
    TrackingFailureReason.EXCESSIVE_MOTION -> ArTrackingIssue.EXCESSIVE_MOTION
    TrackingFailureReason.INSUFFICIENT_FEATURES -> ArTrackingIssue.INSUFFICIENT_FEATURES
    TrackingFailureReason.CAMERA_UNAVAILABLE -> ArTrackingIssue.CAMERA_UNAVAILABLE
    TrackingFailureReason.BAD_STATE -> ArTrackingIssue.BAD_STATE
    TrackingFailureReason.NONE, null -> ArTrackingIssue.NONE
}
