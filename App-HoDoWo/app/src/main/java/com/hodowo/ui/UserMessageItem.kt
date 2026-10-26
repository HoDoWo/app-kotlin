package com.hodowo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hodowo.ui.theme.Primary
import com.hodowo.viewmodel.Message

/**
 * User Outgoing Message Bubble.
 *
 * With [onRegenerate] (and [onEdit] for a question with text) the bubble shows
 * "Regenerate" and "Edit" under it; the chat passes them for its last question only.
 */
@Composable
fun UserMessageItem(
    message: Message,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Primary,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                message.text?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        lineHeight = 20.sp
                    )
                }

                message.imageUri?.let { uri ->
                    Spacer(modifier = Modifier.height(8.dp))
                    LocalThumbnail(
                        uri = uri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formatTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(end = 4.dp)
        )

        if (onRegenerate != null || onEdit != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onRegenerate?.let { MessageActionButton(Icons.Default.Refresh, "Regenerate", it) }
                onEdit?.let { MessageActionButton(Icons.Default.Edit, "Edit", it) }
            }
        }
    }
}

/** Small text button with an icon, shown under the user's last question. */
@Composable
private fun MessageActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
