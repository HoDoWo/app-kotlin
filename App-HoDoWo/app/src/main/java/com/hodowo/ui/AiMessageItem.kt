package com.hodowo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hodowo.ui.theme.PrimaryContainer
import com.hodowo.viewmodel.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI Message Item (handles both Manual Guides and Text responses).
 */
@Composable
fun AiMessageItem(
    message: Message,
    currentStepText: String?,
    isSending: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onImageClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Neurology / AI Avatar
        Surface(
            shape = CircleShape,
            color = PrimaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier.size(32.dp).padding(top = 2.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "AI",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            if (message.manualGuide != null) {
                // AI Diagnostic Response Box (Manual Guide)
                ManualGuideCard(
                    guide = message.manualGuide,
                    isBookmarked = message.isBookmarked,
                    feedback = message.feedback,
                    onLike = onLike,
                    onDislike = onDislike,
                    onToggleBookmark = onToggleBookmark,
                    onImageClick = onImageClick
                )
            } else {
                // Regular Text Response Bubble
                RegularAiTextBubble(
                    message = message,
                    currentStepText = currentStepText, // 3. Pásalo hacia dentro
                    isSending = isSending,             // 4. Pásalo hacia dentro
                    onLike = onLike,
                    onDislike = onDislike
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}