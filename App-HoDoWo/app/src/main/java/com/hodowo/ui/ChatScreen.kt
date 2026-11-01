package com.hodowo.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.components.NotificationIconButton
import com.hodowo.ui.components.ProfileAvatar
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.Message

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenCamera: () -> Unit,
    onOpenAuth: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var dislikeTargetId by remember { mutableStateOf<Long?>(null) }
    var dislikeComment by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editText by remember { mutableStateOf("") }

    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    // System gallery picker to upload image from device
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setSelectedImage(uri)
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length, state.currentStep) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // --- TOP APP BANNER (shared component) ---
            AppTopBar(
                subtitle = "AI CHAT",
                actions = {
                    IconButton(onClick = { showHistory = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Chat history",
                            tint = Primary
                        )
                    }
                    IconButton(onClick = { viewModel.startNewChat() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = Primary
                        )
                    }
                    NotificationIconButton()
                    ProfileAvatar(
                        name = "",
                        size = 34,
                        onClick = onOpenAuth,
                        photoUrl = state.userProfile?.photoUrl
                    )
                }
            )

            // --- ACTIVE APPLIANCE TELEMETRY HEADER ---
            val latestManual = state.messages.lastOrNull { it.manualGuide != null }?.manualGuide

            ActiveApplianceTelemetryHeader(
                applianceName = latestManual?.applianceName ?: state.appliance?.name ?: "New Appliance",
                statusText = if (state.isAnalyzing) "Analyzing Telemetry..."
                else latestManual?.issueTitle ?: (state.appliance?.let { "Ready • Active" } ?: "New Issue"),
                onMore = { showHistory = true }
            )

            // --- CHAT MESSAGE STREAM ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Telemetry Timestamp Anchor
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Today, 2:14 PM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // Message Items
                val lastQuestionId = state.messages.lastOrNull { it.isUser }?.id
                items(
                    items = state.messages,
                    key = { it.id }
                ) { message ->
                    if (message.isUser) {
                        // Only the last question can be asked again, and not while an answer arrives.
                        val canResend = message.id == lastQuestionId && !state.isSending && !state.isAnalyzing
                        UserMessageItem(
                            message = message,
                            onRegenerate = if (canResend) {
                                { viewModel.regenerateMessage(message.id) }
                            } else {
                                null
                            },
                            onEdit = if (canResend && !message.text.isNullOrBlank()) {
                                {
                                    editText = message.text.orEmpty()
                                    editingMessage = message
                                }
                            } else {
                                null
                            }
                        )
                    } else {
                        AiMessageItem(
                            message = message,
                            currentStepText = state.currentStep,
                            isSending = state.isSending,
                            onLike = { viewModel.setFeedback(message.id, Feedback.LIKE) },
                            onDislike = {
                                dislikeComment = ""
                                dislikeTargetId = message.id
                            },
                            onToggleBookmark = { viewModel.toggleBookmark(message.id) },
                            onImageClick = { url -> fullscreenImageUrl = url }
                        )
                    }
                }
            }

            // --- ERROR BANNER (if any) ---
            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // --- SELECTED IMAGE PREVIEW CHIP (Before sending) ---
            AnimatedVisibility(visible = state.selectedImageUri != null) {
                state.selectedImageUri?.let { uri ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = SurfaceContainerLowest,
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier.border(1.dp, SurfaceContainerHigh, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LocalThumbnail(uri = uri, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Photo attached",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { viewModel.clearSelectedImage() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // --- PERSISTENT BOTTOM CHAT DOCK (Floating Pill) ---
            PersistentBottomChatDock(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    viewModel.sendTextMessage(inputText)
                    inputText = ""
                },
                onOpenCamera = onOpenCamera,
                onPickGallery = { galleryLauncher.launch("image/*") },
                isSending = state.isSending
            )
        }

        // --- FULLSCREEN IMAGE VIEWER (zoom + download) ---
        fullscreenImageUrl?.let { url ->
            FullscreenImageViewer(url = url, onDismiss = { fullscreenImageUrl = null })
        }
    }

    // --- CHAT HISTORY MENU ---
    if (showHistory) {
        ChatHistorySheet(
            sessions = state.chatSessions,
            currentChatId = state.currentChatId,
            now = System.currentTimeMillis(),
            onOpen = { chatId ->
                if (state.isSending) {
                    Toast.makeText(context, "Wait for the answer before opening another chat", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.openChat(chatId)
                    showHistory = false
                }
            },
            onDelete = viewModel::deleteChat,
            onNewChat = {
                viewModel.startNewChat()
                showHistory = false
            },
            onDismiss = { showHistory = false }
        )
    }

    // Edit the last question and ask it again
    editingMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editText.isNotBlank(),
                    onClick = {
                        viewModel.editMessage(target.id, editText)
                        editingMessage = null
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dislike feedback dialog
    dislikeTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { dislikeTargetId = null },
            title = { Text("Why was this not helpful?") },
            text = {
                OutlinedTextField(
                    value = dislikeComment,
                    onValueChange = { dislikeComment = it },
                    placeholder = { Text("Add comment (optional)...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setFeedback(targetId, Feedback.DISLIKE, dislikeComment)
                        dislikeTargetId = null
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setFeedback(targetId, Feedback.DISLIKE)
                        dislikeTargetId = null
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }
}


/**
 * Sticky Active Appliance Telemetry Header.
 */
@Composable
private fun ActiveApplianceTelemetryHeader(
    applianceName: String,
    statusText: String,
    onMore: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceContainerHigh.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = applianceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (statusText.contains("Error", ignoreCase = true)) MaterialTheme.colorScheme.error
                    else Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(onClick = onMore) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Chat history",
                    tint = Primary
                )
            }
        }
    }
}



@Composable
fun FeedbackPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) selectedColor.copy(alpha = 0.15f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) selectedColor else SurfaceContainerHigh
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
