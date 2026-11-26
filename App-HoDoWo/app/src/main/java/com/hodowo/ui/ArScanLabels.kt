package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
private val LABEL_MAX_WIDTH = 220.dp

/**
 * The scan labels, flat on the screen just above each control's 3D dot: a numbered
 * badge, and for the selected control a pill with its name. Drawn by Compose, not in
 * the 3D scene, so they stay sharp, readable and the same size from any distance.
 * Tapping one selects it, like tapping its row in the panel.
 */
@Composable
internal fun ScanLabelsOverlay(
    markers: List<ScanMarker>,
    points: List<ScreenPoint?>,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // The selected label last, so it is drawn on top of its neighbours.
        val ordered = markers.sortedBy { it.index == selected }
        ordered.forEach { marker ->
            val point = points.getOrNull(markers.indexOf(marker)) ?: return@forEach
            key(marker) {
                ScanLabelChip(
                    marker = marker,
                    selected = marker.index == selected,
                    modifier = Modifier.above(point),
                    onClick = { onSelect(marker.index) }
                )
            }
        }
    }
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
private fun ScanLabelChip(marker: ScanMarker, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val number = "${marker.index + 1}"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$number · ${marker.label}" }
    ) {
        Surface(
            shape = CircleShape,
            color = Primary,
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 6.dp
        ) {
            if (selected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 3.dp, end = 14.dp, top = 3.dp, bottom = 3.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(INNER_BADGE_SIZE)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(number, color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = marker.label,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = LABEL_MAX_WIDTH)
                    )
                }
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(BADGE_SIZE)) {
                    Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        // A small pointer down to the dot.
        Canvas(modifier = Modifier.size(POINTER_WIDTH, POINTER_HEIGHT)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, Primary)
        }
    }
}
