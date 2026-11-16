package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.ForumException
import com.hodowo.data.ForumReply
import com.hodowo.data.ForumReportReason
import com.hodowo.data.ForumService
import com.hodowo.data.ForumThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Thread opened from the product page, with its replies loaded page by page. */
data class OpenThreadState(
    val thread: ForumThread,
    val replies: List<ForumReply> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val replyDraft: String = "",
    val replyError: String? = null,
    val isSending: Boolean = false
) {
    val canLoadMore: Boolean get() = nextCursor != null && !isLoading
}

/** The "ask a question" composer. */
data class AskQuestionState(
    val title: String = "",
    val details: String = "",
    val error: String? = null,
    val isSending: Boolean = false
)

data class ProductForumUiState(
    val isLoading: Boolean = true,
    val threads: List<ForumThread> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
    /** Non-null while the composer is open. */
    val asking: AskQuestionState? = null,
    /** Non-null while a thread is open. */
    val openThread: OpenThreadState? = null,
    /** One-off feedback ("Thanks, we will review it"); cleared by [ProductForumViewModel.consumeMessage]. */
    val message: String? = null
)

/**
 * Community forum of one product (app #150, Backend#44): the questions about it with their
 * best reply, asking a question, reading and answering a thread, upvotes and reports.
 *
 * Text limits mirror the backend ones, so the user sees the problem before sending; the
 * backend checks them again.
 */
class ProductForumViewModel(
    private val service: ForumService,
    private val productType: String,
    private val productId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductForumUiState())
    val uiState: StateFlow<ProductForumUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = service.productThreads(productType, productId)
                _uiState.update { it.copy(isLoading = false, threads = page.threads, total = page.total) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, error = errorText(e, "Could not load the questions")) }
            }
        }
    }

    // ---------- ask ----------

    fun startAsking() {
        _uiState.update { it.copy(asking = AskQuestionState()) }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(asking = it.asking?.copy(title = title, error = null)) }
    }

    fun onDetailsChange(details: String) {
        _uiState.update { it.copy(asking = it.asking?.copy(details = details, error = null)) }
    }

    fun cancelAsking() {
        _uiState.update { it.copy(asking = null) }
    }

    fun submitQuestion() {
        val ask = _uiState.value.asking ?: return
        if (ask.isSending) return
        val error = questionError(ask.title, ask.details)
        if (error != null) {
            _uiState.update { it.copy(asking = ask.copy(error = error)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(asking = ask.copy(isSending = true, error = null)) }
            try {
                val thread = service.askAboutProduct(
                    productType, productId, ask.title.trim(), ask.details.trim().ifBlank { null }
                )
                _uiState.update {
                    it.copy(asking = null, threads = listOf(thread) + it.threads, total = it.total + 1,
                        message = "Question posted")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(asking = it.asking?.copy(isSending = false, error = errorText(e, "Could not post the question")))
                }
            }
        }
    }

    // ---------- open thread and replies ----------

    fun openThread(thread: ForumThread) {
        _uiState.update { it.copy(openThread = OpenThreadState(thread = thread)) }
        loadReplies(reset = true)
    }

    fun closeThread() {
        _uiState.update { it.copy(openThread = null) }
    }

    fun loadMoreReplies() {
        if (_uiState.value.openThread?.canLoadMore == true) loadReplies(reset = false)
    }

    private fun loadReplies(reset: Boolean) {
        val open = _uiState.value.openThread ?: return
        val threadId = open.thread.id
        val cursor = if (reset) null else open.nextCursor
        viewModelScope.launch {
            updateOpen(threadId) { it.copy(isLoading = true, error = null) }
            try {
                val page = service.replies(threadId, cursor)
                updateOpen(threadId) {
                    it.copy(
                        isLoading = false,
                        replies = if (reset) page.replies else it.replies + page.replies,
                        nextCursor = page.nextCursor
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                updateOpen(threadId) { it.copy(isLoading = false, error = errorText(e, "Could not load the replies")) }
            }
        }
    }

    fun onReplyDraftChange(text: String) {
        _uiState.update { it.copy(openThread = it.openThread?.copy(replyDraft = text, replyError = null)) }
    }

    fun sendReply() {
        val open = _uiState.value.openThread ?: return
        if (open.isSending) return
        val error = replyError(open.replyDraft)
        if (error != null) {
            updateOpen(open.thread.id) { it.copy(replyError = error) }
            return
        }
        val threadId = open.thread.id
        viewModelScope.launch {
            updateOpen(threadId) { it.copy(isSending = true, replyError = null) }
            try {
                val reply = service.reply(threadId, open.replyDraft.trim())
                updateOpen(threadId) {
                    // Replies are oldest first: the new one goes at the end, if that page is loaded.
                    val replies = if (it.nextCursor == null) it.replies + reply else it.replies
                    it.copy(replies = replies, replyDraft = "", isSending = false,
                        thread = it.thread.copy(replyCount = it.thread.replyCount + 1))
                }
                updateThread(threadId) { thread ->
                    thread.copy(replyCount = thread.replyCount + 1, topReply = thread.topReply ?: reply)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                updateOpen(threadId) { it.copy(isSending = false, replyError = errorText(e, "Could not send the reply")) }
            }
        }
    }

    // ---------- votes and reports ----------

    fun toggleVote(thread: ForumThread) {
        if (thread.isMine) return
        viewModelScope.launch {
            try {
                val vote = service.vote(thread.id, null, up = !thread.votedByMe)
                updateThread(thread.id) { it.copy(upvotes = vote.upvotes, votedByMe = vote.votedByMe) }
                updateOpen(thread.id) { it.copy(thread = it.thread.copy(upvotes = vote.upvotes, votedByMe = vote.votedByMe)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                showMessage(errorText(e, "Could not save your vote"))
            }
        }
    }

    fun toggleVote(threadId: String, reply: ForumReply) {
        if (reply.isMine) return
        viewModelScope.launch {
            try {
                val vote = service.vote(threadId, reply.id, up = !reply.votedByMe)
                val change: (ForumReply) -> ForumReply = {
                    if (it.id == reply.id) it.copy(upvotes = vote.upvotes, votedByMe = vote.votedByMe) else it
                }
                updateThread(threadId) { it.copy(topReply = it.topReply?.let(change)) }
                updateOpen(threadId) { it.copy(replies = it.replies.map(change)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                showMessage(errorText(e, "Could not save your vote"))
            }
        }
    }

    fun report(threadId: String, replyId: String?, reason: ForumReportReason) {
        viewModelScope.launch {
            showMessage(
                try {
                    service.report(threadId, replyId, reason)
                    "Thanks, a moderator will look at it"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    errorText(e, "Could not send the report")
                }
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ---------- helpers ----------

    private fun showMessage(text: String) {
        _uiState.update { it.copy(message = text) }
    }

    private fun updateThread(threadId: String, change: (ForumThread) -> ForumThread) {
        _uiState.update { state -> state.copy(threads = state.threads.map { if (it.id == threadId) change(it) else it }) }
    }

    private fun updateOpen(threadId: String, change: (OpenThreadState) -> OpenThreadState) {
        _uiState.update { state ->
            val open = state.openThread
            if (open != null && open.thread.id == threadId) state.copy(openThread = change(open)) else state
        }
    }

    companion object {
        const val TITLE_MIN = 8
        const val TITLE_MAX = 150
        const val DETAILS_MAX = 2000
        const val REPLY_MIN = 2
        const val REPLY_MAX = 2000

        /** Same limits as the backend (Backend#44), checked on the trimmed text. */
        fun questionError(title: String, details: String): String? {
            val length = title.trim().length
            return when {
                length < TITLE_MIN -> "Write a question of at least $TITLE_MIN characters"
                length > TITLE_MAX -> "The question can be at most $TITLE_MAX characters"
                details.trim().length > DETAILS_MAX -> "The details can be at most $DETAILS_MAX characters"
                else -> null
            }
        }

        fun replyError(body: String): String? {
            val length = body.trim().length
            return when {
                length < REPLY_MIN -> "Write a reply of at least $REPLY_MIN characters"
                length > REPLY_MAX -> "The reply can be at most $REPLY_MAX characters"
                else -> null
            }
        }

        private const val SIGN_IN_AGAIN = "Not authorized: please sign in again"

        private fun errorText(e: IOException, fallback: String): String = when {
            e is ForumException -> e.message ?: fallback
            e.message == SIGN_IN_AGAIN -> SIGN_IN_AGAIN
            else -> "$fallback, check your connection"
        }
    }
}