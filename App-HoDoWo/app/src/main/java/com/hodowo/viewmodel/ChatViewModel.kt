package com.hodowo.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * User rating of an AI answer.
 *
 * [score] is the numeric value that will be stored in the "rating" field
 * of the ai_response_ratings table.
 */
enum class Feedback(val score: Int) {
    LIKE(1),
    DISLIKE(-1)
}

/**
 * A single chat message, written either by the user or by the AI.
 */
data class Message(
    val id: Long,
    val text: String? = null,
    val imageUri: Uri? = null,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * The user question this answer replies to.
     * Only set for Q&A answers: it is stored as prompt_query together with
     * the rating, and it is also what makes the feedback buttons visible.
     */
    val question: String? = null,

    /** Rating given by the user to this answer, if any. */
    val feedback: Feedback? = null,

    /** Optional free-text reason attached to the rating. */
    val feedbackComment: String? = null,

    /**
     * True while the AI answer is still arriving piece by piece (#83).
     * A message that is still streaming cannot be rated.
     */
    val isStreaming: Boolean = false
)

/**
 * Appliance identified from a photo.
 * [confidence] goes from 0.0 to 1.0.
 */
data class Appliance(
    val name: String,
    val brand: String?,
    val model: String?,
    val confidence: Float
)

/**
 * Everything the chat screen needs to render itself.
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val appliance: Appliance? = null,

    /** True while a photo is being analyzed. */
    val isAnalyzing: Boolean = false,

    /** True while waiting for the AI answer to a text question. */
    val isSending: Boolean = false,

    val error: String? = null
) {

    /**
     * True while the answer has been requested but no text has arrived yet:
     * the screen shows "HoDoWo is thinking..." only in this phase.
     */
    val isWaitingForAnswer: Boolean
        get() = isSending && messages.none { it.isStreaming && !it.text.isNullOrBlank() }
}

/**
 * Data sent to the backend for each rating
 * (it matches the fields of the ai_response_ratings table).
 *
 * The user is NOT included: the backend gets it from the login token.
 */
data class FeedbackReport(
    val messageId: Long,
    val appliance: Appliance?,
    val question: String,
    val answer: String,
    val feedback: Feedback,
    val comment: String?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * For now we use a local/demo service.
 *
 * Later this interface will be connected to the real HoDoWo backend.
 */
interface HoDoWoAiService {

    /** Identifies the appliance shown in the photo at [uri]. */
    suspend fun identifyAppliance(uri: Uri): Appliance

    /** Answers a user [question], using the identified [appliance] as context. */
    suspend fun askQuestion(
        question: String,
        appliance: Appliance?
    ): String

    /**
     * Sends the user's rating of an answer to the backend.
     * Must throw an exception if the request fails, so that the ViewModel
     * can retry later.
     */
    suspend fun sendFeedback(report: FeedbackReport)

    /**
     * Streams the answer to [question] piece by piece (#83).
     *
     * Default: the whole answer as a single piece, so a service without
     * streaming support (e.g. the current backend, see #84) keeps working.
     */
    fun streamAnswer(
        question: String,
        appliance: Appliance?
    ): Flow<String> = flow {
        emit(askQuestion(question, appliance))
    }
}

/**
 * Working mock that lets us develop the UI without a backend.
 */
class DemoHoDoWoAiService : HoDoWoAiService {

    override suspend fun identifyAppliance(uri: Uri): Appliance {
        // Simulate the network and model latency.
        delay(1200)

        // Always returns the same fake result.
        return Appliance(
            name = "Washing machine",
            brand = "Bosch",
            model = null,
            confidence = 0.94f
        )
    }

    override suspend fun askQuestion(
        question: String,
        appliance: Appliance?
    ): String {

        // Simulate the network and model latency.
        delay(900)

        val applianceName =
            appliance?.name ?: "appliance"

        // Canned answers based on simple keyword matching (English UI text).
        return when {
            question.contains("error", ignoreCase = true) ->
                "For the $applianceName, tell me the error code shown on the display."

            question.contains("won't start", ignoreCase = true) ->
                "Check that the $applianceName is plugged in and that the door is properly closed."

            question.contains("water", ignoreCase = true) ->
                "Check the water tap and the inlet hose."

            else ->
                "I looked at your question about the $applianceName. Tell me the specific problem you are having."
        }
    }

    override suspend fun sendFeedback(report: FeedbackReport) {
        // Simulates the network call: the real backend will expose POST /feedback.
        delay(300)
    }

    /** Sends the canned answer word by word, like a real stream would. */
    override fun streamAnswer(
        question: String,
        appliance: Appliance?
    ): Flow<String> = flow {

        val words = askQuestion(question, appliance).split(" ")

        words.forEachIndexed { index, word ->
            delay(STREAM_DELAY_MS)
            emit(if (index == 0) word else " $word")
        }
    }

    private companion object {
        // Pause between two words of the demo stream.
        const val STREAM_DELAY_MS = 60L
    }
}

/**
 * Holds the chat state and handles the user actions
 * (send a question, analyze a photo, rate an answer).
 */
class ChatViewModel(
    private val aiService: HoDoWoAiService = DemoHoDoWoAiService()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Last message id handed out (ids are unique and increasing).
    private var messageId = 0L

    /**
     * Ratings not yet delivered to the backend (at most one per message).
     * If the user changes their mind, the new rating replaces the queued one.
     */
    private val pendingFeedback = LinkedHashMap<Long, FeedbackReport>()

    // Guards pendingFeedback and prevents parallel sends.
    private val feedbackMutex = Mutex()

    /**
     * Adds the user's question to the chat and asks the AI for an answer.
     * Blank messages are ignored.
     */
    fun sendTextMessage(text: String) {

        val cleanText = text.trim()

        if (cleanText.isEmpty()) {
            return
        }

        addMessage(
            Message(
                id = nextMessageId(),
                text = cleanText,
                isUser = true
            )
        )

        // Empty AI answer, filled while the pieces of the answer arrive.
        // It keeps the question so it can be rated once complete.
        val answerId = nextMessageId()

        addMessage(
            Message(
                id = answerId,
                text = "",
                isUser = false,
                question = cleanText,
                isStreaming = true
            )
        )

        _uiState.value = _uiState.value.copy(
            isSending = true,
            error = null
        )

        viewModelScope.launch {

            try {

                aiService.streamAnswer(
                    question = cleanText,
                    appliance = _uiState.value.appliance
                ).collect { piece ->
                    appendToMessage(answerId, piece)
                }

            } catch (e: CancellationException) {

                // Never swallow coroutine cancellation.
                throw e

            } catch (e: Exception) {

                // Any text received before the error stays visible.
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Error while sending the message."
                )

            } finally {

                finishStreaming(answerId)

                _uiState.value = _uiState.value.copy(
                    isSending = false
                )
            }
        }
    }

    /** Adds a piece of the streamed answer to the message [id]. */
    private fun appendToMessage(id: Long, piece: String) {

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { current ->
                if (current.id == id) {
                    current.copy(text = current.text.orEmpty() + piece)
                } else {
                    current
                }
            }
        )
    }

    /**
     * Marks the streamed answer [id] as complete.
     * An answer that received no text at all (e.g. error before the first
     * piece) is removed, so no empty bubble is left in the chat.
     */
    private fun finishStreaming(id: Long) {

        val messages = _uiState.value.messages
        val message = messages.firstOrNull { it.id == id } ?: return

        _uiState.value = _uiState.value.copy(
            messages =
                if (message.text.isNullOrBlank()) {
                    messages.filterNot { it.id == id }
                } else {
                    messages.map { current ->
                        if (current.id == id) current.copy(isStreaming = false) else current
                    }
                }
        )
    }

    /**
     * Adds the photo to the chat, identifies the appliance in it and
     * stores the result in the state so later questions can use it as context.
     */
    fun analyzeImage(uri: Uri) {

        addMessage(
            Message(
                id = nextMessageId(),
                imageUri = uri,
                isUser = true
            )
        )

        _uiState.value = _uiState.value.copy(
            isAnalyzing = true,
            error = null
        )

        viewModelScope.launch {

            try {

                val appliance = aiService.identifyAppliance(uri)

                _uiState.value = _uiState.value.copy(
                    appliance = appliance
                )

                // Build a readable summary of the identification result.
                addMessage(
                    Message(
                        id = nextMessageId(),
                        text = buildString {

                            append("I identified: ${appliance.name}")

                            appliance.brand?.let {
                                append("\nBrand: $it")
                            }

                            appliance.model?.let {
                                append("\nModel: $it")
                            }

                            append(
                                "\nConfidence: ${
                                    (appliance.confidence * 100).toInt()
                                }%"
                            )
                        },
                        isUser = false
                    )
                )

            } catch (e: Exception) {

                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Error while analyzing the image."
                )

            } finally {

                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false
                )
            }
        }
    }

    /**
     * Records the user's rating of an answer and sends it to the backend.
     *
     * - The rating shows up in the UI immediately (optimistic update).
     * - If the request fails, the rating stays in a queue and is retried
     *   with the next rating, without showing any error to the user.
     * - [comment] is optional (typically the reason for a "not useful" vote).
     */
    fun setFeedback(
        messageId: Long,
        feedback: Feedback,
        comment: String? = null
    ) {

        val message = _uiState.value.messages
            .firstOrNull { it.id == messageId }
            ?: return

        // An answer that is still arriving cannot be rated yet.
        if (message.isStreaming) {
            return
        }

        // Only Q&A answers can be rated (they are the ones with a linked question).
        val question = message.question ?: return
        val answer = message.text ?: return

        val cleanComment = comment?.trim()?.takeIf { it.isNotEmpty() }

        // Same rating already recorded and no new comment: nothing to do.
        if (message.feedback == feedback && cleanComment == null) {
            return
        }

        // Update the message in the UI right away.
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { current ->

                if (current.id == messageId) {
                    current.copy(
                        feedback = feedback,
                        feedbackComment = cleanComment
                    )
                } else {
                    current
                }
            }
        )

        val report = FeedbackReport(
            messageId = messageId,
            appliance = _uiState.value.appliance,
            question = question,
            answer = answer,
            feedback = feedback,
            comment = cleanComment
        )

        viewModelScope.launch {
            submitFeedback(report)
        }
    }

    /**
     * Queues the rating and tries to send the whole queue.
     * The Mutex avoids parallel sends when the user rates several
     * messages in a row.
     */
    private suspend fun submitFeedback(report: FeedbackReport) {

        feedbackMutex.withLock {

            pendingFeedback[report.messageId] = report

            try {

                val iterator = pendingFeedback.values.iterator()

                while (iterator.hasNext()) {

                    aiService.sendFeedback(iterator.next())

                    // Removed from the queue only after a successful send.
                    iterator.remove()
                }

            } catch (e: CancellationException) {

                // Never swallow coroutine cancellation.
                throw e

            } catch (_: Exception) {

                // Send failed: the ratings stay in pendingFeedback
                // and will be retried with the next rating.
            }
        }
    }

    /** Hides the error currently shown on screen. */
    fun clearError() {

        _uiState.value = _uiState.value.copy(
            error = null
        )
    }

    private fun addMessage(message: Message) {

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message
        )
    }

    private fun nextMessageId(): Long {
        messageId++
        return messageId
    }
}