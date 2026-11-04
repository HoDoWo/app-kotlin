package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hodowo.ui.components.AppTopBar
import com.hodowo.viewmodel.Listing
import com.hodowo.viewmodel.ListingStatus
import com.hodowo.viewmodel.MyListingsViewModel
import com.hodowo.viewmodel.formatPriceLabel
import com.hodowo.viewmodel.inquiryLabel

/**
 * "My Listings" (issue #146): the seller's listings with status, and the
 * edit / mark-as-sold / delete actions, the last two behind a confirmation dialog.
 */
@Composable
fun MyListingsScreen(viewModel: MyListingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            AppTopBar(
                subtitle = "MY LISTINGS",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading && state.listings.isEmpty() && state.error == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null && state.listings.isEmpty() -> {
                    Text(
                        text = state.error ?: "Could not load your listings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }
                state.listings.isEmpty() -> {
                    Text(
                        text = "No listings yet. Post an appliance to sell it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.listings.forEach { listing ->
                            ListingCard(
                                listing = listing,
                                onEdit = { viewModel.startEditing(listing) },
                                onMarkAsSold = { viewModel.requestMarkAsSold(listing) },
                                onDelete = { viewModel.requestDelete(listing) }
                            )
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

@Composable
private fun ListingCard(
    listing: Listing,
    onEdit: () -> Unit,
    onMarkAsSold: () -> Unit,
    onDelete: () -> Unit
) {
    val isSold = listing.status == ListingStatus.SOLD
    val statusColor = if (isSold) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.1f)) {
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
                text = "${listing.condition} · ${inquiryLabel(listing.inquiryCount)} · ${listing.postedAgo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = listing.priceLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

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

/**
 * Lightweight edit form (title, description, condition, price). Stands in for the
 * real publish form (#145) until it exists.
 */
@Composable
private fun EditListingDialog(
    listing: Listing,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, condition: String, priceLabel: String) -> Unit
) {
    var title by remember(listing.id) { mutableStateOf(listing.title) }
    var description by remember(listing.id) { mutableStateOf(listing.description) }
    var condition by remember(listing.id) { mutableStateOf(listing.condition) }
    var price by remember(listing.id) { mutableStateOf(listing.priceLabel.removePrefix("$")) }

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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text("Condition") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val priceLabel = formatPriceLabel(price)
                    onSave(title.trim(), description.trim(), condition.trim(), priceLabel)
                },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
