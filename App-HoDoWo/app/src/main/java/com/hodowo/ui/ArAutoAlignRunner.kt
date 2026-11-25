package com.hodowo.ui

import com.hodowo.data.GuideLocateRequest
import com.hodowo.data.GuideLocateService
import com.hodowo.viewmodel.AlignPlan
import com.hodowo.viewmodel.ArGuideUiState
import com.hodowo.viewmodel.AutoAlignResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * AR auto-alignment (#18): sends [captured] to the AI, then anchors the first
 * control it found and fits the pins with the second, exactly as if the user
 * had aimed at them. What is missing is left to the user.
 */
internal suspend fun runAutoAlign(
    captured: CapturedFrame?,
    placement: ArPlacement,
    state: ArGuideUiState,
    service: GuideLocateService,
    referenceJpeg: ByteArray?,
    appliance: String?
): AutoAlignResult {
    val plan = state.plan as? AlignPlan.TwoPoints ?: return AutoAlignResult.NONE
    val firstStep = state.steps.getOrNull(plan.first) ?: return AutoAlignResult.NONE
    val secondStep = state.steps.getOrNull(plan.second) ?: return AutoAlignResult.NONE
    val firstPin = firstStep.pin ?: return AutoAlignResult.NONE
    val secondPin = secondStep.pin ?: return AutoAlignResult.NONE
    if (captured == null) return AutoAlignResult.NONE

    val jpeg = withContext(Dispatchers.Default) { captured.toUprightJpeg() }
    val points = try {
        service.locate(
            GuideLocateRequest(
                frameJpeg = jpeg,
                steps = state.steps,
                targets = listOf(firstStep.number, secondStep.number).distinct(),
                referenceJpeg = referenceJpeg,
                appliance = appliance
            )
        )
    } catch (e: IOException) {
        return AutoAlignResult.ERROR
    }

    val session = placement.session ?: return AutoAlignResult.NONE
    val first = points.firstOrNull { it.step == firstStep.number }
        ?.let { session.surfacePointFor(captured, it.x, it.y) }
        ?: return AutoAlignResult.NONE
    if (!placement.anchorAt(first)) return AutoAlignResult.NONE

    val second = points.firstOrNull { it.step == secondStep.number }
        ?.let { session.surfacePointFor(captured, it.x, it.y) }
    val aligned = second != null &&
        placement.alignSecondAt(second.pose.translation, firstPin, secondPin, state.photoAspect)
    return if (aligned) AutoAlignResult.BOTH else AutoAlignResult.FIRST_ONLY
}
