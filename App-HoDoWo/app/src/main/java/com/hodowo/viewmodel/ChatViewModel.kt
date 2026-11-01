package com.hodowo.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.AuthManager
import com.hodowo.data.FirebaseRepository
import com.hodowo.data.ProfilePhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    val lastUpdated: Long = System.currentTimeMillis(),
    /** Appliance (model) the chat was about, when it was recognised. */
    val applianceName: String? = null
)

/**
 * User registered / owned appliance entity stored in Firestore under users/{userId}/appliances/{applianceId}.
 */
data class UserAppliance(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    val category: String? = null,
    val manualCount: Int = 1,
    val subtitle: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * User profile entity stored in Firestore under users/{userId}.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val memberSince: Long = System.currentTimeMillis()
)

/**
 * State for the appliance manuals screen: one appliance plus every guide related to it.
 */
data class ApplianceManualsUiState(
    val id: String,
    val name: String,
    val brand: String?,
    val model: String?,
    val isRegistered: Boolean,
    val manuals: List<ManualGuide> = emptyList(),
    val loading: Boolean = true
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
    val userAppliances: List<UserAppliance> = emptyList(),
    val userProfile: UserProfile? = null,
    val selectedImageUri: Uri? = null,

    /**
     * Photo the panel analysis (S1-14) runs on. It is what the interactive
     * overlay (S1-15) shows the detected controls on top of.
     */
    val panelImageUri: Uri? = null,

    /** Controls detected on [panelImageUri]; empty when there are none. */
    val panelControls: List<PanelControl> = emptyList(),

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

    /** Answers a user [question], using the identified [appliance] as context. */
    suspend fun askQuestion(
        question: String,
        appliance: Appliance?
    ): String

    /**
     * Locates the control panel inside the full photo at [uri] (S1-16).
     * Returns null when the photo shows no panel.
     */
    suspend fun locatePanel(uri: Uri): PanelBox?

    /**
     * Detects the buttons, dials and other controls in the cropped panel photo
     * at [uri], using the identified [appliance] as context.
     * Returns an empty list when the backend finds no controls.
     */
    suspend fun analyzePanel(uri: Uri, appliance: Appliance?): List<PanelControl>
    suspend fun sendFeedback(report: FeedbackReport)

    suspend fun setBookmark(manualId: String, isBookmarked: Boolean): Boolean = isBookmarked
    suspend fun rateManual(manualId: String, rating: Int): Int = rating

    /** Newest manuals of the signed-in user, for "Recent Manuals" on the Home screen. */
    suspend fun recentManuals(limit: Int): List<RecentManual> = emptyList()

    /** Most searched and most saved guides of all users, for "Most popular" on the Home screen. */
    suspend fun popularManuals(limit: Int): List<RecentManual> = emptyList()

    /** Today's energy-saving tip for the Home screen, or null when unavailable. */
    suspend fun dailyTip(): String? = null

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

    /**
     * The demo has no vision model, so it never finds a panel and the overlay
     * shows the whole photo.
     */
    override suspend fun locatePanel(uri: Uri): PanelBox? = null

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

    override suspend fun dailyTip(): String = "Run washer after 10:00 PM tonight to save on power."

    /** The mockup manuals again, with example search and save counts. */
    override suspend fun popularManuals(limit: Int): List<RecentManual> =
        recentManuals(limit).mapIndexed { index, manual ->
            manual.copy(searchCount = 120 - index * 35, saveCount = 40 - index * 15)
        }

    /** The two manuals of the Home mockup, so demo mode shows a realistic Home screen. */
    override suspend fun recentManuals(limit: Int): List<RecentManual> {
        delay(300)
        val now = System.currentTimeMillis()
        return listOf(
            RecentManual(
                guide = ManualGuide(
                    applianceName = "Bosch Washer",
                    issueTitle = "Error E18: Drain Pump Clog",
                    overview = "Follow these steps to safely access and rinse the drain pump " +
                            "filter cap to resolve water drainage issues.",
                    steps = listOf(
                        ManualStep(1, "Place towel", "Lay a towel under the bottom-right maintenance flap."),
                        ManualStep(2, "Rotate filter", "Turn the filter cap counter-clockwise to drain the water."),
                        ManualStep(3, "Pull & rinse filter", "Pull out the filter, rinse it and screw it back tightly.")
                    ),
                    manualId = "demo-1"
                ),
                createdAtMillis = now - 2 * 60 * 60 * 1000L
            ),
            RecentManual(
                guide = ManualGuide(
                    applianceName = "De'Longhi Coffee Maker",
                    issueTitle = "Descaling Routine & Flush",
                    overview = "Clean calcification deposits from the water lines using standard " +
                            "eco-descaler solution and a 2-cycle hot water purge.",
                    steps = listOf(
                        ManualStep(1, "Add descaler", "Fill the tank with water and the descaling solution."),
                        ManualStep(2, "Run the cycle", "Start the descaling program and wait until it ends."),
                        ManualStep(3, "Flush twice", "Rinse the tank and run two cycles with clean hot water.")
                    ),
                    manualId = "demo-2"
                ),
                createdAtMillis = now - 26 * 60 * 60 * 1000L
            )
        ).take(limit)
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
    private val authManager: AuthManager? = null,
    val panelCropper: PanelCropper = PanelCropper.None,
    /** Keeps the chats on the phone for the history menu; null keeps them only in memory. */
    private val chatHistory: ChatHistoryStore? = null,
    /** Keeps the profile photo on the phone when Firebase Storage is unavailable. */
    private val profilePhotoStore: ProfilePhotoStore? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isUploadingPhoto = MutableStateFlow(false)
    val isUploadingPhoto: StateFlow<Boolean> = _isUploadingPhoto.asStateFlow()

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private val _applianceManuals = MutableStateFlow<ApplianceManualsUiState?>(null)
    val applianceManuals: StateFlow<ApplianceManualsUiState?> = _applianceManuals.asStateFlow()

    private var messageId = 0L
    private var lastImageUri: Uri? = null

    private val pendingFeedback = LinkedHashMap<Long, FeedbackReport>()
    private val feedbackMutex = Mutex()

    /** All past chats (with their messages), newest first. */
    private var savedChats: List<SavedChat> = emptyList()

    private val userId: String
        get() = authManager?.currentUserId ?: GUEST_USER

    /** User whose chats are in [savedChats]. */
    private var historyUser: String = userId

    init {
        // Load initial bookmarked manuals, appliances & user profile from Firebase if user is logged in
        refreshBookmarks()
        refreshChatHistory()
        refreshAppliances()
        refreshUserProfile()
        observeSignedInUser()
    }

    /**
     * Sign in / sign out from any screen: forget the data of the previous user
     * and load the data of the new one (or of the guest).
     */
    private fun observeSignedInUser() {
        val auth = authManager ?: return
        viewModelScope.launch {
            auth.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .drop(1) // The current user was loaded above.
                .collect { onUserChanged() }
        }
    }

    /** Clears every per-user state and reloads it for the user now signed in. */
    private fun onUserChanged() {
        // The open chat is already saved for the previous user after each answer.
        savedChats = emptyList()
        lastImageUri = null
        _applianceManuals.value = null
        _uiState.value = ChatUiState(currentChatId = "chat_${clock()}")
        refreshBookmarks()
        refreshChatHistory()
        refreshAppliances()
        refreshUserProfile()
    }

    /** Reloads the chat history of the current user (e.g. after signing in). */
    fun refreshChatHistory() {
        val store = chatHistory ?: return
        val user = userId
        viewModelScope.launch {
            val chats = try {
                store.load(user)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            // Same user: keep the chats saved while the file was being read.
            // Another user (sign in / out): show only that user's chats.
            savedChats = if (user == historyUser) {
                chats.fold(savedChats) { all, chat -> if (all.any { it.id == chat.id }) all else all.upsert(chat) }
            } else {
                chats
            }
            historyUser = user
            publishSessions()
        }
    }

    /**
     * Opens a past chat from the history menu. The current chat is saved first.
     * Ignored while an answer is still arriving.
     */
    fun openChat(chatId: String) {
        val current = _uiState.value
        if (current.isSending || chatId == current.currentChatId) return
        val chat = savedChats.firstOrNull { it.id == chatId } ?: return

        saveCurrentChat()

        messageId = maxOf(messageId, chat.messages.maxOfOrNull { it.id } ?: 0L)
        lastImageUri = chat.messages.lastOrNull { it.imageUri != null }?.imageUri
        _uiState.value = ChatUiState(
            messages = chat.messages,
            appliance = chat.applianceName?.let { Appliance(name = it, brand = null, model = null, confidence = 1f) },
            currentChatId = chat.id,
            chatSessions = _uiState.value.chatSessions,
            bookmarkedGuides = current.bookmarkedGuides,
            // The profile and the registered appliances belong to the user, not to the chat.
            userAppliances = current.userAppliances,
            userProfile = current.userProfile
        )
    }

    /** Removes a chat from the history; deleting the open chat starts a new one. */
    fun deleteChat(chatId: String) {
        savedChats = savedChats.filterNot { it.id == chatId }
        if (chatId == _uiState.value.currentChatId) {
            resetToNewChat()
        }
        publishSessions()

        val store = chatHistory ?: return
        val user = userId
        viewModelScope.launch {
            try {
                store.delete(user, chatId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The chat is already gone from the screen; the file is fixed on the next save.
            }
        }
    }

    /** Saves the open chat in the history (in memory at once, on the phone in the background). */
    private fun saveCurrentChat() {
        val state = _uiState.value
        val chat = savedChatOf(state.currentChatId, state.messages, state.appliance) ?: return
        savedChats = savedChats.upsert(chat)
        publishSessions()

        val store = chatHistory ?: return
        val user = userId
        viewModelScope.launch {
            try {
                store.save(user, chat)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keeping the history is best effort: the chat itself still works.
            }
        }
    }

    private fun publishSessions() {
        _uiState.update { it.copy(chatSessions = savedChats.map(SavedChat::toSession)) }
    }

    private fun resetToNewChat() {
        val current = _uiState.value
        lastImageUri = null
        _uiState.value = ChatUiState(
            currentChatId = "chat_${clock()}",
            chatSessions = current.chatSessions,
            bookmarkedGuides = current.bookmarkedGuides,
            userAppliances = current.userAppliances,
            userProfile = current.userProfile
        )
    }

    fun setSelectedImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }

    fun clearSelectedImage() {
        _uiState.value = _uiState.value.copy(selectedImageUri = null)
    }

    /**
     * Starts a fresh chat session. The current chat (if the user asked something)
     * is kept in the history menu.
     */
    fun startNewChat() {
        saveCurrentChat()
        resetToNewChat()
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

    fun refreshAppliances() {
        val userId = currentUid()
        if (userId == null) {
            // Guest: appliances are kept only on screen until the app is closed.
            return
        }
        viewModelScope.launch {
            val list = firebaseRepository?.getAppliances(userId) ?: emptyList()
            if (currentUid() == userId) {
                _uiState.update { it.copy(userAppliances = list) }
            }
        }
    }

    fun refreshUserProfile() {
        val userId = currentUid()
        if (userId == null) {
            // Guest: no photo and no name, so the screens show the default avatar.
            _uiState.update { it.copy(userProfile = null) }
            return
        }
        viewModelScope.launch {
            val profile = firebaseRepository?.getUserProfile(userId)
            // The phone keeps the photo when Firebase Storage is not configured.
            val localPhoto = profilePhotoStore?.currentUrl(userId)
            val shown = when {
                profile == null -> localPhoto?.let { UserProfile(userId, "", "", it) }
                profile.photoUrl.isNullOrBlank() && localPhoto != null -> profile.copy(photoUrl = localPhoto)
                else -> profile
            }
            if (currentUid() == userId) {
                _uiState.update { it.copy(userProfile = shown) }
            }
        }
    }

    /**
     * Adds an appliance to the profile.
     * Returns true when it is saved to the user's account, false when there is no
     * signed-in user (it is then shown only until the app is closed) or the name is blank.
     */
    fun addAppliance(
        name: String,
        brand: String? = null,
        model: String? = null,
        category: String? = null,
        room: String? = null,
        subtitle: String? = null
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        val appliance = UserAppliance(
            name = cleanName,
            brand = brand?.trim()?.ifEmpty { null },
            model = model?.trim()?.ifEmpty { null },
            category = category?.trim()?.ifEmpty { null },
            subtitle = subtitle?.trim() ?: listOfNotNull(room?.trim()?.ifEmpty { null }, "Registered appliance").joinToString(" • "),
            manualCount = 1
        )
        _uiState.update { it.copy(userAppliances = it.userAppliances + appliance) }
        val userId = currentUid() ?: return false
        viewModelScope.launch {
            if (firebaseRepository?.saveAppliance(userId, appliance) == true) {
                refreshAppliances()
            } else {
                // Keep it on screen instead of losing it silently.
                _userMessages.emit("Couldn't save the appliance. Check your connection.")
            }
        }
        return true
    }

    fun deleteAppliance(applianceId: String) {
        _uiState.update { state ->
            state.copy(userAppliances = state.userAppliances.filterNot { it.id == applianceId })
        }
        val userId = currentUid() ?: return
        viewModelScope.launch {
            firebaseRepository?.deleteAppliance(userId, applianceId)
            refreshAppliances()
        }
    }

    fun updateUserDisplayName(displayName: String) {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) return
        val uid = currentUid() ?: return
        // Update UI immediately, preserving the current photo / memberSince.
        _uiState.update { state ->
            state.copy(
                userProfile = state.userProfile?.copy(displayName = cleanName)
                    ?: UserProfile(uid, cleanName, authManager?.currentUser?.value?.email.orEmpty())
            )
        }
        viewModelScope.launch {
            try {
                authManager?.updateProfile(cleanName)
            } catch (_: Exception) {}
            if (firebaseRepository?.updateProfileFields(uid, displayName = cleanName) != true) {
                _userMessages.emit("Couldn't save your name. Check your connection.")
            }
        }
    }

    /** Uploads the picked image, stores its URL in users/{uid}.photoUrl and refreshes the UI. */
    fun updateProfilePhoto(uri: Uri) {
        val uid = currentUid() ?: return
        viewModelScope.launch {
            _isUploadingPhoto.value = true
            val cloudUrl = firebaseRepository?.uploadProfilePhoto(uid, uri)
            val savedUrl = if (
                cloudUrl != null &&
                firebaseRepository?.updateProfileFields(uid, photoUrl = cloudUrl) == true
            ) {
                cloudUrl
            } else {
                // Firebase Storage is not configured: keep the photo on this phone.
                profilePhotoStore?.save(uid, uri)
            }
            if (savedUrl != null) {
                _uiState.update { state ->
                    state.copy(
                        userProfile = state.userProfile?.copy(photoUrl = savedUrl)
                            ?: UserProfile(uid, "", "", savedUrl)
                    )
                }
                _userMessages.emit(
                    if (cloudUrl != null) "Profile photo updated"
                    else "Profile photo saved on this device"
                )
            } else {
                _userMessages.emit("Couldn't upload the photo. Try again.")
            }
            _isUploadingPhoto.value = false
        }
    }

    /** Called when an owned appliance is tapped; the screen then navigates to ApplianceManualsScreen. */
    fun openApplianceManuals(id: String, name: String, brand: String?, model: String?, isRegistered: Boolean) {
        _applianceManuals.value = ApplianceManualsUiState(id, name, brand, model, isRegistered)
        viewModelScope.launch {
            val uid = currentUid()
            val manuals = if (uid == null) emptyList()
            else firebaseRepository?.getManualsForAppliance(uid, id, name, brand, model) ?: emptyList()
            _applianceManuals.update { cur ->
                if (cur?.id == id) cur.copy(manuals = manuals, loading = false) else cur
            }
        }
    }

    fun updateAppliance(id: String, name: String, brand: String, model: String) {
        val uid = currentUid() ?: return
        val existing = _uiState.value.userAppliances.firstOrNull { it.id == id } ?: return
        val updated = existing.copy(
            name = name,
            brand = brand.ifBlank { null },
            model = model.ifBlank { null }
        )
        viewModelScope.launch {
            firebaseRepository?.saveAppliance(uid, updated)
            _uiState.update { it.copy(userAppliances = firebaseRepository?.getAppliances(uid) ?: it.userAppliances) }
            openApplianceManuals(id, updated.name, updated.brand, updated.model, true) // reload screen
        }
    }

    /** Signed-in (or anonymous) Firebase uid, or null when there is no user yet. */
    private fun currentUid(): String? = authManager?.currentUser?.value?.uid

    fun removeBookmark(guide: ManualGuide) {
        val userId = authManager?.currentUserId ?: "guest"
        val manualId = guide.manualId
        _uiState.value = _uiState.value.copy(
            bookmarkedGuides = _uiState.value.bookmarkedGuides.filterNot {
                (manualId != null && it.manualId == manualId) ||
                        (it.issueTitle == guide.issueTitle && it.applianceName == guide.applianceName)
            },
            messages = _uiState.value.messages.map { msg ->
                if ((manualId != null && msg.manualGuide?.manualId == manualId) ||
                    (msg.manualGuide?.issueTitle == guide.issueTitle && msg.manualGuide?.applianceName == guide.applianceName)) {
                    msg.copy(isBookmarked = false)
                } else msg
            }
        )
        viewModelScope.launch {
            if (manualId != null) {
                firebaseRepository?.removeBookmarkByManualId(userId, manualId)
                try {
                    aiService.setBookmark(manualId, false)
                } catch (_: Exception) {}
            }
            refreshBookmarks()
        }
    }

    fun selectApplianceForChat(appliance: Appliance) {
        _uiState.value = _uiState.value.copy(appliance = appliance)
    }

    /**
     * Asks the question [messageId] again: the question and everything after it
     * are removed and the question is sent once more, so it gets a new answer.
     */
    fun regenerateMessage(messageId: Long) {
        val question = takeBackQuestion(messageId) ?: return
        val photo = question.imageUri
        if (question.text.isNullOrBlank() && photo != null) {
            analyzeImage(photo)
        } else {
            sendTextMessage(question.text.orEmpty(), attachedImageUri = photo, useSelectedImage = false)
        }
    }

    /**
     * Replaces the question [messageId] with [newText] and asks it again.
     * The old answer and anything after it are removed; the photo is kept.
     */
    fun editMessage(messageId: Long, newText: String) {
        if (newText.isBlank()) return
        val question = takeBackQuestion(messageId) ?: return
        sendTextMessage(newText, attachedImageUri = question.imageUri, useSelectedImage = false)
    }

    /**
     * Removes the user message [messageId] and every message after it, and
     * returns it. Returns null (and changes nothing) while an answer is still
     * arriving or when [messageId] is not a question of this chat.
     */
    private fun takeBackQuestion(messageId: Long): Message? {
        val state = _uiState.value
        if (state.isSending || state.isAnalyzing) return null
        val index = state.messages.indexOfFirst { it.id == messageId && it.isUser }
        if (index < 0) return null
        _uiState.update { it.copy(messages = it.messages.take(index), error = null) }
        return state.messages[index]
    }

    /**
     * Sends user message (with optional attached image).
     * With [useSelectedImage] false, the photo picked in the input bar is not attached
     * (used when a question is asked again).
     */
    fun sendTextMessage(text: String, attachedImageUri: Uri? = null, useSelectedImage: Boolean = true) {
        val cleanText = text.trim()
        val imageToAttach = attachedImageUri ?: _uiState.value.selectedImageUri.takeIf { useSelectedImage }

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

        // Clear selected image preview (it was attached to this message)
        if (useSelectedImage) {
            _uiState.value = _uiState.value.copy(selectedImageUri = null)
        }

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
        val chatId = _uiState.value.currentChatId

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
                // Keep the chat in the history menu, unless the user already moved to another one.
                if (_uiState.value.currentChatId == chatId) {
                    saveCurrentChat()
                }

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

    /**
     * Returns the image the detection should run on (S1-16): the photo cropped
     * to the located panel, or the original photo when the panel cannot be
     * found. Cropping is an optimisation, so a failure never blocks the flow.
     */
    private suspend fun panelPhotoOf(uri: Uri): Uri = try {

        val box = aiService.locatePanel(uri)

        if (box == null) uri else panelCropper.crop(uri, box) ?: uri

    } catch (cancellation: CancellationException) {

        throw cancellation

    } catch (_: Exception) {

        uri
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

    /**
     * Runs the panel analysis on the last analyzed photo and stores the result
     * in the state, where the interactive overlay (S1-15) reads it from.
     *
     * The photo is first cropped to the control panel using the box located by
     * the backend (S1-16); the buttons and dials are then detected on that crop
     * (S1-14). Cropping is best effort: when no panel can be located, the whole
     * photo is used.
     *
     * @return true when the analysis was started; false when there is no photo
     *   yet (an error is stored in the state in that case).
     */
    fun analyzePanel(): Boolean {

        val uri = lastImageUri
        if (uri == null) {
            _uiState.value = _uiState.value.copy(
                error = "Take a photo first, then tap the detect button."
            )

            return false
        }

        _uiState.value = _uiState.value.copy(
            // The overlay shows the photo right away, with the loading spinner.
            panelImageUri = uri,
            panelControls = emptyList(),
            isAnalyzing = true,
            error = null
        )

        viewModelScope.launch {
            try {

                // 1. Locate the panel and crop the photo to it (S1-16).
                val panelUri = panelPhotoOf(uri)

                // The overlay (S1-15) shows the very image the detection runs on.
                _uiState.value = _uiState.value.copy(
                    panelImageUri = panelUri
                )

                // 2. Detect the controls on that image (S1-14).
                val controls = aiService.analyzePanel(
                    uri = panelUri,
                    appliance = _uiState.value.appliance
                )

                _uiState.value = _uiState.value.copy(
                    panelControls = controls
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

        return true
    }

    internal fun buildControlsText(controls: List<PanelControl>): String {
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

    private companion object {
        const val GUEST_USER = "guest"
    }
}