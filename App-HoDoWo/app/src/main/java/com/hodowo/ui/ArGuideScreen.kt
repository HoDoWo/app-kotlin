package com.hodowo.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.hodowo.ui.theme.Primary
import com.hodowo.viewmodel.ArCameraPermission
import com.hodowo.viewmodel.ArGuidePhase
import com.hodowo.viewmodel.ArGuideUiState
import com.hodowo.viewmodel.ArGuideViewModel
import com.hodowo.viewmodel.ArSessionProblem
import com.hodowo.viewmodel.ArTrackingIssue
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.ARSessionFailure
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.placementHit
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
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

/**
 * The AR screen (#240): camera permission, ARCore session, plane detection and one
 * arrow anchored on the appliance. The guide steps on the arrow come with #241.
 *
 * The session follows the screen lifecycle (it pauses in the background and resumes
 * after). All controls sit outside the camera view, so they work with TalkBack, and the
 * status line is a live region that announces every change of state.
 */
@Composable
fun ArGuideScreen(
    onClose: () -> Unit,
    onOpenGuides: () -> Unit,
    viewModel: ArGuideViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    BackHandler(onBack = onClose)
    KeepScreenOn()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        viewModel.onPermissionResult(granted, permanentlyDenied)
    }

    // First visit: use the permission if it is there, otherwise ask once.
    LaunchedEffect(Unit) {
        if (context.hasCameraPermission()) {
            viewModel.onPermissionResult(granted = true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Back from Settings: pick up a permission granted there.
    val lifecycleOwner = activity as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                state.permission != ArCameraPermission.GRANTED &&
                context.hasCameraPermission()
            ) {
                viewModel.onPermissionResult(granted = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // One placement per session: rebuilt (and its anchor detached) on every reset.
    val placement = remember(state.sessionKey) { ArPlacement() }
    DisposableEffect(placement) { onDispose { placement.clear() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.showsCamera) {
            key(state.sessionKey) {
                ArCameraView(
                    placement = placement,
                    arrowPlaced = state.placed,
                    onCameraTracking = viewModel::onCameraTracking,
                    onTrackingIssue = viewModel::onTrackingIssue,
                    onSurfaceUnderReticle = viewModel::onSurfaceUnderReticle,
                    onTapPlaced = viewModel::onArrowPlaced,
                    onAnchorTracking = viewModel::onAnchorTracking,
                    onSessionFailed = viewModel::onSessionFailed
                )
            }
        }

        ArTopBar(onClose = onClose)

        ArBottomPanel(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter),
            onAllowCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
            onPlace = {
                if (placement.place(placement.centerHit)) viewModel.onArrowPlaced()
            },
            onMoveArrow = {
                placement.clear()
                viewModel.moveArrow()
            },
            onReset = viewModel::resetSession,
            onOpenGuides = onOpenGuides
        )
    }
}

/**
 * The arrow anchor and the surface currently under the reticle. ARCore frames and
 * button taps both arrive on the main thread, so no locking is needed.
 */
private class ArPlacement {
    /** Latest surface hit at the screen centre, refreshed every frame. */
    var centerHit: HitResult? = null
    var anchor by mutableStateOf<Anchor?>(null)
        private set

    /** Anchors the arrow on [hit]; false when ARCore cannot create an anchor there. */
    fun place(hit: HitResult?): Boolean {
        val created = hit?.createAnchorOrNull() ?: return false
        clear()
        anchor = created
        return true
    }

    /** Detaches the anchor, so ARCore stops tracking it and frees its resources. */
    fun clear() {
        anchor?.let { runCatching { it.detach() } }
        anchor = null
    }
}

@Composable
private fun ArCameraView(
    placement: ArPlacement,
    arrowPlaced: Boolean,
    onCameraTracking: (Boolean) -> Unit,
    onTrackingIssue: (ArTrackingIssue) -> Unit,
    onSurfaceUnderReticle: (Boolean) -> Unit,
    onTapPlaced: () -> Unit,
    onAnchorTracking: (Boolean) -> Unit,
    onSessionFailed: (ArSessionProblem) -> Unit
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }

    ARSceneView(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it },
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
        instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP,
        planeRenderer = !arrowPlaced,
        onSessionFailure = { failure -> onSessionFailed(failure.toProblem()) },
        onTrackingFailureChanged = { reason -> onTrackingIssue(reason.toIssue()) },
        onSessionUpdated = { _, frame ->
            latestFrame = frame
            val tracking = frame.camera.trackingState == TrackingState.TRACKING
            onCameraTracking(tracking)
            val hit = if (tracking && viewport != IntSize.Zero) {
                surfaceHit(frame, viewport.width / 2f, viewport.height / 2f)
            } else {
                null
            }
            placement.centerHit = hit
            onSurfaceUnderReticle(hit != null)
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { event, node ->
                // Tapping the camera view places the arrow there, like the Place arrow button.
                // Reads the placement itself: this listener is remembered, a parameter would be stale.
                if (node != null || placement.anchor != null) return@rememberOnGestureListener
                val frame = latestFrame ?: return@rememberOnGestureListener
                if (placement.place(surfaceHit(frame, event.x, event.y))) onTapPlaced()
            }
        )
    ) {
        val arrowMaterial = remember(materialLoader) { materialLoader.createColorInstance(Primary) }
        val reticleMaterial = remember(materialLoader) {
            materialLoader.createUnlitColorInstance(Color.White.copy(alpha = 0.7f))
        }

        if (!arrowPlaced) {
            // Reticle: shows where Place arrow will put the arrow.
            HitResultNode(hitTest = { placement.centerHit }) {
                CylinderNode(radius = RETICLE_RADIUS, height = RETICLE_HEIGHT, materialInstance = reticleMaterial)
            }
        }

        placement.anchor?.let { anchor ->
            AnchorNode(
                anchor = anchor,
                onTrackingStateChanged = { onAnchorTracking(it == TrackingState.TRACKING) }
            ) {
                // The anchor's +Y is the surface normal (out of the appliance front, or up on
                // a top): a ring on the surface and an arrow pointing into it.
                CylinderNode(radius = RING_RADIUS, height = RING_HEIGHT, materialInstance = arrowMaterial)
                ConeNode(
                    radius = CONE_RADIUS,
                    height = CONE_HEIGHT,
                    materialInstance = arrowMaterial,
                    position = Position(y = ARROW_GAP + CONE_HEIGHT / 2),
                    rotation = Rotation(x = FLIP_DEGREES)
                )
                CylinderNode(
                    radius = SHAFT_RADIUS,
                    height = SHAFT_HEIGHT,
                    materialInstance = arrowMaterial,
                    position = Position(y = ARROW_GAP + CONE_HEIGHT + SHAFT_HEIGHT / 2)
                )
            }
        }
    }
}

/**
 * A tracked plane under the point if there is one; otherwise an instant-placement point
 * about an arm's length away, so the arrow can be placed before a plane is found (glossy,
 * plain appliance fronts are slow to detect). It snaps to the real surface later.
 */
private fun surfaceHit(frame: Frame, x: Float, y: Float): HitResult? {
    val planeHit = placementHit(
        frame,
        frame.hitTest(x, y).filter { it.trackable is Plane },
        instantPlacement = false
    )
    return planeHit ?: placementHit(
        frame,
        frame.hitTestInstantPlacement(x, y, INSTANT_PLACEMENT_DISTANCE_M),
        instantPlacement = true
    )
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

@Composable
private fun ArTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close AR", tint = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "AR guide",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun ArBottomPanel(
    state: ArGuideUiState,
    modifier: Modifier,
    onAllowCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlace: () -> Unit,
    onMoveArrow: () -> Unit,
    onReset: () -> Unit,
    onOpenGuides: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(modifier = Modifier.height(4.dp))
            when (state.phase) {
                ArGuidePhase.ASKING_PERMISSION -> Unit
                ArGuidePhase.PERMISSION_DENIED -> {
                    if (state.permission == ArCameraPermission.PERMANENTLY_DENIED) {
                        PrimaryAction("Open Settings", onClick = onOpenSettings)
                    } else {
                        PrimaryAction("Allow camera", onClick = onAllowCamera)
                    }
                    SecondaryAction("Use the 2D guide", onClick = onOpenGuides)
                }
                ArGuidePhase.SESSION_ERROR -> {
                    PrimaryAction("Try again", onClick = onReset)
                    SecondaryAction("Use the 2D guide", onClick = onOpenGuides)
                }
                ArGuidePhase.SCANNING, ArGuidePhase.READY_TO_PLACE -> {
                    PrimaryAction("Place arrow", enabled = state.canPlace, onClick = onPlace)
                }
                ArGuidePhase.PLACED -> {
                    PrimaryAction("Move arrow", onClick = onMoveArrow)
                    SecondaryAction("Reset AR", onClick = onReset)
                }
                ArGuidePhase.TRACKING_LOST -> {
                    if (state.placed) PrimaryAction("Move arrow", onClick = onMoveArrow)
                    SecondaryAction("Reset AR", onClick = onReset)
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Text(label) }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Text(label) }
}

/** AR drains the battery anyway: keep the screen on only while this screen is shown. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
