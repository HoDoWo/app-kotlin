package com.hodowo.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryContainer
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SurfaceContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * Main chat screen.
 *
 * Shows the identified appliance (if any), the conversation, the text input
 * and the camera button. All the state comes from [viewModel].
 *
 * @param viewModel source of the UI state and target of the user actions.
 * @param onOpenCamera called when the user taps the camera button.
 * @param onDetectPanel called when the user taps the debug detect button
 *   (S1-14/S1-15): runs the panel analysis and opens the overlay.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenCamera: () -> Unit,
    onOpenAuth: () -> Unit
    onDetectPanel: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var dislikeTargetId by remember { mutableStateOf<Long?>(null) }
    var dislikeComment by remember { mutableStateOf("") }

    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

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
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // --- TOP APP HEADER (Matching HTML mock) ---
            TopAppHeader(
                onNewChat = { viewModel.startNewChat() },
                onOpenAuth = onOpenAuth
            )

            // --- ACTIVE APPLIANCE TELEMETRY HEADER ---
            val latestManual = state.messages.lastOrNull { it.manualGuide != null }?.manualGuide

            ActiveApplianceTelemetryHeader(
                applianceName = latestManual?.applianceName ?: state.appliance?.name ?: "New Appliance",
                statusText = if (state.isAnalyzing) "Analyzing Telemetry..."
                else latestManual?.issueTitle ?: (state.appliance?.let { "Ready • Active" } ?: "New Issue")
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
                items(
                    items = state.messages,
                    key = { it.id }
                ) { message ->
                    if (message.isUser) {
                        UserMessageItem(message = message)
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

        // --- FULLSCREEN IMAGE VIEWER OVERLAY ---
        fullscreenImageUrl?.let { url ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Fullscreen manual diagram",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )

                TextButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Back",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
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
    statusText: String
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

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "More",
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