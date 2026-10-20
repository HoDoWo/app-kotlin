package com.hodowo.viewmodel

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Chat history menu: saving, reopening and deleting past chats. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatHistoryTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: MemoryChatStore
    private var time = 1_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = MemoryChatStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ChatViewModel(
        aiService = GuideService(),
        chatHistory = store,
        clock = { time }
    )

    // ---------- ChatViewModel ----------

    @Test
    fun anAnsweredChatIsSavedWithItsApplianceAndQuestion() = runTest(dispatcher) {
        val vm = viewModel()

        vm.sendTextMessage("How do I set 180 °C?")
        advanceUntilIdle()

        val saved = store.chats.single()
        assertEquals("How do I set 180 °C?", saved.title)
        assertEquals("Indesit Oven", saved.applianceName)
        assertEquals(2, saved.messages.size)

        val session = vm.uiState.value.chatSessions.single()
        assertEquals(vm.uiState.value.currentChatId, session.id)
        assertEquals("Indesit Oven", session.applianceName)
    }

    @Test
    fun aPastChatCanBeOpenedAgain() = runTest(dispatcher) {
        val vm = viewModel()
        vm.sendTextMessage("First question")
        advanceUntilIdle()
        val firstId = vm.uiState.value.currentChatId

        time = 2_000L
        vm.startNewChat()
        vm.sendTextMessage("Second question")
        advanceUntilIdle()

        vm.openChat(firstId)

        val state = vm.uiState.value
        assertEquals(firstId, state.currentChatId)
        assertEquals("First question", state.messages.first().text)
        assertEquals("Indesit Oven", state.appliance?.name)
        assertEquals(listOf("Second question", "First question"), state.chatSessions.map { it.title })
    }

    @Test
    fun newMessagesInAReopenedChatGetNewIds() = runTest(dispatcher) {
        val vm = viewModel()
        vm.sendTextMessage("First question")
        advanceUntilIdle()
        val firstId = vm.uiState.value.currentChatId
        vm.startNewChat()

        vm.openChat(firstId)
        vm.sendTextMessage("Follow-up")
        advanceUntilIdle()

        val ids = vm.uiState.value.messages.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun theHistoryIsLoadedWhenTheAppStarts() = runTest(dispatcher) {
        store.chats += savedChatOf("old", listOf(Message(id = 1, text = "Old question", isUser = true)), null)!!

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Old question"), vm.uiState.value.chatSessions.map { it.title })
    }

    @Test
    fun deletingTheOpenChatStartsANewOne() = runTest(dispatcher) {
        val vm = viewModel()
        vm.sendTextMessage("To be deleted")
        advanceUntilIdle()
        val chatId = vm.uiState.value.currentChatId

        time = 2_000L
        vm.deleteChat(chatId)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.messages.isEmpty())
        assertTrue(state.chatSessions.isEmpty())
        assertTrue(store.chats.isEmpty())
        assertTrue(state.currentChatId != chatId)
    }

    @Test
    fun anEmptyChatIsNotSaved() = runTest(dispatcher) {
        val vm = viewModel()

        vm.startNewChat()
        advanceUntilIdle()

        assertTrue(store.chats.isEmpty())
    }

    // ---------- savedChatOf ----------

    @Test
    fun aChatWithOnlyAPhotoIsNamedAfterTheAppliance() {
        val chat = savedChatOf(
            chatId = "c1",
            messages = listOf(Message(id = 1, isUser = true)),
            appliance = Appliance(name = "Washing machine", brand = "Bosch", model = "WAN28", confidence = 0.9f)
        )!!

        assertEquals("Bosch Washing machine WAN28", chat.applianceName)
        assertEquals("Photo of Bosch Washing machine WAN28", chat.title)
    }

    @Test
    fun aGenericApplianceNameIsNotShown() {
        val guide = ManualGuide(applianceName = "Household Appliance", issueTitle = "?", overview = "")
        val chat = savedChatOf(
            chatId = "c1",
            messages = listOf(
                Message(id = 1, text = "?", isUser = true),
                Message(id = 2, text = "answer", isUser = false, manualGuide = guide)
            ),
            appliance = null
        )!!

        assertNull(chat.applianceName)
    }

    @Test
    fun aChatStillStreamingIsSavedWithoutTheUnfinishedAnswer() {
        val chat = savedChatOf(
            chatId = "c1",
            messages = listOf(
                Message(id = 1, text = "Question", isUser = true),
                Message(id = 2, text = "Half an ans", isUser = false, isStreaming = true)
            ),
            appliance = null
        )!!

        assertEquals(1, chat.messages.size)
    }
}

/** In-memory [ChatHistoryStore] for one user. */
private class MemoryChatStore : ChatHistoryStore {
    var chats: List<SavedChat> = emptyList()

    override suspend fun load(userId: String): List<SavedChat> = chats
    override suspend fun save(userId: String, chat: SavedChat) {
        chats = chats.upsert(chat)
    }
    override suspend fun delete(userId: String, chatId: String) {
        chats = chats.filterNot { it.id == chatId }
    }
}

/** Answers every question with a guide for an Indesit oven. */
private class GuideService : HoDoWoAiService {
    override suspend fun identifyAppliance(uri: Uri): Appliance = error("not used")
    override suspend fun askQuestion(question: String, appliance: Appliance?): String = "Turn the knob."
    override suspend fun locatePanel(uri: Uri): PanelBox? = null
    override suspend fun analyzePanel(uri: Uri, appliance: Appliance?): List<PanelControl> = emptyList()
    override suspend fun sendFeedback(report: FeedbackReport) = Unit

    override fun streamAnswerEvents(
        question: String,
        appliance: Appliance?,
        imageUri: Uri?,
        history: List<ChatMessageHistory>
    ): Flow<ChatStreamEvent> = flow {
        val guide = ManualGuide(applianceName = "Indesit Oven", issueTitle = question, overview = "Turn the knob.")
        emit(ChatStreamEvent.Response("Turn the knob.", guide))
    }
}
