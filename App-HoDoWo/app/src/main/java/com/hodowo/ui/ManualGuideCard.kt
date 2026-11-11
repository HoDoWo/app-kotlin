package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hodowo.data.formatPriceCents
import com.hodowo.ui.adaptive.LocalAppLayout
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.SuggestedPart
import java.util.Locale

/**
 * AI Diagnostic Response Box matching the HTML mock with enlarged typography.
 *
 * In landscape / wide layouts the diagram image is shown beside the step list
 * instead of below it, making better use of horizontal space.
 */
@Composable
fun ManualGuideCard(
    guide: ManualGuide,
    isBookmarked: Boolean,
    feedback: Feedback?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onImageClick: (String) -> Unit,
    /** "Buy part" of the suggested spare part (#149): opens its detail page. */
    onBuyPart: (SuggestedPart) -> Unit = {}
) {
    val isWide = LocalAppLayout.current.isWide
    // Step chosen on the image (pin) or in the list: both show it highlighted.
    var selectedStep by remember(guide) { mutableStateOf<Int?>(null) }
    val onSelectStep: (Int) -> Unit = { number -> selectedStep = if (selectedStep == number) null else number }

    Surface(
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceContainerHigh.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Badge & Title
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = guide.applianceName.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 13.sp
                    )

                    // Bookmark Button
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = guide.issueTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp,
                    fontSize = 20.sp
                )
            }

            HorizontalDivider(color = SurfaceContainerHigh.copy(alpha = 0.6f))

            // Diagnostic Overview
            Text(
                text = guide.overview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp,
                fontSize = 16.sp
            )

            // Step-by-Step Instructions + optional side image in wide layout
            if (isWide && guide.imageUrl != null) {
                // Landscape: steps on the left, image on the right
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Steps column (takes remaining space)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StepsSection(guide, selectedStep, onSelectStep)
                    }

                    // Diagram image (fixed width beside steps), with the step pins
                    GuideImage(
                        url = guide.imageUrl,
                        steps = guide.steps,
                        selectedStep = selectedStep,
                        onSelectStep = onSelectStep,
                        onImageClick = onImageClick,
                        modifier = Modifier.width(200.dp)
                    )
                }
            } else {
                // Portrait: steps, then image below
                if (guide.steps.isNotEmpty()) {
                    StepsSection(guide, selectedStep, onSelectStep)
                }

                guide.imageUrl?.let { url ->
                    GuideImage(
                        url = url,
                        steps = guide.steps,
                        selectedStep = selectedStep,
                        onSelectStep = onSelectStep,
                        onImageClick = onImageClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Hidden entirely when the diagnosis has no confident suggestion.
            guide.suggestedPart?.let { part ->
                SuggestedPartBlock(part = part, onBuy = { onBuyPart(part) })
            }

            HorizontalDivider(color = SurfaceContainerHigh.copy(alpha = 0.6f))

            // Helpfulness Feedback Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Was this manual helpful?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackPill(
                        text = "Yes",
                        icon = Icons.Default.ThumbUp,
                        isSelected = feedback == Feedback.LIKE,
                        selectedColor = Primary,
                        onClick = onLike
                    )
                    FeedbackPill(
                        text = "No",
                        icon = Icons.Default.ThumbDown,
                        isSelected = feedback == Feedback.DISLIKE,
                        selectedColor = MaterialTheme.colorScheme.error,
                        onClick = onDislike
                    )
                }
            }
        }
    }
}

/** Numbered step list, shared between portrait and landscape layouts. */
@Composable
private fun StepsSection(guide: ManualGuide, selectedStep: Int?, onSelectStep: (Int) -> Unit) {
    if (guide.steps.isEmpty()) return

    Text(
        text = "Follow these repair steps:",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 17.sp
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        guide.steps.forEach { step ->
            val selected = step.number == selectedStep
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) PrimaryFixed else Color.Transparent)
                    .then(if (step.pin != null) Modifier.clickable { onSelectStep(step.number) } else Modifier)
                    .padding(4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Primary,
                    modifier = Modifier.size(28.dp).padding(top = 1.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${step.number}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            append(step.title)
                            if (step.description.isNotBlank()) {
                                append(": ")
                                append(step.description)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 23.sp,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * The guide image with a numbered pin on the control of each step (review 10-11).
 * Tapping a pin highlights its step in the list (and the other way round); the
 * image keeps its proportions so the pins stay on their controls. Tapping the
 * image elsewhere opens it full screen.
 */
@Composable
private fun GuideImage(
    url: String,
    steps: List<ManualStep>,
    selectedStep: Int?,
    onSelectStep: (Int) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var ratio by remember(url) { mutableStateOf<Float?>(null) }
    val pinned = steps.filter { it.pin != null }
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SurfaceContainerHigh, RoundedCornerShape(12.dp))
            .then(ratio?.let { Modifier.aspectRatio(it) } ?: Modifier)
            .clickable { onImageClick(url) }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageData(url))
                .crossfade(true)
                .build(),
            contentDescription = "Step-by-step visual diagram",
            contentScale = if (ratio != null) ContentScale.FillBounds else ContentScale.FillWidth,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
            },
            modifier = Modifier.fillMaxWidth()
        )
        // Pins only once the proportions are known, so they land on the right control.
        if (ratio != null) {
            pinned.forEach { step ->
                val pin = step.pin ?: return@forEach
                val selected = step.number == selectedStep
                val size = if (selected) 34.dp else 26.dp
                Surface(
                    shape = CircleShape,
                    color = if (selected) Primary else Primary.copy(alpha = 0.85f),
                    border = BorderStroke(2.dp, Color.White),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .offset(x = maxWidth * pin.x - size / 2, y = maxHeight * pin.y - size / 2)
                        .size(size)
                        .clickable { onSelectStep(step.number) }
                        .semantics { contentDescription = "Step ${step.number}: ${step.title}" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${step.number}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** The spare part the diagnosis points to: image, name, price, compatibility and a buy action (#149). */
@Composable
private fun SuggestedPartBlock(part: SuggestedPart, onBuy: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth().border(1.dp, SurfaceContainerHigh, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Suggested spare part",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (part.imageUrl != null) {
                        AsyncImage(
                            model = part.imageUrl,
                            contentDescription = part.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = part.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                    part.priceCents?.let {
                        Text(
                            text = formatPriceCents(it, "eur"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                    CompatibilityBadge(compatibility = part.compatibility)
                }
            }
            Button(onClick = onBuy, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Buy part")
            }
        }
    }
}