package com.hodowo.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.AuthManager
import com.hodowo.data.FirebaseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * User rating of an AI answer.
 */
enum class Feedback(val score: Int) {
    LIKE(1),
    DISLIKE(-1)
}

/**
 * Single step of a repair / troubleshooting manual.
 */
data class ManualStep(
    val number: Int,
    val title: String,
    val description: String
)

/**
 * Structured AI manual guide response.
 */
data class ManualGuide(
    val applianceName: String,
    val issueTitle: String,
    val overview: String,
    val steps: List<ManualStep> = emptyList(),
    val imageUrl: String? = null,
    val warnings: List<String> = emptyList(),
    val manualId: String? = null
)

/**
 * Message history turn sent to AI for conversation context memory.
 */
data class ChatMessageHistory(
    val role: String,
    val content: String
)

/**
 * A single chat message.
 */
data class Message(
    val id: Long,
    val text: String? = null,
    val imageUri: Uri? = null,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),

    /** The user question this answer replies to. */
    val question: String? = null,

    /** Rating given by the user to this answer, if any. */
    val feedback: Feedback? = null,

    /** Optional free-text reason attached to the rating. */
    val feedbackComment: String? = null,

    /** True while streaming. */
    val isStreaming: Boolean = false,

    /** Structured repair guide, if the AI resolved the issue to a manual guide. */
    val manualGuide: ManualGuide? = null,

    /** True if user bookmarked this manual guide. */
    val isBookmarked: Boolean = false
)

/**
 * Appliance identified from a photo.
 */
data class Appliance(
    val name: String,
    val brand: String?,
    val model: String?,
    val confidence: Float
)

/**
 * Chat conversation session metadata.
 */
data class ChatSession(
    val id: String,
    val title: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Agent streaming events.
 */
sealed class ChatStreamEvent {
    data class Step(val title: String, val detail: String = "") : ChatStreamEvent()
    data class Token(val token: String) : ChatStreamEvent()
    data class Response(
        val fullText: String,
        val manualGuide: ManualGuide? = null,
        val imageUrl: String? = null
    ) : ChatStreamEvent()
}

/**
 * UI State for the chat screen.
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val appliance: Appliance? = null,
    val isAnalyzing: Boolean = false,
    val isSending: Boolean = false,
    val currentStep: String? = null,
    val currentChatId: String = "session_${System.currentTimeMillis()}",
    val chatSessions: List<ChatSession> = emptyList(),
    val bookmarkedGuides: List<ManualGuide> = emptyList(),
    val selectedImageUri: Uri? = null,
    val error: String? = null
) {
    val isWaitingForAnswer: Boolean
        get() = isSending && messages.none { it.isStreaming && (!it.text.isNullOrBlank() || it.manualGuide != null) }
}

/**
 * Feedback report sent to backend and Firebase.
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
 * AI Service interface.
 */
interface HoDoWoAiService {
    suspend fun identifyAppliance(uri: Uri): Appliance
    suspend fun askQuestion(question: String, appliance: Appliance?): String
    suspend fun analyzePanel(uri: Uri, appliance: Appliance?): List<PanelControl>
    suspend fun sendFeedback(report: FeedbackReport)

    suspend fun setBookmark(manualId: String, isBookmarked: Boolean): Boolean = isBookmarked
    suspend fun rateManual(manualId: String, rating: Int): Int = rating

    fun streamAnswer(
        question: String,
        appliance: Appliance?
    ): Flow<String> = flow {
        emit(askQuestion(question, appliance))
    }

    fun streamAnswerEvents(
        question: String,
        appliance: Appliance?,
        imageUri: Uri? = null,
        history: List<ChatMessageHistory> = emptyList()
    ): Flow<ChatStreamEvent> = flow {
        streamAnswer(question, appliance).collect { piece ->
            emit(ChatStreamEvent.Token(piece))
        }
    }
}

/**
 * Rich demo service providing realistic progress streaming and manual guide cards.
 */
class DemoHoDoWoAiService : HoDoWoAiService {

    override suspend fun identifyAppliance(uri: Uri): Appliance {
        delay(800)
        return Appliance(
            name = "Washing machine",
            brand = "Bosch",
            model = "Serie 6",
            confidence = 0.96f
        )
    }

    override suspend fun askQuestion(
        question: String,
        appliance: Appliance?
    ): String {
        delay(600)
        val q = question.lowercase()
        return when {
            "e24" in q || "error" in q -> "This error code usually indicates the drain pump or hose is blocked."
            "start" in q -> "Make sure the appliance is plugged in and the door is firmly closed."
            "water" in q -> "Turn off the water tap immediately and check the inlet hose connections."
            appliance != null -> "Hello! How can I help you with your ${appliance.name}?"
            else -> "Hello! Please tell me which appliance you need help with."
        }
    }

    override suspend fun analyzePanel(uri: Uri, appliance: Appliance?): List<PanelControl> {
        delay(600)
        return listOf(
            PanelControl(
                id = "c1",
                label = "Start/Pause",
                type = ControlType.BUTTON,
                description = "Starts or pauses the selected program",
                box = ControlBox(x = 0.62f, y = 0.40f, w = 0.10f, h = 0.12f)
            ),
            PanelControl(
                id = "c2",
                label = "Temperature dial",
                type = ControlType.DIAL,
                description = "Selects the wash temperature",
                box = ControlBox(x = 0.10f, y = 0.20f, w = 0.12f, h = 0.12f)
            )
        )
    }

    override suspend fun sendFeedback(report: FeedbackReport) {
        delay(200)
    }

    override fun streamAnswer(
        question: String,
        appliance: Appliance?
    ): Flow<String> = flow {
        val words = askQuestion(question, appliance).split(" ")
        words.forEachIndexed { index, word ->
            delay(40L)
            emit(if (index == 0) word else " $word")
        }
    }

    override fun streamAnswerEvents(
        question: String,
        appliance: Appliance?,
        imageUri: Uri?,
        history: List<ChatMessageHistory>
    ): Flow<ChatStreamEvent> = flow {
        val isManualQuery = question.contains("e18", ignoreCase = true) ||
                question.contains("wash", ignoreCase = true) ||
                question.contains("fix", ignoreCase = true) ||
                question.contains("error", ignoreCase = true) ||
                question.contains("stop", ignoreCase = true) ||
                imageUri != null

        if (imageUri != null) {
            emit(ChatStreamEvent.Step("Identifying Appliance...", "Detecting appliance type, make and model markings"))
            delay(600)
        }

        emit(ChatStreamEvent.Step("Searching Manuals...", "Retrieving manufacturer repair instructions"))
        delay(700)

        if (isManualQuery) {
            emit(ChatStreamEvent.Step("Generating Visual Guide...", "Generating step-by-step visual diagram"))
            delay(700)

            val guide = ManualGuide(
                applianceName = if (appliance != null) "${appliance.brand.orEmpty()} ${appliance.name}".trim() else "Bosch Washing Machine",
                issueTitle = "Error E18: Drain Pump Clog & Filter Cleaning",
                overview = "Error E18 indicates the drain pump is blocked and cannot expel wastewater, typically resolved by cleaning the pump filter.",
                steps = listOf(
                    ManualStep(
                        number = 1,
                        title = "Place towel",
                        description = "Lay a shallow tray or absorbent towel under the bottom-right maintenance access flap."
                    ),
                    ManualStep(
                        number = 2,
                        title = "Unscrew filter",
                        description = "Slowly rotate the circular filter plug counter-clockwise 90° to drain residual water."
                    ),
                    ManualStep(
                        number = 3,
                        title = "Rinse and reseal",
                        description = "Pull out the cylindrical filter, clear debris or trapped coins under running water, and screw back tightly."
                    )
                ),
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBGU0w5Mr4RbGBYDru45lDQI4Nyx97xXyU41x0BWYDn9pfTndbv2igBkFJ-VXKhpShJUbPSEbi_loUXIzLKl758Z-19hEkSNn3PJzfkavcK_lZL1MQStj8TbiVhDPQRC2E-c3OrWTqmu7cG8_GISbburmbMEPJj13MZsKnVVgV0qJmOIQCPF0KoyFAaVaQvzb-HRxdv6gAT-jJSMq0Ywlhw0pJ8pvP-NhZp_iN7y7faoE0qWyESvL0ZV8Kc1YEAJ36qaQ"
            )

            emit(
                ChatStreamEvent.Response(
                    fullText = "Error E18 indicates the drain pump is blocked and cannot expel wastewater, typically resolved by cleaning the pump filter.",
                    manualGuide = guide,
                    imageUrl = guide.imageUrl
                )
            )
        } else {
            val words = askQuestion(question, appliance).split(" ")
            words.forEachIndexed { index, word ->
                delay(30L)
                emit(ChatStreamEvent.Token(if (index == 0) word else " $word"))
            }
            emit(ChatStreamEvent.Response(askQuestion(question, appliance)))
        }
    }
}

/**
 * Main ViewModel for Chat and telemetry state.
 */
class ChatViewModel(
    private val aiService: HoDoWoAiService = DemoHoDoWoAiService(),
    private val firebaseRepository: FirebaseRepository? = null,
    private val authManager: AuthManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messageId = 0L
    private var lastImageUri: Uri? = null

    private val pendingFeedback = LinkedHashMap<Long, FeedbackReport>()
    private val feedbackMutex = Mutex()

    init {
        // Load initial bookmarked manuals from Firebase if user is logged in
        refreshBookmarks()
    }

    fun setSelectedImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }

    fun clearSelectedImage() {
        _uiState.value = _uiState.value.copy(selectedImageUri = null)
    }

    /**
     * Starts a fresh chat session.
     */
    fun startNewChat() {
        val current = _uiState.value
        val sessions = if (current.messages.isNotEmpty()) {
            val title = current.messages.firstOrNull { it.isUser }?.text?.take(30)
                ?: current.appliance?.name
                ?: "Chat #${current.chatSessions.size + 1}"
            current.chatSessions + ChatSession(current.currentChatId, title)
        } else {
            current.chatSessions
        }

        _uiState.value = ChatUiState(
            currentChatId = "chat_${System.currentTimeMillis()}",
            chatSessions = sessions,
            appliance = null
        )
    }

    /**
     * Toggles bookmark for a manual guide message.
     */
    fun toggleBookmark(messageId: Long) {
        val target = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val newBookmarked = !target.isBookmarked

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { msg ->
                if (msg.id == messageId) msg.copy(isBookmarked = newBookmarked) else msg
            }
        )

        val userId = authManager?.currentUserId ?: "guest"
        viewModelScope.launch {
            if (newBookmarked) {
                firebaseRepository?.saveBookmark(userId, target.copy(isBookmarked = true))
            } else {
                firebaseRepository?.removeBookmark(userId, messageId)
            }
            target.manualGuide?.manualId?.let { manualId ->
                try {
                    aiService.setBookmark(manualId, newBookmarked)
                } catch (e: Exception) {
                    // Ignore network failure; local and firestore updated
                }
            }
            refreshBookmarks()
        }
    }

    fun refreshBookmarks() {
        val userId = authManager?.currentUserId ?: return
        viewModelScope.launch {
            val list = firebaseRepository?.getBookmarks(userId) ?: emptyList()
            _uiState.value = _uiState.value.copy(bookmarkedGuides = list)
        }
    }

    /**
     * Sends user message (with optional attached image).
     */
    fun sendTextMessage(text: String, attachedImageUri: Uri? = null) {
        val cleanText = text.trim()
        val imageToAttach = attachedImageUri ?: _uiState.value.selectedImageUri

        if (cleanText.isEmpty() && imageToAttach == null) {
            return
        }

        // Build conversation history from previous completed messages
        val history = _uiState.value.messages
            .filterNot { it.isStreaming }
            .mapNotNull { msg ->
                val content = msg.text?.takeIf { it.isNotBlank() } ?: msg.manualGuide?.overview
                if (!content.isNullOrBlank()) {
                    ChatMessageHistory(
                        role = if (msg.isUser) "user" else "assistant",
                        content = content
                    )
                } else null
            }

        // Clear selected image preview
        _uiState.value = _uiState.value.copy(selectedImageUri = null)

        addMessage(
            Message(
                id = nextMessageId(),
                text = cleanText.ifEmpty { null },
                imageUri = imageToAttach,
                isUser = true
            )
        )

        val answerId = nextMessageId()

        addMessage(
            Message(
                id = answerId,
                text = "",
                isUser = false,
                question = cleanText.ifEmpty { "Identify and analyze appliance" },
                isStreaming = true
            )
        )

        _uiState.value = _uiState.value.copy(
            isSending = true,
            currentStep = null,
            error = null
        )

        viewModelScope.launch {
            try {
                aiService.streamAnswerEvents(
                    question = cleanText,
                    appliance = _uiState.value.appliance,
                    imageUri = imageToAttach,
                    history = history
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Step -> {
                            val friendlyText = when {
                                event.title.contains("appliance_identification", ignoreCase = true) -> "Identifying appliance model..."
                                event.title.contains("search_manuals", ignoreCase = true) -> "Searching repair manuals..."
                                event.title.contains("panel", ignoreCase = true) -> "Analyzing control panel..."
                                else -> event.title
                            }
                            _uiState.update { it.copy(currentStep = friendlyText, isSending = true) }
                        }
                        is ChatStreamEvent.Token -> {
                            appendToMessage(answerId, event.token)
                        }
                        is ChatStreamEvent.Response -> {
                            setFinalMessageContent(answerId, event.fullText, event.manualGuide)
                            // If guide was returned, persist it to Firebase
                            event.manualGuide?.let { guide ->
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        appliance = Appliance(
                                            name = guide.applianceName,
                                            brand = null,
                                            model = null,
                                            confidence = 1.0f
                                        )
                                    )
                                }
                                val userId = authManager?.currentUserId ?: "guest"
                                firebaseRepository?.saveManualGuide(userId, answerId, guide)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Error while sending the message."
                )
            } finally {
                finishStreaming(answerId)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    currentStep = null
                )
                // Persist chat to Firebase
                val userId = authManager?.currentUserId ?: "guest"
                firebaseRepository?.saveChatMessages(
                    userId = userId,
                    chatId = _uiState.value.currentChatId,
                    messages = _uiState.value.messages,
                    appliance = _uiState.value.appliance
                )
            }
        }
    }

    private fun setFinalMessageContent(id: Long, text: String, manualGuide: ManualGuide?) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { current ->
                if (current.id == id) {
                    current.copy(
                        text = if (current.text.isNullOrBlank()) text else current.text,
                        manualGuide = manualGuide ?: current.manualGuide
                    )
                } else {
                    current
                }
            }
        )
    }

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

    private fun finishStreaming(id: Long) {
        val messages = _uiState.value.messages
        val message = messages.firstOrNull { it.id == id } ?: return

        _uiState.value = _uiState.value.copy(
            messages = if (message.text.isNullOrBlank() && message.manualGuide == null) {
                messages.filterNot { it.id == id }
            } else {
                messages.map { current ->
                    if (current.id == id) current.copy(isStreaming = false) else current
                }
            }
        )
    }

    fun analyzeImage(uri: Uri) {
        lastImageUri = uri

        addMessage(
            Message(
                id = nextMessageId(),
                imageUri = uri,
                isUser = true
            )
        )

        _uiState.value = _uiState.value.copy(
            isAnalyzing = true,
            currentStep = "Analyzing uploaded appliance photo...",
            error = null
        )

        viewModelScope.launch {
            try {
                val appliance = aiService.identifyAppliance(uri)
                _uiState.value = _uiState.value.copy(
                    appliance = appliance
                )

                addMessage(
                    Message(
                        id = nextMessageId(),
                        text = buildString {
                            append("I identified: ${appliance.name}")
                            appliance.brand?.let { append("\nBrand: $it") }
                            appliance.model?.let { append("\nModel: $it") }
                            append("\nConfidence: ${(appliance.confidence * 100).toInt()}%")
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
                    isAnalyzing = false,
                    currentStep = null
                )
            }
        }
    }

    fun debugAnalyzePanel() {
        val uri = lastImageUri
        if (uri == null) {
            _uiState.value = _uiState.value.copy(
                error = "Take a photo first, then tap the detect button."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isAnalyzing = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val controls = aiService.analyzePanel(
                    uri = uri,
                    appliance = _uiState.value.appliance
                )

                addMessage(
                    Message(
                        id = nextMessageId(),
                        text = buildControlsText(controls),
                        isUser = false
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Error while detecting the controls."
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false
                )
            }
        }
    }

    private fun buildControlsText(controls: List<PanelControl>): String {
        if (controls.isEmpty()) {
            return "No controls detected in the photo."
        }

        return buildString {
            append("Detected ${controls.size} control(s):")
            controls.forEach { control ->
                append("\n• ${control.label} (${control.type.name.lowercase()})")
                append(
                    " — x=${"%.2f".format(control.box.x)}" +
                            ", y=${"%.2f".format(control.box.y)}" +
                            ", w=${"%.2f".format(control.box.w)}" +
                            ", h=${"%.2f".format(control.box.h)}"
                )
            }
        }
    }

    fun setFeedback(
        messageId: Long,
        feedback: Feedback,
        comment: String? = null
    ) {
        val message = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.isStreaming) return

        val question = message.question ?: return
        val answer = message.text ?: message.manualGuide?.overview ?: return
        val cleanComment = comment?.trim()?.takeIf { it.isNotEmpty() }

        if (message.feedback == feedback && cleanComment == null) return

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

        // Sync with Firebase
        val userId = authManager?.currentUserId ?: "guest"
        viewModelScope.launch {
            firebaseRepository?.saveFeedback(userId, report)
        }

        // Call backend rating endpoint if manualId is present
        message.manualGuide?.manualId?.let { manualId ->
            viewModelScope.launch {
                try {
                    aiService.rateManual(manualId, feedback.score)
                } catch (e: Exception) {
                    // Ignore network failure
                }
            }
        }

        viewModelScope.launch {
            submitFeedback(report)
        }
    }

    private suspend fun submitFeedback(report: FeedbackReport) {
        feedbackMutex.withLock {
            pendingFeedback[report.messageId] = report
            try {
                val iterator = pendingFeedback.values.iterator()
                while (iterator.hasNext()) {
                    aiService.sendFeedback(iterator.next())
                    iterator.remove()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Pending feedback stays queued for retry
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
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