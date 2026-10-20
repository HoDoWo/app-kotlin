package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.ChatSession
import com.hodowo.viewmodel.relativeTimeLabel

/**
 * Menu with the past chats: for each one the appliance (model), the first
 * question and when it was used. Tapping a chat opens it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistorySheet(
    sessions: List<ChatSession>,
    currentChatId: String,
    now: Long,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit
) {
    var chatToDelete by remember { mutableStateOf<ChatSession?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Your chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onNewChat,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New chat")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (sessions.isEmpty()) {
                Text(
                    text = "No chats yet. Your conversations will be listed here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        ChatSessionRow(
                            session = session,
                            isOpen = session.id == currentChatId,
                            now = now,
                            onClick = { onOpen(session.id) },
                            onDelete = { chatToDelete = session }
                        )
                    }
                }
            }
        }
    }

    chatToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Delete this chat?") },
            text = { Text("\"${session.title}\" will be removed from your history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(session.id)
                        chatToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatSessionRow(
    session: ChatSession,
    isOpen: Boolean,
    now: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isOpen) SecondaryContainer else SurfaceContainerLowest,
        border = BorderStroke(1.dp, if (isOpen) Primary.copy(alpha = 0.4f) else OutlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Primary.copy(alpha = 0.1f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Forum, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (session.applianceName ?: "Appliance not recognised").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (session.applianceName != null) Primary else OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val label = if (isOpen) "Open now" else relativeTimeLabel(session.lastUpdated, now)
                label?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete chat", tint = OnSurfaceVariant)
            }
        }
    }
}
