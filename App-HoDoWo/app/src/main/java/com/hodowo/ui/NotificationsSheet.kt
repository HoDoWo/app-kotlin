package com.hodowo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hodowo.data.AppNotification
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.viewmodel.NotificationsUiState
import com.hodowo.viewmodel.relativeTimeLabel

/**
 * The list behind the bell: new messages, offers to answer (also on the
 * products the user sells), answers to the user's offers, price changes in the
 * cart and a sponsored product. Tapping one opens what it is about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    state: NotificationsUiState,
    onOpen: (AppNotification) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            when {
                state.items.isEmpty() && state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Primary) }
                state.items.isEmpty() -> EmptyNotifications(state.error)
                else -> {
                    val now = System.currentTimeMillis()
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 560.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            NotificationRow(
                                item = item,
                                isNew = state.isNew(item),
                                time = if (item.kind == "sponsored") null else relativeTimeLabel(item.createdAt, now),
                                onClick = { onOpen(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotifications(error: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.NotificationsNone,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = error ?: "You're all caught up",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariant
        )
        if (error == null) {
            Text(
                text = "Messages, offers on your listings and price changes in your cart will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationRow(item: AppNotification, isNew: Boolean, time: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isNew) PrimaryFixed.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box {
            val url = item.imageUrl
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceContainerHigh)
                )
            } else {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) { Icon(iconOf(item.kind), contentDescription = null, tint = Primary) }
            }
            if (url != null) {
                Surface(
                    shape = CircleShape,
                    color = Primary,
                    modifier = Modifier.size(20.dp).align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            iconOf(item.kind),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isNew) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                time?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun iconOf(kind: String): ImageVector = when (kind) {
    "offer" -> Icons.Outlined.LocalOffer
    "offer_accepted" -> Icons.Outlined.CheckCircle
    "offer_declined" -> Icons.Outlined.HighlightOff
    "price_drop" -> Icons.AutoMirrored.Outlined.TrendingDown
    "price_change" -> Icons.AutoMirrored.Outlined.TrendingUp
    "sponsored" -> Icons.Outlined.Campaign
    "sale" -> Icons.Outlined.Sell
    else -> Icons.Outlined.ChatBubbleOutline
}
