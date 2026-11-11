package com.hodowo.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hodowo.data.ChatMarkdown
import com.hodowo.data.MdStyle
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.Message

/**
 * Regular Text AI message bubble.
 */
@Composable
fun RegularAiTextBubble(
    message: Message,
    currentStepText: String?,
    isSending: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceContainerHigh.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (message.text.isNullOrBlank() && isSending) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                    Text(
                        text = currentStepText ?: "Thinking...",
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                val textToDisplay = if (message.isStreaming) (message.text ?: "") + " ▍" else (message.text ?: "")
                // The AI writes Markdown: show bold, lists and headings instead of the raw markers.
                val styled = remember(textToDisplay) { markdownText(textToDisplay) }
                Text(
                    text = styled,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 23.sp
                )
            }

            if (!message.isStreaming && !message.text.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeedbackPill(
                        text = "Yes",
                        icon = Icons.Default.ThumbUp,
                        isSelected = message.feedback == Feedback.LIKE,
                        selectedColor = Primary,
                        onClick = onLike
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FeedbackPill(
                        text = "No",
                        icon = Icons.Default.ThumbDown,
                        isSelected = message.feedback == Feedback.DISLIKE,
                        selectedColor = MaterialTheme.colorScheme.error,
                        onClick = onDislike
                    )
                }
            }
        }
    }
}

/** The styled text of an AI answer, from [ChatMarkdown]. */
internal fun markdownText(text: String): AnnotatedString = buildAnnotatedString {
    ChatMarkdown.parse(text).forEach { span ->
        withStyle(spanStyle(span.styles)) { append(span.text) }
    }
}

private fun spanStyle(styles: Set<MdStyle>): SpanStyle = SpanStyle(
    fontWeight = if (MdStyle.BOLD in styles) FontWeight.Bold else null,
    fontStyle = if (MdStyle.ITALIC in styles) FontStyle.Italic else null,
    fontFamily = if (MdStyle.CODE in styles) FontFamily.Monospace else null,
    background = if (MdStyle.CODE in styles) SurfaceContainerHigh else Color.Unspecified,
    color = if (MdStyle.LINK in styles) Primary else Color.Unspecified,
    textDecoration = if (MdStyle.LINK in styles) TextDecoration.Underline else null,
    fontSize = if (MdStyle.HEADING in styles) 17.sp else TextUnit.Unspecified
)

