package com.hodowo.ui

import com.hodowo.ui.theme.OnOverlay
import com.hodowo.ui.theme.OverlayScrim
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hodowo.BuildConfig
import com.hodowo.R
import com.hodowo.ui.theme.Primary
import com.hodowo.viewmodel.AlignPlan
import com.hodowo.viewmodel.ArCameraPermission
import com.hodowo.viewmodel.ArGuidePhase
import com.hodowo.viewmodel.ArGuideUiState
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.ScanLabel
import com.hodowo.viewmodel.ScanStatus
import kotlinx.coroutines.delay
import java.util.Locale

private const val STEP_TEXT_MAX_HEIGHT_DP = 140
private const val PERF_REFRESH_MS = 1000L
private const val PERF_LOG_EVERY = 5
private const val PERF_TAG = "ArPerf"

/** A drag on the handle longer than this (px) opens or closes the panel. */
private const val HANDLE_DRAG_THRESHOLD = 24f

/** Everything the bottom panel can do. */
internal class ArPanelActions(
    val onAllowCamera: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onPlace: () -> Unit,
    val onMoveArrow: () -> Unit,
    val onReset: () -> Unit,
    val onOpenGuides: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onFindAutomatically: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onSelectScan: (Int) -> Unit = {}
)

/** One button of the panel. */
private class PanelAction(val label: String, val enabled: Boolean = true, val icon: ImageVector? = null, val onClick: () -> Unit)

@Composable
internal fun ArTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(containerColor = OverlayScrim)
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ar_close), tint = OnOverlay)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.ar_title),
            color = OnOverlay,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(OverlayScrim, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .semantics { heading() }
        )
    }
}

/**
 * The AR panel: a handle to lower or raise it (tap or drag), the status line, the main
 * button and, when raised, the details (scanned controls, guide step, other actions).
 * Lowered, it leaves almost the whole camera view free. Opaque theme colours keep it
 * readable in bright light; theme text styles follow the user's font size.
 */
@Composable
internal fun ArBottomPanel(state: ArGuideUiState, maxHeight: Dp, modifier: Modifier, actions: ArPanelActions) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val (primary, secondary) = panelActions(state, actions)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .animateContentSize(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SheetHandle(expanded = expanded, onExpandedChange = { expanded = it })
            Text(
                text = state.message.text(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            // Guide navigation stays visible when the panel is lowered: it is what the user does most.
            if (state.currentStep != null) StepNavigation(state, actions)
            primary?.let { PrimaryAction(it) }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.scanMode && state.scanDevice != null) ScanList(state, actions.onSelectScan)
                    state.currentStep?.let { StepCard(step = it, position = state.stepIndex + 1, total = state.steps.size) }
                    secondary.forEach { SecondaryAction(it) }
                }
            }
        }
    }
}

/** The bar at the top of the panel: tap it or drag it down / up to lower / raise the panel. */
@Composable
private fun SheetHandle(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    var dragged by remember { mutableFloatStateOf(0f) }
    val label = stringResource(if (expanded) R.string.ar_hide_details else R.string.ar_show_details)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clickable(onClickLabel = label, role = Role.Button) { onExpandedChange(!expanded) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> dragged += delta },
                onDragStopped = {
                    if (dragged > HANDLE_DRAG_THRESHOLD) onExpandedChange(false)
                    if (dragged < -HANDLE_DRAG_THRESHOLD) onExpandedChange(true)
                    dragged = 0f
                }
            )
            .semantics { contentDescription = label }
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
    }
}

/** The main button and the others, for the current state. */
@Composable
private fun panelActions(state: ArGuideUiState, actions: ArPanelActions): Pair<PanelAction?, List<PanelAction>> {
    val openGuide = PanelAction(stringResource(R.string.ar_open_2d_guide), onClick = actions.onOpenGuides)
    val reset = PanelAction(stringResource(R.string.ar_reset), onClick = actions.onReset)
    val scanPhases = setOf(ArGuidePhase.SCANNING, ArGuidePhase.READY_TO_PLACE, ArGuidePhase.PLACED)
    if (state.scanMode && state.phase in scanPhases) {
        // AR tab: the scan is the only action, no arrow to place.
        val label = when (state.scanStatus) {
            ScanStatus.SEARCHING -> R.string.ar_scanning
            ScanStatus.IDLE -> R.string.ar_scan
            else -> R.string.ar_scan_again
        }
        return PanelAction(stringResource(label), state.canScan, Icons.Filled.Refresh, actions.onScan) to listOf(reset)
    }
    val (primary, secondary) = when (state.phase) {
        ArGuidePhase.ASKING_PERMISSION -> null to emptyList()
        ArGuidePhase.PERMISSION_DENIED -> {
            val permanently = state.permission == ArCameraPermission.PERMANENTLY_DENIED
            PanelAction(
                stringResource(if (permanently) R.string.ar_open_settings else R.string.ar_allow_camera),
                onClick = if (permanently) actions.onOpenSettings else actions.onAllowCamera
            ) to listOf(PanelAction(stringResource(R.string.ar_use_2d_guide), onClick = actions.onOpenGuides))
        }
        ArGuidePhase.SESSION_ERROR ->
            PanelAction(stringResource(R.string.ar_try_again), onClick = actions.onReset) to
                listOf(PanelAction(stringResource(R.string.ar_use_2d_guide), onClick = actions.onOpenGuides))
        ArGuidePhase.SCANNING, ArGuidePhase.READY_TO_PLACE -> placeActions(state, actions)
        ArGuidePhase.PLACED ->
            PanelAction(state.recenterLabel.text(), onClick = actions.onMoveArrow) to listOf(reset)
        ArGuidePhase.TRACKING_LOST -> {
            val recenter = PanelAction(state.recenterLabel.text(), onClick = actions.onMoveArrow)
            if (state.placed) recenter to listOf(reset) else reset to emptyList()
        }
    }
    val withGuide = state.guided && state.phase != ArGuidePhase.PERMISSION_DENIED &&
        state.phase != ArGuidePhase.SESSION_ERROR
    // The 2D guide stays one tap away while following a guide in AR (#241).
    return primary to (if (withGuide) secondary + openGuide else secondary)
}

@Composable
private fun placeActions(state: ArGuideUiState, actions: ArPanelActions): Pair<PanelAction?, List<PanelAction>> {
    val place = PanelAction(state.placeLabel.text(), state.canPlace, onClick = actions.onPlace)
    if (state.autoAlignAvailable && state.plan is AlignPlan.TwoPoints && !state.placed) {
        // The AI finds both controls; aiming yourself stays one tap away.
        val label = if (state.searching) R.string.ar_searching else R.string.ar_find_automatically
        return PanelAction(stringResource(label), state.canAutoAlign, onClick = actions.onFindAutomatically) to listOf(place)
    }
    return place to emptyList()
}

/**
 * The device the scan recognised and its controls, numbered like the dots on the device.
 * Tapping a control shows its name on the device; tapping it again hides it.
 */
@Composable
private fun ScanList(state: ArGuideUiState, onSelect: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = state.scanDevice.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .semantics { heading() }
            )
            state.scanLabels.forEachIndexed { index, control ->
                ScanRow(number = index + 1, control = control, selected = state.selectedScan == index) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun ScanRow(number: Int, control: ScanLabel, selected: Boolean, onClick: () -> Unit) {
    val selectedText = stringResource(R.string.ar_control_selected)
    val outOfView = stringResource(R.string.ar_not_in_view)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = control.placed, role = Role.Button, onClick = onClick)
            .background(if (selected) Primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                this.selected = selected
                if (selected) stateDescription = selectedText
            }
    ) {
        NumberBadge(number = number, filled = selected, dimmed = !control.placed)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = control.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (control.description.isNotBlank()) {
                Text(
                    text = control.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!control.placed) {
                Text(
                    text = outOfView,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The number of a control, as on its dot in AR: filled when its name is shown there. */
@Composable
private fun NumberBadge(number: Int, filled: Boolean, dimmed: Boolean) {
    val colour = if (dimmed) MaterialTheme.colorScheme.outline else Primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .background(if (filled) colour else Color.Transparent, CircleShape)
            .then(if (filled) Modifier else Modifier.background(colour.copy(alpha = 0.15f), CircleShape))
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else colour
        )
    }
}

@Composable
private fun StepCard(step: ManualStep, position: Int, total: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.ar_step_header, step.number, position, total),
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (step.description.isNotBlank()) {
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(max = STEP_TEXT_MAX_HEIGHT_DP.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun StepNavigation(state: ArGuideUiState, actions: ArPanelActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = actions.onPrevious,
            enabled = state.canGoBack,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.ar_previous))
        }
        OutlinedButton(
            onClick = actions.onNext,
            enabled = state.canGoForward,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.ar_next))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PrimaryAction(action: PanelAction) {
    Button(
        onClick = action.onClick,
        enabled = action.enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Primary),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        action.icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(action.label)
    }
}

@Composable
private fun SecondaryAction(action: PanelAction) {
    OutlinedButton(
        onClick = action.onClick,
        enabled = action.enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Text(action.label) }
}

/**
 * Debug builds only (#241): frame time and thermal state of the AR view, on screen
 * and every few seconds in Logcat (tag "ArPerf"), for the performance table.
 */
@Composable
internal fun ArPerformanceOverlay(placement: ArPlacement, modifier: Modifier) {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(placement) {
        var ticks = 0
        while (true) {
            delay(PERF_REFRESH_MS)
            val stats = placement.meter.stats() ?: continue
            text = String.format(
                Locale.US,
                "%.1f ms avg · %.0f ms worst · %d fps · thermal %s",
                stats.averageMs,
                stats.worstMs,
                stats.fps,
                context.thermalStatusName()
            )
            ticks++
            if (ticks % PERF_LOG_EVERY == 0) Log.i(PERF_TAG, text)
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = OnOverlay,
            style = MaterialTheme.typography.labelSmall,
            modifier = modifier
                .statusBarsPadding()
                .padding(top = 56.dp, end = 8.dp)
                .background(OverlayScrim, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun Context.thermalStatusName(): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "n/a"
    val status = getSystemService(PowerManager::class.java)?.currentThermalStatus ?: return "n/a"
    return when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> "unknown"
    }
}
