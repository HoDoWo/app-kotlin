package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
import java.util.Locale

private const val VIEWER_MAX_ZOOM = 5f
private const val VIEWER_DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full-screen guide (review 10-11): the image is pinned and only the steps
 * scroll, so the image and the text of a step are always visible together.
 *
 * * With the steps shown, the image stays fixed above them (beside them in
 *   landscape). Tapping a numbered pin scrolls to its step and highlights it;
 *   tapping a step highlights its pin.
 * * "Image only" hides the list: tapping a pin then opens a box with the text
 *   of that step over the image, with Previous / Next to read the others.
 *
 * The image can be zoomed (pinch or double-tap) and downloaded in both modes.
 * A guide without an image has nothing to pin and shows nothing here.
 */
@Composable
fun GuideViewerDialog(guide: ManualGuide, initialStep: Int? = null, onDismiss: () -> Unit) {
    val url = guide.imageUrl ?: return
    val steps = guide.steps
    // The selected step is its position in the list, not its number: numbers can repeat.
    var selected by remember(guide) {
        mutableStateOf(steps.indexOfFirst { it.number == initialStep }.takeIf { it >= 0 })
    }
    var stepsShown by remember { mutableStateOf(true) }
    val listShown = stepsShown && steps.isNotEmpty()
    val select: (Int) -> Unit = { index -> selected = if (selected == index && listShown) null else index }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val sideBySide = maxWidth > maxHeight
            Column(modifier = Modifier.fillMaxSize()) {
                ViewerTopBar(
                    title = guide.issueTitle,
                    url = url,
                    toggleLabel = if (steps.isEmpty()) null else if (listShown) "Image only" else "Show steps",
                    onToggle = { stepsShown = !stepsShown },
                    onDismiss = onDismiss
                )
                if (sideBySide) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ImagePane(
                            url = url,
                            steps = steps,
                            selected = selected,
                            listShown = listShown,
                            onSelect = select,
                            onClearSelection = { selected = null },
                            modifier = Modifier.weight(1.2f).fillMaxHeight()
                        )
                        if (listShown) {
                            StepsPane(guide, selected, select, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                } else {
                    ImagePane(
                        url = url,
                        steps = steps,
                        selected = selected,
                        listShown = listShown,
                        onSelect = select,
                        onClearSelection = { selected = null },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    if (listShown) {
                        StepsPane(guide, selected, select, Modifier.weight(1.1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    title: String,
    url: String,
    toggleLabel: String?,
    onToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (toggleLabel != null) {
            TextButton(onClick = onToggle) {
                Text(toggleLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        ImageDownloadButton(url = url)
    }
}

/** The pinned image with its pins and, in "Image only", the box with the text of the chosen step. */
@Composable
private fun ImagePane(
    url: String,
    steps: List<ManualStep>,
    selected: Int?,
    listShown: Boolean,
    onSelect: (Int) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clipToBounds()) {
        ZoomableGuideImage(
            url = url,
            steps = steps,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.fillMaxSize()
        )
        if (!listShown) {
            val step = selected?.let { steps.getOrNull(it) }
            if (step != null && selected != null) {
                StepCallout(
                    step = step,
                    position = "${selected + 1} / ${steps.size}",
                    onPrevious = if (selected > 0) ({ onSelect(selected - 1) }) else null,
                    onNext = if (selected < steps.lastIndex) ({ onSelect(selected + 1) }) else null,
                    onClose = onClearSelection,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else {
                Text(
                    text = if (steps.any { it.pin != null }) "Tap a number to read its step" else "Pinch or double-tap to zoom",
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }
}

/**
 * The guide image fitted in the space it has, zoomable, with a numbered pin on
 * the control of each step. The pins are placed on the image itself, so they
 * stay on their controls while zooming, and keep their size.
 */
@Composable
private fun ZoomableGuideImage(
    url: String,
    steps: List<ManualStep>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var ratio by remember(url) { mutableStateOf<Float?>(null) }
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val areaWidthPx = constraints.maxWidth.toFloat()
        val areaHeightPx = constraints.maxHeight.toFloat()
        val imageRatio = ratio
        // The size the image takes when it fits the area keeping its proportions.
        val areaIsWider = imageRatio != null && maxWidth / maxHeight > imageRatio
        val imageWidth = if (imageRatio != null && areaIsWider) maxHeight * imageRatio else maxWidth
        val imageHeight = if (imageRatio != null && !areaIsWider) maxWidth / imageRatio else maxHeight

        Box(
            modifier = Modifier
                .size(imageWidth, imageHeight)
                .pointerInput(url) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = VIEWER_DOUBLE_TAP_ZOOM
                            }
                        }
                    )
                }
                .pointerInput(url, areaWidthPx, areaHeightPx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, VIEWER_MAX_ZOOM)
                        // Keeps the zoomed image inside its area.
                        val maxX = areaWidthPx * (scale - 1f) / 2f
                        val maxY = areaHeightPx * (scale - 1f) / 2f
                        val moved = offset + pan
                        offset = if (scale == 1f) {
                            Offset.Zero
                        } else {
                            Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageData(url))
                    .crossfade(true)
                    .build(),
                contentDescription = "Step-by-step visual diagram",
                contentScale = if (imageRatio != null) ContentScale.FillBounds else ContentScale.Fit,
                onSuccess = { state ->
                    val size = state.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
                },
                modifier = Modifier.fillMaxSize()
            )
            // Pins only once the proportions are known, so they land on the right control.
            if (imageRatio != null) {
                steps.forEachIndexed { index, step ->
                    val pin = step.pin ?: return@forEachIndexed
                    val isSelected = index == selected
                    val pinSize = if (isSelected) 38.dp else 30.dp
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) Primary else Primary.copy(alpha = 0.85f),
                        border = BorderStroke(2.dp, Color.White),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .offset(x = imageWidth * pin.x - pinSize / 2, y = imageHeight * pin.y - pinSize / 2)
                            .size(pinSize)
                            .graphicsLayer {
                                scaleX = 1f / scale
                                scaleY = 1f / scale
                            }
                            .clip(CircleShape)
                            .clickable { onSelect(index) }
                            .semantics { contentDescription = "Step ${step.number}: ${step.title}" }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${step.number}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/** The steps, the only part that scrolls; it follows the pin chosen on the image. */
@Composable
private fun StepsPane(guide: ManualGuide, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // The header is the first item of the list, so the steps start at position 1.
    LaunchedEffect(selected) {
        if (selected != null) listState.animateScrollToItem(selected + 1)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = guide.applianceName.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (guide.overview.isNotBlank()) {
                        Text(
                            text = markdownText(guide.overview),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            itemsIndexed(guide.steps) { index, step ->
                StepRow(step = step, isSelected = index == selected, onClick = { onSelect(index) })
            }
            itemsIndexed(guide.warnings) { _, warning ->
                Text(
                    text = markdownText("⚠️ $warning"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StepRow(step: ManualStep, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) PrimaryFixed else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        StepNumber(step.number)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = markdownText(step.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
            if (step.description.isNotBlank()) {
                Text(
                    text = markdownText(step.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun StepNumber(number: Int) {
    Surface(shape = CircleShape, color = Primary, modifier = Modifier.size(28.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "$number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** The text of one step over the full-screen image, with the way to the previous and next step. */
@Composable
private fun StepCallout(
    step: ManualStep,
    position: String,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepNumber(step.number)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = markdownText(step.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close the step")
                }
            }
            if (step.description.isNotBlank()) {
                Text(
                    text = markdownText(step.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) { Text("Previous") }
                Text(position, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onNext?.invoke() }, enabled = onNext != null) { Text("Next") }
            }
        }
    }
}
