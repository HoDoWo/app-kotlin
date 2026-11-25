package com.hodowo.ui

import com.hodowo.data.ArScanService
import com.hodowo.viewmodel.ScanLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the AR tab scan found: the device name (null when none) and its controls. */
internal class ArScanOutcome(val device: String?, val labels: List<ScanLabel>)

/**
 * AR tab scan (#18): sends [captured] to the AI and anchors a label on every
 * control it names, on the plane its ray meets (the phone may have moved while
 * the AI answered). Throws [java.io.IOException] when there is no answer.
 */
internal suspend fun runArScan(captured: CapturedFrame?, placement: ArPlacement, service: ArScanService): ArScanOutcome {
    if (captured == null) return ArScanOutcome(null, emptyList())
    val jpeg = withContext(Dispatchers.Default) { captured.toUprightJpeg() }
    val result = service.scan(jpeg)
    val session = placement.session
    val markers = mutableListOf<ScanMarker>()
    val labels = result.controls.map { control ->
        val anchor = session?.surfacePointFor(captured, control.x, control.y)
            ?.let { point -> runCatching { point.plane.createAnchor(point.pose) }.getOrNull() }
        anchor?.let { markers += ScanMarker(it, control.label) }
        ScanLabel(control.label, control.description, placed = anchor != null)
    }
    placement.showScan(markers)
    return ArScanOutcome(result.deviceName, labels)
}
