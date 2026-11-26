package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hodowo.ui.theme.Primary
import com.hodowo.viewmodel.ScreenPoint

private val CHIP_GAP = 6.dp
private val EDGE_PADDING = 8.dp
private val BADGE_SIZE = 28.dp
private val INNER_BADGE_SIZE = 22.dp
private val POINTER_WIDTH = 12.dp
private val POINTER_HEIGHT = 7.dp
private val STEM_HEIGHT = 36.dp
private val STEM_WIDTH = 2.dp
private val STEM_OUTLINE = 4.dp
private val LABEL_MAX_WIDTH = 220.dp

/** A label drawn flat on the camera view above a 3D dot: [badge] always, [text] when [highlighted]. */
internal class OverlayLabel(
    val key: Any,
    val point: ScreenPoint,
    val badge: String,
    val text: String,
    val highlighted: Boolean,
    val onClick: () -> Unit
)

/**
 * Labels flat on the screen just above their 3D dots: a numbered badge, and for the
 * highlighted one a pill with its text. Drawn by Compose, not in the 3D scene, so they
 * stay sharp, readable and the same size from any distance. Used by the AR tab scan
 * (controls) and by the guide (steps).
 */
@Composable
internal fun ArLabelsOverlay(labels: List<OverlayLabel>) {
    Box(modifier = Modifier.fillMaxSize()) {
        // The highlighted label last, so it is drawn on top of its neighbours.
        labels.sortedBy { it.highlighted }.forEach { label ->
            key(label.key) {
                LabelChip(label, modifier = Modifier.above(label.point))
            }
        }
    }
}

/** The scan labels: tapping one selects it, like tapping its row in the panel. */
@Composable
internal fun ScanLabelsOverlay(
    markers: List<ScanMarker>,
    points: List<ScreenPoint?>,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    val labels = markers.mapIndexedNotNull { i, marker ->
        val point = points.getOrNull(i) ?: return@mapIndexedNotNull null
        OverlayLabel(
            key = marker,
            point = point,
            badge = "${marker.index + 1}",
            text = marker.label,
            highlighted = marker.index == selected,
            onClick = { onSelect(marker.index) }
        )
    }
    ArLabelsOverlay(labels)
}

/** Places the content with its bottom centre on [point], kept inside the screen sideways. */
private fun Modifier.above(point: ScreenPoint): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth))
    val edge = EDGE_PADDING.roundToPx()
    val gap = CHIP_GAP.roundToPx()
    val maxX = (constraints.maxWidth - placeable.width - edge).coerceAtLeast(edge)
    val x = (point.x - placeable.width / 2f).toInt().coerceIn(edge, maxX)
    val y = (point.y - placeable.height - gap).toInt()
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
}

@Composable
private fun LabelChip(label: OverlayLabel, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(role = Role.Button, onClick = label.onClick)
            .semantics { contentDescription = "${label.badge} · ${label.text}" }
    ) {
        Surface(
            shape = CircleShape,
            color = Primary,
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 6.dp
        ) {
            if (label.highlighted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 3.dp, end = 14.dp, top = 3.dp, bottom = 3.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.White) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(INNER_BADGE_SIZE, INNER_BADGE_SIZE)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(label.badge, color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label.text,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = LABEL_MAX_WIDTH)
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(BADGE_SIZE, BADGE_SIZE)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(label.badge, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        // A pointer down to the dot. The highlighted pill is wide: it stands higher on a
        // thin stem, so it does not cover the control and its neighbours.
        val height = if (label.highlighted) POINTER_HEIGHT + STEM_HEIGHT else POINTER_HEIGHT
        Canvas(modifier = Modifier.size(POINTER_WIDTH, height)) {
            val tip = POINTER_HEIGHT.toPx()
            val stemTop = size.height - tip
            if (stemTop > 0f) {
                drawLine(
                    color = Color.White,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, stemTop),
                    strokeWidth = STEM_OUTLINE.toPx()
                )
                drawLine(
                    color = Primary,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, stemTop),
                    strokeWidth = STEM_WIDTH.toPx()
                )
            }
            val path = Path().apply {
                moveTo(0f, stemTop)
                lineTo(size.width, stemTop)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, Primary)
        }
    }
}
