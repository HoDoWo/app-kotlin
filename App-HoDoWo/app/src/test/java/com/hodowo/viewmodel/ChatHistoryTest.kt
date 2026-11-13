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

    @Test
    fun aRecentManualOpensTheChatWhereItWasWritten() = runTest(dispatcher) {
        val vm = viewModel()
        vm.sendTextMessage("How do I set 180 °C?")
        advanceUntilIdle()
        val chatId = vm.uiState.value.currentChatId
        time = 2_000L
        vm.startNewChat()

        vm.openChatForGuide(ManualGuide(applianceName = "Indesit Oven", issueTitle = "How do I set 180 °C?", overview = ""))

        assertEquals(chatId, vm.uiState.value.currentChatId)
        assertEquals("How do I set 180 °C?", vm.uiState.value.messages.first().text)
    }

    @Test
    fun aManualWithoutItsChatStartsANewChatAboutIt() = runTest(dispatcher) {
        val vm = viewModel()
        val guide = ManualGuide(applianceName = "Bosch Dishwasher", issueTitle = "E24 error", overview = "Clean the filter.")

        vm.openChatForGuide(guide)

        val state = vm.uiState.value
        assertEquals(guide, state.messages.single().manualGuide)
        assertEquals("Bosch Dishwasher", state.appliance?.name)
        // Nothing asked yet: not in the history until the first question.
        advanceUntilIdle()
        assertTrue(store.chats.isEmpty())
    }

    @Test
    fun aManualOfARegisteredApplianceStartsItsChatWithBrandAndModel() = runTest(dispatcher) {
        val vm = viewModel()
        val oven = Appliance(name = "Oven", brand = "Indesit", model = "IFW 6530", confidence = 1f)

        vm.openChatForGuide(ManualGuide(applianceName = "Oven", issueTitle = "No heat", overview = "Check the fuse."), oven)

        assertEquals(oven, vm.uiState.value.appliance)
        assertEquals("No heat", vm.uiState.value.messages.single().manualGuide?.issueTitle)
    }

    @Test
    fun aReopenedChatKeepsTheApplianceModel() = runTest(dispatcher) {
        store.chats += savedChatOf(
            "old",
            listOf(Message(id = 1, text = "Old question", isUser = true)),
            Appliance(name = "Oven", brand = "Indesit", model = "IFW 6530", confidence = 0.9f)
        )!!
        val vm = viewModel()
        advanceUntilIdle()

        vm.openChat("old")

        assertEquals(Appliance("Oven", "Indesit", "IFW 6530", 1f), vm.uiState.value.appliance)
    }

    // ---------- conversationHistory ----------

    @Test
    fun theAiIsToldAboutPhotosAndGuidesOfTheChat() {
        val photo = io.mockk.mockk<Uri>()
        val guide = ManualGuide(
            applianceName = "Indesit Oven",
            issueTitle = "Set 180 °C",
            overview = "Use the right knob.",
            steps = listOf(ManualStep(1, "Turn the knob", ""), ManualStep(2, "Wait", ""))
        )
        val history = conversationHistory(
            listOf(
                Message(id = 1, imageUri = photo, isUser = true),
                Message(id = 2, text = "This is an oven.", isUser = false),
                Message(id = 3, text = "How do I set 180?", imageUri = photo, isUser = true),
                Message(id = 4, isUser = false, manualGuide = guide),
                Message(id = 5, text = "", isUser = false, isStreaming = true)
            ),
            Appliance(name = "Oven", brand = "Indesit", model = null, confidence = 1f)
        )

        assertEquals(
            listOf(
                ChatMessageHistory("user", "[Sent a photo of Indesit Oven]"),
                ChatMessageHistory("assistant", "This is an oven."),
                ChatMessageHistory("user", "[Sent a photo of Indesit Oven] How do I set 180?"),
                ChatMessageHistory("assistant", "Use the right knob.\nGuide: Set 180 °C (Indesit Oven)\n1. Turn the knob\n2. Wait")
            ),
            history
        )
    }

    @Test
    fun aGuideKeepsItsOverviewAndStepDescriptionsForTheNextQuestion() {
        val guide = ManualGuide(
            applianceName = "washing machine",
            issueTitle = "Quick wash",
            overview = "Rapid 30 washes a small load in 30 minutes.",
            steps = listOf(
                ManualStep(1, "Turn the dial", "Set it to Rapido 30."),
                ManualStep(2, "Start", "Start")
            )
        )
        val history = conversationHistory(
            listOf(Message(id = 1, text = "Here is how.", isUser = false, manualGuide = guide)),
            null
        )

        assertEquals(
            listOf(
                ChatMessageHistory(
                    "assistant",
                    "Here is how.\nRapid 30 washes a small load in 30 minutes.\n" +
                        "Guide: Quick wash (washing machine)\n1. Turn the dial: Set it to Rapido 30.\n2. Start"
                )
            ),
            history
        )
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