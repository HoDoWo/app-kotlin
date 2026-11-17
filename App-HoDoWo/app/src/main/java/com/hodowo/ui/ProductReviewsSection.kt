package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hodowo.data.ProductReview
import com.hodowo.data.ReviewsSummary
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.ProductReviewsUiState
import com.hodowo.viewmodel.ProductReviewsViewModel
import com.hodowo.viewmodel.ReviewEditorState
import com.hodowo.viewmodel.relativeTimeLabel
import java.util.Locale

private val StarColor = Color(0xFFEF9900)

/**
 * "Customer Reviews" of the product page: the average with the stars, how many
 * reviews there are for each number of stars, the reviews (newest first), and
 * "Write a review" for who bought the product.
 */
@Composable
fun ProductReviewsSection(state: ProductReviewsUiState, viewModel: ProductReviewsViewModel) {
    val page = state.page
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Customer Reviews",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (page.canReview) {
                TextButton(onClick = viewModel::startWriting) {
                    Text(if (page.myReview != null) "Edit your review" else "Write a review", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        when {
            state.isLoading && page.summary.count == 0 -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            state.error != null && page.summary.count == 0 -> Text(
                text = state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            page.summary.count == 0 -> Text(
                text = if (page.canReview) {
                    "No reviews yet. You bought it: tell the others how it went."
                } else {
                    "No reviews yet. Only buyers of this product can review it."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                SummaryCard(page.summary)
                page.items.forEach { ReviewItem(it) }
            }
        }
    }
    state.editor?.let { ReviewEditorDialog(it, viewModel) }
}

@Composable
private fun SummaryCard(summary: ReviewsSummary) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.1f", summary.average),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Stars(rating = Math.round(summary.average).toInt(), size = 16)
                Text(
                    text = if (summary.count == 1) "1 review" else "${summary.count} reviews",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (ProductReviewsViewModel.MAX_STARS downTo 1).forEach { stars ->
                    val count = summary.stars[stars] ?: 0
                    val share = if (summary.count > 0) count.toFloat() / summary.count else 0f
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$stars", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(SurfaceContainerHigh)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(share).height(6.dp).clip(RoundedCornerShape(3.dp))
                                    .background(StarColor)
                            )
                        }
                        Text(
                            "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewItem(review: ProductReview) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (review.mine) "${review.authorName} (you)" else review.authorName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (review.verifiedPurchase) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verified purchase",
                                tint = Primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Stars(rating = review.rating, size = 14)
                }
                relativeTimeLabel(review.createdAt, System.currentTimeMillis())?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (review.text.isNotBlank()) {
                Text(text = review.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun Stars(rating: Int, size: Int) {
    Row {
        repeat(ProductReviewsViewModel.MAX_STARS) { index ->
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = if (index < rating) StarColor else OutlineVariant,
                modifier = Modifier.size(size.dp)
            )
        }
    }
}

@Composable
private fun ReviewEditorDialog(editor: ReviewEditorState, viewModel: ProductReviewsViewModel) {
    AlertDialog(
        onDismissRequest = { if (!editor.isSending) viewModel.cancel() },
        title = { Text(if (editor.editing) "Edit your review" else "Write a review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    (1..ProductReviewsViewModel.MAX_STARS).forEach { stars ->
                        IconButton(onClick = { viewModel.setRating(stars) }, enabled = !editor.isSending) {
                            Icon(
                                imageVector = if (stars <= editor.rating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (stars == 1) "1 star" else "$stars stars",
                                tint = if (stars <= editor.rating) StarColor else OutlineVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = editor.text,
                    onValueChange = viewModel::setText,
                    label = { Text("How did it go? (optional)") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !editor.isSending,
                    modifier = Modifier.fillMaxWidth()
                )
                editor.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::submit, enabled = !editor.isSending && editor.rating > 0) {
                Text(if (editor.isSending) "Saving..." else "Publish")
            }
        },
        dismissButton = {
            Row {
                if (editor.editing) {
                    TextButton(onClick = viewModel::delete, enabled = !editor.isSending) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = viewModel::cancel, enabled = !editor.isSending) { Text("Cancel") }
            }
        }
    )
}
