package com.hodowo.ui

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
import androidx.compose.ui.graphics.toArgb
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
import com.hodowo.ui.theme.Primary
import com.hodowo.viewmodel.AlignPlan
import com.hodowo.viewmodel.ArGuideUiState
import com.hodowo.viewmodel.ArSessionProblem
import com.hodowo.viewmodel.ArTrackingIssue
import com.hodowo.viewmodel.PinMapper
import com.hodowo.viewmodel.StepPin
import com.hodowo.viewmodel.ScreenPoint
import com.hodowo.viewmodel.Vec3
import com.hodowo.viewmodel.multiplyMatrices
import com.hodowo.viewmodel.projectToView
import com.hodowo.viewmodel.label
import com.hodowo.viewmodel.pinGroups
import io.github.sceneview.SceneScope
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.ARSessionFailure
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.placementHit
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberOnGestureListener

/** Distance used for instant placement before ARCore has found a plane: about an arm's length. */
private const val INSTANT_PLACEMENT_DISTANCE_M = 0.6f

/** Arrow size in meters: big enough to see from a step back, small enough for a single button. */
private const val RING_RADIUS = 0.03f
private const val RING_HEIGHT = 0.003f
private const val CONE_RADIUS = 0.022f
private const val CONE_HEIGHT = 0.05f
private const val SHAFT_RADIUS = 0.007f
private const val SHAFT_HEIGHT = 0.07f
private const val ARROW_GAP = 0.004f
private const val RETICLE_RADIUS = 0.035f
private const val RETICLE_HEIGHT = 0.002f
private const val FLIP_DEGREES = 180f

/** Guide pins (#241): a dot on each control and its step numbers just in front of it. */
private const val PIN_RADIUS = 0.008f
private const val CURRENT_PIN_RADIUS = 0.012f
private const val LABEL_LIFT = 0.04f
private const val LABEL_HEIGHT = 0.035f
private const val LABEL_WIDTH_PER_CHAR = 0.016f
private const val LABEL_MIN_WIDTH = 0.035f
private const val LABEL_FONT_PX = 72f

/** AR tab scan: a small dot on each control, smaller than guide pins (keys are small). */
private const val SCAN_DOT_RADIUS = 0.005f

/** Near-black behind white digits: readable on a white appliance in daylight. */
private const val LABEL_BACKGROUND = 0xE6000000.toInt()

/** What the AR view reports while it runs. */
internal class ArCameraEvents(
    val onCameraTracking: (Boolean) -> Unit,
    val onTrackingIssue: (ArTrackingIssue) -> Unit,
    val onSurfaceUnderReticle: (Boolean) -> Unit,
    val onTap: (HitResult?) -> Unit,
    val onAnchorTracking: (Boolean) -> Unit,
    val onSessionFailed: (ArSessionProblem) -> Unit,
    val onSelectScan: (Int) -> Unit = {}
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
            val arrowMaterial = remember(materialLoader) { materialLoader.createColorInstance(Primary) }
            val pinMaterial = remember(materialLoader) { materialLoader.createColorInstance(Color.White) }
            val reticleMaterial = remember(materialLoader) {
                materialLoader.createUnlitColorInstance(Color.White.copy(alpha = 0.7f))
            }
            val cameraPosition: () -> Position = { cameraNode.worldPosition }

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
                    val materials = PinMaterials(arrowMaterial, pinMaterial, cameraPosition)
                    when {
                        state.plan == AlignPlan.Free -> Arrow(arrowMaterial, origin = Vec3(0f, 0f, 0f), side = 1f)
                        mapper != null -> GuidePins(state, mapper, materials)
                        else -> FirstPointMarker(state, materials)
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
        }
    }
}

private class PinMaterials(
    val current: MaterialInstance,
    val other: MaterialInstance,
    val cameraPosition: () -> Position
)

/** Every control of the guide on the appliance; the current step's gets the arrow. */
@Composable
private fun SceneScope.GuidePins(state: ArGuideUiState, mapper: PinMapper, materials: PinMaterials) {
    val groups = remember(state.steps, state.photoAspect) { pinGroups(state.steps, state.photoAspect) }
    groups.forEach { group ->
        key(group.pin) {
            val current = state.stepIndex in group.stepIndices
            val at = mapper.localPosition(group.pin)
            SphereNode(
                radius = if (current) CURRENT_PIN_RADIUS else PIN_RADIUS,
                materialInstance = if (current) materials.current else materials.other,
                position = at.toPosition()
            )
            PinLabel(
                text = group.label(state.steps),
                current = current,
                position = mapper.localPosition(group.pin, LABEL_LIFT).toPosition(),
                cameraPosition = materials.cameraPosition
            )
            if (current) Arrow(materials.current, origin = at, side = mapper.side)
        }
    }
}

/** While the second point is missing: mark where the first one went. */
@Composable
private fun SceneScope.FirstPointMarker(state: ArGuideUiState, materials: PinMaterials) {
    val plan = state.plan as? AlignPlan.TwoPoints ?: return
    SphereNode(radius = CURRENT_PIN_RADIUS, materialInstance = materials.current)
    PinLabel(
        text = state.steps.getOrNull(plan.first)?.number?.toString().orEmpty(),
        current = true,
        position = Position(y = LABEL_LIFT),
        cameraPosition = materials.cameraPosition
    )
}

/** Step numbers that always face the camera, white on near-black (on brand blue when current). */
@Composable
private fun SceneScope.PinLabel(text: String, current: Boolean, position: Position, cameraPosition: () -> Position) {
    ArTextNode(
        text = text,
        fontSize = LABEL_FONT_PX,
        textColor = android.graphics.Color.WHITE,
        backgroundColor = if (current) Primary.toArgb() else LABEL_BACKGROUND,
        widthMeters = maxOf(LABEL_MIN_WIDTH, LABEL_WIDTH_PER_CHAR * text.length),
        heightMeters = LABEL_HEIGHT,
        position = position,
        cameraPosition = cameraPosition
    )
}

/**
 * A ring on the surface at [origin] and an arrow pointing into it. [side] is the
 * side of the surface the camera is on (+1 along the anchor's +Y normal).
 */
@Composable
private fun SceneScope.Arrow(material: MaterialInstance, origin: Vec3, side: Float) {
    fun along(distance: Float) = Position(origin.x, origin.y + side * distance, origin.z)
    CylinderNode(radius = RING_RADIUS, height = RING_HEIGHT, materialInstance = material, position = along(0f))
    ConeNode(
        radius = CONE_RADIUS,
        height = CONE_HEIGHT,
        materialInstance = material,
        position = along(ARROW_GAP + CONE_HEIGHT / 2),
        // The cone tip is +Y: flip it to point into the surface when the camera is on +Y.
        rotation = Rotation(x = if (side > 0f) FLIP_DEGREES else 0f)
    )
    CylinderNode(
        radius = SHAFT_RADIUS,
        height = SHAFT_HEIGHT,
        materialInstance = material,
        position = along(ARROW_GAP + CONE_HEIGHT + SHAFT_HEIGHT / 2)
    )
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
