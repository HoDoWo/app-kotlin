package com.hodowo.ui

import com.hodowo.ui.theme.MediaBackground
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import coil.request.ImageRequest
import com.google.ar.core.HitResult
import com.hodowo.data.AiServiceProvider
import com.hodowo.viewmodel.AlignPlan
import com.hodowo.viewmodel.ArCameraPermission
import com.hodowo.viewmodel.ArGuideUiState
import com.hodowo.viewmodel.ArGuideViewModel
import com.hodowo.viewmodel.ManualStep
import kotlinx.coroutines.launch
import java.io.IOException

/** The bottom panel never covers more than this share of the screen, also at large font sizes. */
private const val PANEL_MAX_SCREEN_SHARE = 0.55f

/** Size of the photo loaded only to read its aspect ratio. */
private const val ASPECT_PROBE_SIZE = 256

/**
 * The AR screen (#240, #241): camera permission, ARCore session, plane detection
 * and the guide on the appliance.
 *
 * Without [steps] (the AR tab) the user anchors one arrow. With the [steps] of a
 * repair guide the user aims at two controls of the photo; every numbered pin of
 * the guide is then placed on the appliance, the current step gets the arrow, and
 * Previous / Next walk through the steps without leaving AR. [onStepChange] keeps
 * the 2D guide on the same step.
 *
 * The session follows the screen lifecycle (it pauses in the background and resumes
 * after). All controls sit outside the camera view, so they work with TalkBack, and the
 * status line is a live region that announces every change of state.
 */
@Composable
fun ArGuideScreen(
    onClose: () -> Unit,
    onOpenGuides: () -> Unit,
    steps: List<ManualStep> = emptyList(),
    initialStepIndex: Int = 0,
    photoUrl: String? = null,
    applianceName: String? = null,
    onStepChange: (Int) -> Unit = {},
    viewModel: ArGuideViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Null without a backend URL: then only manual aiming is offered.
    val locateService = remember { AiServiceProvider.guideLocateService }
    val scanService = remember { AiServiceProvider.arScanService }

    BackHandler(onBack = onClose)
    KeepScreenOn()

    // Fresh on every visit: an anchor of the previous visit no longer exists.
    LaunchedEffect(Unit) {
        viewModel.start(
            steps,
            initialStepIndex,
            autoAlignAvailable = locateService != null,
            scanAvailable = scanService != null
        )
    }
    PhotoAspect(photoUrl, viewModel::onPhotoAspect)
    val stepChange by rememberUpdatedState(onStepChange)
    LaunchedEffect(state.stepIndex, state.guided) {
        if (state.guided) stepChange(state.stepIndex)
    }

    val askCamera = rememberCameraPermission(viewModel)

    // One placement per session: rebuilt (and its anchor detached) on every reset.
    val placement = remember(state.sessionKey) { ArPlacement() }
    DisposableEffect(placement) {
        onDispose {
            placement.clear()
            placement.clearScan()
        }
    }

    // Read through LocalConfiguration so the labels follow an in-app language change.
    val scanLanguage = rememberUpdatedState(currentLocale().language)
    val place: (HitResult?) -> Unit = { hit -> placeOrAlign(placement, hit, viewModel.state.value, viewModel) }
    // AR tab: name what the camera sees and label its main controls; the first scan starts by itself.
    val scan: () -> Unit = {
        if (scanService != null && viewModel.state.value.canScan) {
            viewModel.onScanStarted()
            placement.captureRequest = { captured ->
                scope.launch {
                    try {
                        val outcome = runArScan(captured, placement, scanService, scanLanguage.value)
                        viewModel.onScanFinished(outcome.device, outcome.labels)
                    } catch (e: IOException) {
                        viewModel.onScanError()
                    }
                }
            }
        }
    }
    LaunchedEffect(state.shouldAutoScan) { if (state.shouldAutoScan) scan() }
    val findAutomatically: () -> Unit = {
        if (locateService != null && viewModel.state.value.canAutoAlign) {
            viewModel.onAutoAlignStarted()
            placement.captureRequest = { captured ->
                scope.launch {
                    val reference = photoUrl?.let { loadReferenceJpeg(context, it) }
                    val result = runAutoAlign(
                        captured, placement, viewModel.state.value, locateService, reference, applianceName
                    )
                    viewModel.onAutoAlignFinished(result)
                }
            }
        }
    }
    val needsPlane = state.plan != AlignPlan.Free

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MediaBackground)
    ) {
        if (state.showsCamera) {
            key(state.sessionKey) {
                ArCameraView(
                    placement = placement,
                    state = state,
                    planeOnly = needsPlane,
                    events = ArCameraEvents(
                        onCameraTracking = viewModel::onCameraTracking,
                        onTrackingIssue = viewModel::onTrackingIssue,
                        onSurfaceUnderReticle = viewModel::onSurfaceUnderReticle,
                        onTap = place,
                        onAnchorTracking = viewModel::onAnchorTracking,
                        onSessionFailed = viewModel::onSessionFailed,
                        onSelectScan = viewModel::selectScan
                    )
                )
            }
            ArPerformanceOverlay(placement = placement, modifier = Modifier.align(Alignment.TopEnd))
        }

        ArTopBar(onClose = onClose)

        ArBottomPanel(
            state = state,
            maxHeight = maxHeight * PANEL_MAX_SCREEN_SHARE,
            modifier = Modifier.align(Alignment.BottomCenter),
            actions = ArPanelActions(
                onAllowCamera = askCamera,
                onOpenSettings = { context.openAppSettings() },
                onPlace = { place(placement.centerHit) },
                onMoveArrow = {
                    placement.clear()
                    viewModel.moveArrow()
                },
                onReset = viewModel::resetSession,
                onOpenGuides = onOpenGuides,
                onPrevious = viewModel::previousStep,
                onNext = viewModel::nextStep,
                onFindAutomatically = findAutomatically,
                onScan = scan,
                onSelectScan = viewModel::selectScan
            )
        )
    }
}

/**
 * Places the first point (the anchor) or, while aligning the guide, the second
 * point; tells the ViewModel what happened.
 */
private fun placeOrAlign(placement: ArPlacement, hit: HitResult?, state: ArGuideUiState, viewModel: ArGuideViewModel) {
    // Placed and done (or tracking lost): Move arrow / Re-align first. Not while the AI searches.
    // In the AR tab scan there is no arrow to place: a tap on the camera does nothing.
    if (state.scanMode || state.searching || (state.placed && !state.needsSecondPoint)) return
    val plan = state.plan
    if (state.needsSecondPoint && plan is AlignPlan.TwoPoints) {
        val first = state.steps[plan.first].pin
        val second = state.steps[plan.second].pin
        if (first == null || second == null) return
        if (placement.alignSecond(hit, first, second, state.photoAspect)) {
            viewModel.onArrowPlaced()
        } else {
            viewModel.onAlignmentFailed()
        }
        return
    }
    if (!placement.place(hit)) return
    if (plan is AlignPlan.OnePoint) state.steps[plan.first].pin?.let(placement::alignSingle)
    viewModel.onArrowPlaced()
}

/** Asks for the camera on the first visit and picks up a permission granted in Settings. */
@Composable
private fun rememberCameraPermission(viewModel: ArGuideViewModel): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        viewModel.onPermissionResult(granted, permanentlyDenied)
    }
    LaunchedEffect(Unit) {
        if (context.hasCameraPermission()) {
            viewModel.onPermissionResult(granted = true)
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    val lifecycleOwner = activity as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                viewModel.state.value.permission != ArCameraPermission.GRANTED &&
                context.hasCameraPermission()
            ) {
                viewModel.onPermissionResult(granted = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return { launcher.launch(Manifest.permission.CAMERA) }
}

/** Reads the guide photo's height / width once: pins are in photo coordinates. */
@Composable
private fun PhotoAspect(url: String?, onAspect: (Float) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(ASPECT_PROBE_SIZE).build()
        val drawable = context.imageLoader.execute(request).drawable ?: return@LaunchedEffect
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            onAspect(drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth)
        }
    }
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
