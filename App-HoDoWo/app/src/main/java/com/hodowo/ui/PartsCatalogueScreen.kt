package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.formatPriceCents
import com.hodowo.ui.adaptive.AdaptiveContent
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.viewmodel.PartsCatalogueViewModel
import kotlin.math.roundToInt

/**
 * Parts catalogue (issue #148): the user finds a spare part by name, brand or model number,
 * filters by category and brand, and sees whether it fits. Tapping a part opens its detail page.
 */
@Composable
fun PartsCatalogueScreen(
    viewModel: PartsCatalogueViewModel,
    onBack: () -> Unit,
    onOpenPart: (MarketplaceProduct) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val visible = state.visibleParts

    Scaffold(
        topBar = {
            AppTopBar(
                subtitle = "SPARE PARTS",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        }
    ) { innerPadding ->
        AdaptiveContent(modifier = Modifier.padding(innerPadding), maxWidth = 1100.dp) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    placeholder = { Text("Part name, brand or model number") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.setQuery("")
                                viewModel.search()
                            }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )

                if (state.categories.isNotEmpty() || state.brands.isNotEmpty()) {
                    FilterRow(
                        label = "Category",
                        options = state.categories,
                        selected = state.category,
                        onSelect = viewModel::selectCategory
                    )
                    FilterRow(
                        label = "Brand",
                        options = state.brands,
                        selected = state.brand,
                        onSelect = viewModel::selectBrand
                    )
                }

                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Primary)
                }

                // The failed search keeps showing the last saved list, with a way to retry.
                state.error?.takeIf { state.parts.isNotEmpty() }?.let { message ->
                    ErrorBanner(message = message, onRetry = viewModel::search)
                }

                when {
                    state.isLoading && state.parts.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary)
                        }
                    }

                    state.error != null && state.parts.isEmpty() -> {
                        CatalogueMessage(
                            title = "Could not load the parts",
                            body = state.error ?: "Check your connection and try again.",
                            actionLabel = "Retry",
                            onAction = viewModel::search
                        )
                    }

                    state.hasSearched && visible.isEmpty() -> {
                        val filtered = state.parts.isNotEmpty()
                        CatalogueMessage(
                            title = "No parts found",
                            body = if (filtered) {
                                "No part matches these filters."
                            } else {
                                "Try the model number printed on your appliance, or a different name."
                            },
                            actionLabel = if (filtered) "Clear filters" else null,
                            onAction = viewModel::clearFilters
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // Extra space at the bottom, so the "New chat" button never covers a part.
                            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.isFromCache) {
                                item {
                                    Text(
                                        text = "Showing your last search",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(visible, key = { it.id }) { part ->
                                PartRow(part = part, onClick = { onOpenPart(part) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(label: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    if (options.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        options.forEach { option ->
            FilterChip(
                selected = option.equals(selected, ignoreCase = true),
                onClick = { onSelect(if (option.equals(selected, ignoreCase = true)) null else option) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun PartRow(part: MarketplaceProduct, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open part", onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                if (part.imageUrl != null) {
                    AsyncImage(
                        model = part.imageUrl,
                        contentDescription = part.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = part.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = listOfNotNull(part.brand, part.model, part.partNumber?.let { "SKU $it" })
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatPriceCents((part.price * 100).roundToInt(), "eur"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                CompatibilityBadge(compatibility = part.compatibility)
            }
        }
    }
}

@Composable
private fun CatalogueMessage(title: String, body: String, actionLabel: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = Primary.copy(alpha = 0.1f), modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Build, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
                if (actionLabel == "Retry") {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(actionLabel)
            }
        }
    }
}
