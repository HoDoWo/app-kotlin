package com.hodowo.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hodowo.data.Conversation
import com.hodowo.data.ConversationMessage
import com.hodowo.data.Offer
import com.hodowo.data.formatPriceCents
import com.hodowo.data.formatTimeAgo
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.theme.Error
import com.hodowo.ui.theme.OnPrimary
import com.hodowo.ui.theme.OnSurface
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.MessagesViewModel
import kotlinx.coroutines.delay

private const val CURRENCY = "eur"
private const val POLL_MILLIS = 10_000L

/**
 * Messages between buyers and sellers: the inbox, or the open conversation with
 * its offer card. While a conversation is open it is reloaded every few seconds.
 */
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onBack: () -> Unit,
    /** An accepted offer was put in the cart: show the cart to pay. */
    onOpenCart: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadInbox() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.openCart) {
        if (state.openCart) {
            viewModel.onCartOpened()
            onOpenCart()
        }
    }

    val open = state.open
    if (open != null) {
        BackHandler { viewModel.close() }
        LaunchedEffect(open.id) {
            while (true) {
                delay(POLL_MILLIS)
                viewModel.refreshOpen()
            }
        }
        ConversationContent(
            conversation = open,
            isSending = state.isSending,
            askOffer = state.askOffer,
            onOfferDialogShown = viewModel::onOfferDialogShown,
            onBack = viewModel::close,
            onSend = viewModel::send,
            onOffer = viewModel::makeOffer,
            onAccept = viewModel::acceptOffer,
            onDecline = viewModel::declineOffer,
            onPay = viewModel::payOffer
        )
    } else {
        BackHandler(onBack = onBack)
        InboxContent(
            conversations = state.conversations,
            isLoading = state.isLoading || state.isOpening,
            error = state.error,
            onBack = onBack,
            onRetry = viewModel::loadInbox,
            onOpen = viewModel::openConversation
        )
    }
}

// ---------- inbox ----------

@Composable
private fun InboxContent(
    conversations: List<Conversation>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AppTopBar(
            subtitle = "MESSAGES",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                }
            }
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && conversations.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                error != null && conversations.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    OutlinedButton(onClick = onRetry) { Text("Try again") }
                }
                conversations.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = OnSurfaceVariant,
                        modifier = Modifier.size(40.dp))
                    Text("No messages yet", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        "Message a seller from a second-hand listing: the conversations with buyers and sellers appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(conversations, key = { it.id }) { conversation ->
                        InboxRow(conversation = conversation, onClick = { onOpen(conversation.id) })
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(start = 84.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ListingThumbnail(conversation.listing.imageUrl, size = 56)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.otherName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (conversation.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(formatTimeAgo(conversation.updatedAt), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
            Text(
                text = (if (conversation.buying) "Buying · " else "Selling · ") + conversation.listing.title,
                style = MaterialTheme.typography.labelMedium,
                color = Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.lastMessage?.let { (if (it.mine) "You: " else "") + it.text } ?: "No messages yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (conversation.unread > 0) OnSurface else OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unread > 0) {
                    Badge(containerColor = Primary) { Text("${conversation.unread}", color = OnPrimary) }
                }
            }
        }
    }
}

@Composable
private fun ListingThumbnail(url: String?, size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------- conversation ----------

@Composable
private fun ConversationContent(
    conversation: Conversation,
    isSending: Boolean,
    askOffer: Boolean,
    onOfferDialogShown: () -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onOffer: (String) -> Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onPay: () -> Unit
) {
    var draft by remember(conversation.id) { mutableStateOf("") }
    var showOfferDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(askOffer) {
        if (askOffer) {
            showOfferDialog = true
            onOfferDialogShown()
        }
    }
    LaunchedEffect(conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) listState.animateScrollToItem(conversation.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        AppTopBar(
            subtitle = conversation.otherName.uppercase(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to messages")
                }
            }
        )
        ListingHeader(conversation)
        conversation.offer?.let { offer ->
            OfferCard(
                offer = offer,
                buying = conversation.buying,
                isSending = isSending,
                onAccept = onAccept,
                onDecline = onDecline,
                onCounter = { showOfferDialog = true },
                onPay = onPay
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (conversation.messages.isEmpty()) {
                item {
                    Text(
                        text = if (conversation.buying) "Ask ${conversation.otherName} about the item, or make an offer."
                        else "Say hello to ${conversation.otherName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
            items(conversation.messages, key = { it.id }) { message -> MessageBubble(message) }
        }
        Composer(
            draft = draft,
            onDraftChange = { draft = it },
            canOffer = conversation.canOffer,
            isSending = isSending,
            onOfferClick = { showOfferDialog = true },
            onSend = {
                onSend(draft)
                draft = ""
            }
        )
    }

    if (showOfferDialog) {
        OfferDialog(
            listPriceCents = conversation.listing.priceCents,
            counter = conversation.offer?.canAnswer == true,
            onDismiss = { showOfferDialog = false },
            onConfirm = { amount -> if (onOffer(amount)) showOfferDialog = false }
        )
    }
}

@Composable
private fun ListingHeader(conversation: Conversation) {
    val listing = conversation.listing
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceContainerLowest).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ListingThumbnail(listing.imageUrl, size = 44)
        Column(modifier = Modifier.weight(1f)) {
            Text(listing.title, style = MaterialTheme.typography.titleSmall, color = OnSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            val status = when (listing.status) {
                "sold" -> "Sold"
                "removed" -> "No longer listed"
                else -> listing.priceCents?.let { formatPriceCents(it, CURRENCY) } ?: ""
            }
            Text(status, style = MaterialTheme.typography.labelMedium,
                color = if (listing.status == "active") OnSurfaceVariant else Error)
        }
    }
    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun OfferCard(
    offer: Offer,
    buying: Boolean,
    isSending: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCounter: () -> Unit,
    onPay: () -> Unit
) {
    val amount = formatPriceCents(offer.amountCents, CURRENCY)
    val who = if (offer.fromMe) "Your offer" else if (buying) "Seller's offer" else "Buyer's offer"
    val note = when (offer.status) {
        "pending" -> if (offer.fromMe) "Waiting for an answer" else "Answer before it expires"
        "accepted" -> if (buying) "Accepted: pay it from the cart within 48 hours" else "Accepted: waiting for the payment"
        "declined" -> "Declined"
        "used" -> "Paid"
        else -> "Expired"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = PrimaryFixed.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.LocalOffer, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                Text("$who: $amount", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = OnSurface, modifier = Modifier.weight(1f))
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            if (offer.canAnswer) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept, enabled = !isSending, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text("Accept")
                    }
                    OutlinedButton(onClick = onCounter, enabled = !isSending) { Text("Counter") }
                    TextButton(onClick = onDecline, enabled = !isSending) { Text("Decline") }
                }
            }
            if (offer.canPay) {
                Button(
                    onClick = onPay,
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buy for $amount") }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage) {
    val isEvent = message.kind != "text"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isEvent -> Arrangement.Center
            message.mine -> Arrangement.End
            else -> Arrangement.Start
        }
    ) {
        if (isEvent) {
            // Offers and answers: a small line in the middle, like Vinted.
            Surface(shape = RoundedCornerShape(50), color = SurfaceContainerLow) {
                Text(
                    text = (if (message.mine) "You: " else "") + message.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            Column(horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (message.mine) Primary else SurfaceContainerLowest,
                    border = if (message.mine) null else BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.mine) OnPrimary else OnSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Text(formatTimeAgo(message.createdAt), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp))
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    canOffer: Boolean,
    isSending: Boolean,
    onOfferClick: () -> Unit,
    onSend: () -> Unit
) {
    Surface(color = SurfaceContainerLowest, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canOffer) {
                IconButton(onClick = onOfferClick, enabled = !isSending) {
                    Icon(Icons.Outlined.LocalOffer, contentDescription = "Make an offer", tint = Primary)
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= MAX_MESSAGE) onDraftChange(it) },
                placeholder = { Text("Write a message") },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onSend,
                enabled = draft.isNotBlank() && !isSending,
                modifier = Modifier.clip(CircleShape).background(if (draft.isNotBlank()) Primary else SurfaceContainerLow)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send",
                    tint = if (draft.isNotBlank()) OnPrimary else OnSurfaceVariant)
            }
        }
    }
}

private const val MAX_MESSAGE = 1000

@Composable
private fun OfferDialog(listPriceCents: Int?, counter: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (counter) "Make a counter-offer" else "Make an offer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listPriceCents?.let {
                    Text("Listed at ${formatPriceCents(it, CURRENCY)}. Offers start from half the price.",
                        style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(10) },
                    label = { Text("Your price (€)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.size(4.dp))
                Text("If it is accepted, you can buy it at this price for 48 hours.",
                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount) }, enabled = amount.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Send offer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
