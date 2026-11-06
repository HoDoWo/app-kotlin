package com.hodowo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hodowo.data.ForumReply
import com.hodowo.data.ForumReportReason
import com.hodowo.data.ForumThread
import com.hodowo.data.forumBadgeLabel
import com.hodowo.data.formatTimeAgo
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.SecondaryFixed
import com.hodowo.ui.theme.SurfaceContainer
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.AskQuestionState
import com.hodowo.viewmodel.OpenThreadState
import com.hodowo.viewmodel.ProductForumUiState
import com.hodowo.viewmodel.ProductForumViewModel

/** What a report is about: a question (replyId null) or one of its replies. */
private data class ReportTarget(val threadId: String, val replyId: String?)

/**
 * "Community Forum" block of the Product Detail page (app #150): real questions about the
 * product from the backend (Backend#44), laid out like the Stitch "Product Detail & Community"
 * design. Opening a question shows all its replies and the reply composer.
 */
@Composable
fun ProductForumSection(state: ProductForumUiState, viewModel: ProductForumViewModel) {
    var reportTarget by remember { mutableStateOf<ReportTarget?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Community Forum",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (state.threads.isNotEmpty()) {
                TextButton(onClick = viewModel::startAsking) { Text("Ask a question", fontWeight = FontWeight.SemiBold) }
            }
        }

        when {
            state.isLoading && state.threads.isEmpty() -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            state.error != null && state.threads.isEmpty() -> ForumMessageCard(
                text = state.error,
                action = "Try again",
                onAction = viewModel::load
            )
            state.threads.isEmpty() -> ForumMessageCard(
                text = "No questions yet. Ask the community anything about this product.",
                action = "Ask the first question",
                onAction = viewModel::startAsking
            )
            else -> state.threads.forEach { thread ->
                QuestionCard(
                    thread = thread,
                    onOpen = { viewModel.openThread(thread) },
                    onVote = { viewModel.toggleVote(thread) },
                    onVoteReply = { reply -> viewModel.toggleVote(thread.id, reply) },
                    onReport = { reportTarget = ReportTarget(thread.id, null) }
                )
            }
        }
    }

    state.asking?.let { AskQuestionSheet(it, viewModel) }
    state.openThread?.let { open ->
        ThreadSheet(
            open = open,
            viewModel = viewModel,
            onReport = { replyId -> reportTarget = ReportTarget(open.thread.id, replyId) }
        )
    }
    reportTarget?.let { target ->
        ReportDialog(
            onDismiss = { reportTarget = null },
            onReport = { reason ->
                viewModel.report(target.threadId, target.replyId, reason)
                reportTarget = null
            }
        )
    }
}

@Composable
private fun ForumMessageCard(text: String, action: String, onAction: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(8.dp)) { Text(action) }
        }
    }
}

@Composable
private fun QuestionCard(
    thread: ForumThread,
    onOpen: () -> Unit,
    onVote: () -> Unit,
    onVoteReply: (ForumReply) -> Unit,
    onReport: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionTitle(thread.title)
            thread.topReply?.let { ReplyBlock(it, onVote = { onVoteReply(it) }) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                UpvotePill(upvotes = thread.upvotes, voted = thread.votedByMe, enabled = !thread.isMine, onClick = onVote)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when (thread.replyCount) {
                        0 -> "Be the first to reply"
                        1 -> "1 reply"
                        else -> "${thread.replyCount} replies"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!thread.isMine) {
                    IconButton(onClick = onReport, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Flag, contentDescription = "Report question", modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionTitle(title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).background(SecondaryFixed),
            contentAlignment = Alignment.Center
        ) {
            Text("Q", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A reply in the Stitch style: initial avatar, name, badge, time, then the text. */
@Composable
private fun ReplyBlock(reply: ForumReply, onVote: (() -> Unit)? = null, onReport: (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(8.dp), color = SurfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AuthorRow(name = reply.authorName, badge = reply.authorBadge, createdAt = reply.createdAt)
            Text(
                text = reply.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 30.dp)
            )
            if (onVote != null || onReport != null) {
                Row(modifier = Modifier.padding(start = 30.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    onVote?.let { UpvotePill(reply.upvotes, reply.votedByMe, enabled = !reply.isMine, onClick = it) }
                    Spacer(modifier = Modifier.weight(1f))
                    if (onReport != null && !reply.isMine) {
                        IconButton(onClick = onReport, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Flag, contentDescription = "Report reply", modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorRow(name: String, badge: String?, createdAt: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(text = name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        forumBadgeLabel(badge)?.let {
            Surface(shape = RoundedCornerShape(50), color = PrimaryFixed) {
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = Primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(text = formatTimeAgo(createdAt), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UpvotePill(upvotes: Int, voted: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = SurfaceContainer,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (voted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = if (voted) "Remove upvote" else "Upvote",
                tint = Primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (upvotes == 1) "1 upvote" else "$upvotes upvotes",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (voted) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ---------- ask a question ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskQuestionSheet(ask: AskQuestionState, viewModel: ProductForumViewModel) {
    ModalBottomSheet(
        onDismissRequest = viewModel::cancelAsking,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ask the community", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Other owners, sellers and buyers of this product can answer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = ask.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Your question") },
                supportingText = { Text("${ask.title.trim().length} / ${ProductForumViewModel.TITLE_MAX}") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = ask.details,
                onValueChange = viewModel::onDetailsChange,
                label = { Text("Details (optional)") },
                placeholder = { Text("Model, what you tried, what happens…") },
                supportingText = { Text("${ask.details.trim().length} / ${ProductForumViewModel.DETAILS_MAX}") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            ask.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = viewModel::cancelAsking, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = viewModel::submitQuestion,
                    enabled = !ask.isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (ask.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Post", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------- thread with replies ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadSheet(open: OpenThreadState, viewModel: ProductForumViewModel, onReport: (replyId: String?) -> Unit) {
    val thread = open.thread
    ModalBottomSheet(
        onDismissRequest = viewModel::closeThread,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuestionTitle(thread.title)
                        AuthorRow(name = thread.authorName, badge = thread.authorBadge, createdAt = thread.createdAt)
                        thread.body?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UpvotePill(thread.upvotes, thread.votedByMe, enabled = !thread.isMine,
                                onClick = { viewModel.toggleVote(thread) })
                            Spacer(modifier = Modifier.weight(1f))
                            if (!thread.isMine) {
                                TextButton(onClick = { onReport(null) }) { Text("Report") }
                            }
                        }
                        HorizontalDivider()
                        Text(
                            text = if (thread.replyCount == 1) "1 reply" else "${thread.replyCount} replies",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                items(open.replies, key = { it.id }) { reply ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ReplyBlock(
                            reply = reply,
                            onVote = { viewModel.toggleVote(thread.id, reply) },
                            onReport = { onReport(reply.id) }
                        )
                    }
                }
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            open.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            open.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(open.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { viewModel.openThread(thread) }) { Text("Try again") }
                            }
                            open.canLoadMore -> TextButton(onClick = viewModel::loadMoreReplies) { Text("Show more replies") }
                            open.replies.isEmpty() -> Text("No replies yet: share what you know.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            ReplyComposer(open, viewModel)
        }
    }
}

@Composable
private fun ReplyComposer(open: OpenThreadState, viewModel: ProductForumViewModel) {
    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = open.replyDraft,
                    onValueChange = viewModel::onReplyDraftChange,
                    placeholder = { Text("Write a reply…") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::sendReply, enabled = !open.isSending) {
                    if (open.isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply", tint = Primary)
                    }
                }
            }
            open.replyError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---------- report ----------

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onReport: (ForumReportReason) -> Unit) {
    var reason by remember { mutableStateOf(ForumReportReason.SPAM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report this post") },
        text = {
            Column {
                Text(
                    "A post reported by several people is hidden until a moderator checks it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ForumReportReason.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(selected = reason == option, onClick = { reason = option })
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onReport(reason) }) { Text("Report") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}