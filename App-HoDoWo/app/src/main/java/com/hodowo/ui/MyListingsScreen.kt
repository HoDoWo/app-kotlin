package com.hodowo.ui

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hodowo.ui.adaptive.AdaptiveContent
import com.hodowo.ui.adaptive.LocalAppLayout
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.viewmodel.Listing
import com.hodowo.viewmodel.ListingStatus
import com.hodowo.viewmodel.MyListingsViewModel
import com.hodowo.viewmodel.formatPriceLabel
import com.hodowo.viewmodel.inquiryLabel

/** Filter chips at the top of "My Listings". */
private enum class ListingFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    SOLD("Sold")
}

/** Conditions accepted by the marketplace API, with the labels the app shows. */
private val LISTING_CONDITIONS = listOf(
    "Unopened" to "unopened",
    "Like new" to "like-new",
    "Used, good" to "used-good",
    "For parts" to "parts-only",
    "Refurbished" to "refurbished"
)

/**
 * "My Listings": every appliance the signed-in user has posted, with its
 * status and the edit / mark-as-sold / delete actions. The data comes from the
 * marketplace backend (`/api/marketplace/listings?mine=true`), never from
 * sample data.
 */
@Composable
fun MyListingsScreen(
    viewModel: MyListingsViewModel,
    onBack: () -> Unit,
    onPostProduct: () -> Unit = {},
    /** Opens the Product Detail page of a listing, as buyers see it (#111). */
    onOpenListing: (Listing) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val layoutInfo = LocalAppLayout.current

    var selectedFilter by remember { mutableStateOf(ListingFilter.ALL) }

    LaunchedEffect(Unit) { viewModel.load() }

    // Toast for "Listing updated", "Marked as sold" and "Listing deleted".
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                subtitle = "MY LISTINGS",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    TextButton(onClick = onPostProduct) {
                        Icon(Icons.Default.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sell", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { innerPadding ->
        AdaptiveContent(modifier = Modifier.padding(innerPadding), maxWidth = 1100.dp) {
            when {
                state.isLoading && state.listings.isEmpty() && state.error == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                state.error != null && state.listings.isEmpty() -> {
                    EmptyMessage(
                        title = "Could not load your listings",
                        body = state.error ?: "Check your connection and try again.",
                        actionLabel = "Retry",
                        actionIcon = Icons.Default.Refresh,
                        onAction = viewModel::load
                    )
                }

                state.listings.isEmpty() -> {
                    EmptyMessage(
                        title = "No listings yet",
                        body = "Post an appliance you no longer need and it will appear here.",
                        actionLabel = "Post an appliance",
                        actionIcon = Icons.Default.Sell,
                        onAction = onPostProduct
                    )
                }

                else -> {
                    val filtered = when (selectedFilter) {
                        ListingFilter.ALL -> state.listings
                        ListingFilter.ACTIVE -> state.listings.filter { it.status == ListingStatus.ACTIVE }
                        ListingFilter.SOLD -> state.listings.filter { it.status == ListingStatus.SOLD }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            ListingsHeader(
                                activeCount = state.listings.count { it.status == ListingStatus.ACTIVE },
                                soldCount = state.listings.count { it.status == ListingStatus.SOLD },
                                selectedFilter = selectedFilter,
                                onSelectFilter = { selectedFilter = it },
                                onRefresh = viewModel::load
                            )
                        }

                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    text = when (selectedFilter) {
                                        ListingFilter.ACTIVE -> "You have no active listings right now."
                                        ListingFilter.SOLD -> "You have not marked any listing as sold yet."
                                        ListingFilter.ALL -> "No listings to show."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            }
                        } else if (layoutInfo.isWide) {
                            // Landscape / tablet: two cards per row so the screen
                            // uses the extra width instead of one tall column.
                            items(filtered.chunked(2), key = { row -> row.joinToString("-") { it.id } }) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    row.forEach { listing ->
                                        ListingCard(
                                            listing = listing,
                                            onEdit = { viewModel.startEditing(listing) },
                                            onMarkAsSold = { viewModel.requestMarkAsSold(listing) },
                                            onDelete = { viewModel.requestDelete(listing) },
                                            onOpen = { onOpenListing(listing) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        } else {
                            items(filtered, key = { it.id }) { listing ->
                                ListingCard(
                                    listing = listing,
                                    onEdit = { viewModel.startEditing(listing) },
                                    onMarkAsSold = { viewModel.requestMarkAsSold(listing) },
                                    onDelete = { viewModel.requestDelete(listing) },
                                    onOpen = { onOpenListing(listing) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.editingListing?.let { listing ->
        EditListingDialog(
            listing = listing,
            onDismiss = viewModel::cancelEditing,
            onSave = viewModel::saveEdit
        )
    }

    state.pendingMarkAsSold?.let { listing ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMarkAsSold,
            title = { Text("Mark as sold?") },
            text = { Text("\"${listing.title}\" will no longer show up for buyers.") },
            confirmButton = { Button(onClick = viewModel::confirmMarkAsSold) { Text("Mark as sold") } },
            dismissButton = { TextButton(onClick = viewModel::dismissMarkAsSold) { Text("Cancel") } }
        )
    }

    state.pendingDelete?.let { listing ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete this listing?") },
            text = { Text("\"${listing.title}\" will be removed permanently. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") } }
        )
    }
}

/** Active/sold counters, the filter chips and a refresh action. */
@Composable
private fun ListingsHeader(
    activeCount: Int,
    soldCount: Int,
    selectedFilter: ListingFilter,
    onSelectFilter: (ListingFilter) -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$activeCount active · $soldCount sold",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Only you can edit or delete these listings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh listings")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListingFilter.values().forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.12f),
                        selectedLabelColor = Primary
                    )
                )
            }
        }
    }
}

/** Empty/error state with one clear action (post a listing or retry). */
@Composable
private fun EmptyMessage(
    title: String,
    body: String,
    actionLabel: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = Primary.copy(alpha = 0.1f), modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(actionIcon, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun ListingCard(
    listing: Listing,
    onEdit: () -> Unit,
    onMarkAsSold: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSold = listing.status == ListingStatus.SOLD
    val statusColor = if (isSold) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth().clickable(onClickLabel = "View as buyers see it", onClick = onOpen)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ListingThumbnail(listing = listing)

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = listing.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.12f)) {
                            Text(
                                text = if (isSold) "Sold" else "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = listing.priceLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${listing.condition} · ${inquiryLabel(listing.inquiryCount)} · ${listing.postedAgo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (listing.description.isNotBlank()) {
                Text(
                    text = listing.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(8.dp)) {
                    Text("Edit", style = MaterialTheme.typography.labelMedium)
                }
                if (!isSold) {
                    OutlinedButton(onClick = onMarkAsSold, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Sell, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Mark as sold", style = MaterialTheme.typography.labelMedium)
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Square photo of the listing; a store icon when it has none. */
@Composable
private fun ListingThumbnail(listing: Listing) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        listing.imageUrl?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageData(url))
                    .crossfade(true)
                    .build(),
                contentDescription = listing.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } ?: Icon(
            Icons.Default.Storefront,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Edit form (title, description, condition, price) that saves through the
 * marketplace API. It mirrors the backend validation (3+ character title,
 * 30+ character description, a positive price) so the dialog explains a
 * problem instead of letting the request fail.
 */
@Composable
private fun EditListingDialog(
    listing: Listing,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, condition: String, priceLabel: String) -> Unit
) {
    var title by remember(listing.id) { mutableStateOf(listing.title) }
    var description by remember(listing.id) { mutableStateOf(listing.description) }
    var condition by remember(listing.id) { mutableStateOf(conditionKeyOf(listing.condition)) }
    var price by remember(listing.id) { mutableStateOf(listing.priceLabel.removePrefix("$")) }

    val trimmedTitle = title.trim()
    val trimmedDescription = description.trim()
    val priceValue = price.replace(',', '.').toDoubleOrNull() ?: 0.0
    val canSave = trimmedTitle.length >= 3 && trimmedDescription.length >= 30 && priceValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit listing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = trimmedTitle.isNotEmpty() && trimmedTitle.length < 3,
                    supportingText = { Text("At least 3 characters") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    isError = trimmedDescription.isNotEmpty() && trimmedDescription.length < 30,
                    supportingText = {
                        Text("${trimmedDescription.length}/30 characters minimum")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Condition", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LISTING_CONDITIONS.forEach { (label, key) ->
                        FilterChip(
                            selected = condition == key,
                            onClick = { condition = key },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.12f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price") },
                    singleLine = true,
                    isError = price.isNotBlank() && priceValue <= 0,
                    supportingText = { Text("A price greater than zero") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(trimmedTitle, trimmedDescription, condition, formatPriceLabel(price)) },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Maps a stored condition key or a label ("Like new") to the API's key. */
private fun conditionKeyOf(value: String): String {
    val text = value.trim()
    return LISTING_CONDITIONS.firstOrNull {
        it.second.equals(text, ignoreCase = true) || it.first.equals(text, ignoreCase = true)
    }?.second ?: "used-good"
}
