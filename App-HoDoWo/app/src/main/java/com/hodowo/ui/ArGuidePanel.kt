package com.hodowo.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hodowo.BuildConfig
import com.hodowo.ui.theme.Primary
import com.hodowo.viewmodel.AlignPlan
import com.hodowo.viewmodel.ArCameraPermission
import com.hodowo.viewmodel.ArGuidePhase
import com.hodowo.viewmodel.ArGuideUiState
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.ScanStatus
import kotlinx.coroutines.delay
import java.util.Locale

private const val STEP_TEXT_MAX_HEIGHT_DP = 140
private const val PERF_REFRESH_MS = 1000L
private const val PERF_LOG_EVERY = 5
private const val PERF_TAG = "ArPerf"

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
    val onScan: () -> Unit = {}
)

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
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close AR", tint = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "AR guide",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .semantics { heading() }
        )
    }
}

/**
 * Status, the current step and the actions. Opaque theme colours keep it readable in
 * bright light; theme text styles follow the user's font size, and the panel scrolls
 * instead of covering the whole camera view.
 */
@Composable
internal fun ArBottomPanel(state: ArGuideUiState, maxHeight: Dp, modifier: Modifier, actions: ArPanelActions) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (state.scanMode && state.scanDevice != null) ScanCard(state)
            state.currentStep?.let { step ->
                StepCard(step = step, position = state.stepIndex + 1, total = state.steps.size)
                StepNavigation(state, actions)
            }
            PhaseActions(state, actions)
        }
    }
}

/** The device the AR tab scan named and its main controls, also readable by TalkBack. */
@Composable
private fun ScanCard(state: ArGuideUiState) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.scanDevice.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            state.scanLabels.forEach { control ->
                Text(
                    text = buildString {
                        append(control.label)
                        if (control.description.isNotBlank()) append(" — ").append(control.description)
                        if (!control.placed) append(" (not on screen)")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                text = "Step ${step.number} · $position of $total",
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
            Text("Previous")
        }
        OutlinedButton(
            onClick = actions.onNext,
            enabled = state.canGoForward,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text("Next")
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PhaseActions(state: ArGuideUiState, actions: ArPanelActions) {
    val scanPhases = setOf(ArGuidePhase.SCANNING, ArGuidePhase.READY_TO_PLACE, ArGuidePhase.PLACED)
    if (state.scanMode && state.phase in scanPhases) {
        // AR tab: the scan comes first; the free arrow stays available below it.
        PrimaryAction(
            label = when (state.scanStatus) {
                ScanStatus.SEARCHING -> "Scanning…"
                ScanStatus.IDLE -> "Scan"
                else -> "Scan again"
            },
            enabled = state.canScan,
            onClick = actions.onScan
        )
        if (state.phase == ArGuidePhase.PLACED) {
            SecondaryAction(state.recenterLabel, onClick = actions.onMoveArrow)
        } else {
            SecondaryAction(state.placeLabel, enabled = state.canPlace, onClick = actions.onPlace)
        }
        return
    }
    when (state.phase) {
        ArGuidePhase.ASKING_PERMISSION -> Unit
        ArGuidePhase.PERMISSION_DENIED -> {
            if (state.permission == ArCameraPermission.PERMANENTLY_DENIED) {
                PrimaryAction("Open Settings", onClick = actions.onOpenSettings)
            } else {
                PrimaryAction("Allow camera", onClick = actions.onAllowCamera)
            }
            SecondaryAction("Use the 2D guide", onClick = actions.onOpenGuides)
            return
        }
        ArGuidePhase.SESSION_ERROR -> {
            PrimaryAction("Try again", onClick = actions.onReset)
            SecondaryAction("Use the 2D guide", onClick = actions.onOpenGuides)
            return
        }
        ArGuidePhase.SCANNING, ArGuidePhase.READY_TO_PLACE -> {
            if (state.autoAlignAvailable && state.plan is AlignPlan.TwoPoints && !state.placed) {
                // The AI finds both controls; aiming yourself stays one tap away.
                PrimaryAction(
                    if (state.searching) "Searching…" else "Find automatically",
                    enabled = state.canAutoAlign,
                    onClick = actions.onFindAutomatically
                )
                SecondaryAction(state.placeLabel, enabled = state.canPlace, onClick = actions.onPlace)
            } else {
                PrimaryAction(state.placeLabel, enabled = state.canPlace, onClick = actions.onPlace)
            }
        }
        ArGuidePhase.PLACED -> {
            PrimaryAction(state.recenterLabel, onClick = actions.onMoveArrow)
            SecondaryAction("Reset AR", onClick = actions.onReset)
        }
        ArGuidePhase.TRACKING_LOST -> {
            if (state.placed) PrimaryAction(state.recenterLabel, onClick = actions.onMoveArrow)
            SecondaryAction("Reset AR", onClick = actions.onReset)
        }
    }
    // The 2D guide stays one tap away while following a guide in AR (#241).
    if (state.guided) SecondaryAction("Open 2D guide", onClick = actions.onOpenGuides)
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
private fun SecondaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Text(label) }
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
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = modifier
                .statusBarsPadding()
                .padding(top = 56.dp, end = 8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
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
