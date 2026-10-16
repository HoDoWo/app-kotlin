package com.hodowo.viewmodel

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ChatViewModel] and [DemoHoDoWoAiService].
 *
 * The ViewModel is tested with a fake AI service, so no network is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: FakeAiService
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        // viewModelScope uses Dispatchers.Main, which does not exist in unit tests.
        Dispatchers.setMain(dispatcher)
        service = FakeAiService()
        viewModel = ChatViewModel(service)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------- sendTextMessage ----------

    @Test
    fun blankMessageIsIgnored() = runTest(dispatcher) {
        viewModel.sendTextMessage("   ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun textMessageAddsQuestionAndAnswer() = runTest(dispatcher) {
        viewModel.sendTextMessage("  Why does it leak?  ")
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        assertEquals(2, messages.size)

        // The question is trimmed and written by the user.
        assertEquals("Why does it leak?", messages[0].text)
        assertTrue(messages[0].isUser)

        // The answer keeps the question, so it can be rated later.
        assertEquals("fake answer", messages[1].text)
        assertFalse(messages[1].isUser)
        assertEquals("Why does it leak?", messages[1].question)

        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun failedQuestionShowsError() = runTest(dispatcher) {
        service.askError = IllegalStateException("boom")

        viewModel.sendTextMessage("Hello")
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSending)

        // Only the user message is in the chat.
        assertEquals(1, viewModel.uiState.value.messages.size)
    }

    @Test
    fun failedQuestionWithoutMessageShowsDefaultError() = runTest(dispatcher) {
        service.askError = IllegalStateException()

        viewModel.sendTextMessage("Hello")
        advanceUntilIdle()

        assertEquals("Error while sending the message.", viewModel.uiState.value.error)
    }

    @Test
    fun clearErrorRemovesTheError() = runTest(dispatcher) {
        service.askError = IllegalStateException("boom")
        viewModel.sendTextMessage("Hello")
        advanceUntilIdle()

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }

    // ---------- analyzeImage ----------

    @Test
    fun analyzeImageStoresApplianceAndAddsSummary() = runTest(dispatcher) {
        viewModel.analyzeImage(mockk<Uri>())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Dishwasher", state.appliance?.name)
        assertFalse(state.isAnalyzing)

        // Photo sent by the user + summary written by the AI.
        assertEquals(2, state.messages.size)
        assertNotNull(state.messages[0].imageUri)
        assertTrue(state.messages[0].isUser)

        val summary = state.messages[1].text.orEmpty()
        assertTrue(summary.contains("I identified: Dishwasher"))
        assertTrue(summary.contains("Brand: Bosch"))
        assertTrue(summary.contains("Model: X1"))
        assertTrue(summary.contains("Confidence: 50%"))
    }

    @Test
    fun summaryOmitsUnknownBrandAndModel() = runTest(dispatcher) {
        service.appliance = Appliance("Oven", null, null, 0.5f)

        viewModel.analyzeImage(mockk<Uri>())
        advanceUntilIdle()

        val summary = viewModel.uiState.value.messages[1].text.orEmpty()
        assertFalse(summary.contains("Brand:"))
        assertFalse(summary.contains("Model:"))
    }

    @Test
    fun failedAnalysisShowsError() = runTest(dispatcher) {
        service.identifyError = IllegalStateException()

        viewModel.analyzeImage(mockk<Uri>())
        advanceUntilIdle()

        assertEquals("Error while analyzing the image.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertNull(viewModel.uiState.value.appliance)
    }

    @Test
    fun identifiedApplianceIsPassedToTheNextQuestion() = runTest(dispatcher) {
        viewModel.analyzeImage(mockk<Uri>())
        advanceUntilIdle()

        viewModel.sendTextMessage("Hello")
        advanceUntilIdle()

        assertEquals("Dishwasher", service.lastAppliance?.name)
    }

    // ---------- setFeedback ----------

    @Test
    fun likeIsShownAndSentToTheBackend() = runTest(dispatcher) {
        val answerId = askAndGetAnswerId("Question 1")

        viewModel.setFeedback(answerId, Feedback.LIKE)
        advanceUntilIdle()

        val answer = viewModel.uiState.value.messages.first { it.id == answerId }
        assertEquals(Feedback.LIKE, answer.feedback)

        assertEquals(1, service.sentFeedback.size)
        val report = service.sentFeedback[0]
        assertEquals("Question 1", report.question)
        assertEquals("fake answer", report.answer)
        assertEquals(Feedback.LIKE, report.feedback)
        assertNull(report.comment)
    }

    @Test
    fun dislikeKeepsTheTrimmedComment() = runTest(dispatcher) {
        val answerId = askAndGetAnswerId("Question 1")

        viewModel.setFeedback(answerId, Feedback.DISLIKE, "  too generic  ")
        advanceUntilIdle()

        val answer = viewModel.uiState.value.messages.first { it.id == answerId }
        assertEquals("too generic", answer.feedbackComment)
        assertEquals("too generic", service.sentFeedback[0].comment)
    }

    @Test
    fun blankCommentIsStoredAsNull() = runTest(dispatcher) {
        val answerId = askAndGetAnswerId("Question 1")

        viewModel.setFeedback(answerId, Feedback.DISLIKE, "   ")
        advanceUntilIdle()

        assertNull(service.sentFeedback[0].comment)
    }

    @Test
    fun userMessagesCannotBeRated() = runTest(dispatcher) {
        viewModel.sendTextMessage("Question 1")
        advanceUntilIdle()

        // The first message (id 1) is the user's question.
        viewModel.setFeedback(1L, Feedback.LIKE)
        advanceUntilIdle()

        assertTrue(service.sentFeedback.isEmpty())
        assertNull(viewModel.uiState.value.messages[0].feedback)
    }

    @Test
    fun unknownMessageIsIgnored() = runTest(dispatcher) {
        viewModel.setFeedback(999L, Feedback.LIKE)
        advanceUntilIdle()

        assertTrue(service.sentFeedback.isEmpty())
    }

    @Test
    fun sameVoteWithoutCommentIsNotSentTwice() = runTest(dispatcher) {
        val answerId = askAndGetAnswerId("Question 1")

        viewModel.setFeedback(answerId, Feedback.LIKE)
        advanceUntilIdle()
        viewModel.setFeedback(answerId, Feedback.LIKE)
        advanceUntilIdle()

        assertEquals(1, service.sentFeedback.size)
    }

    @Test
    fun changingTheVoteSendsTheNewOne() = runTest(dispatcher) {
        val answerId = askAndGetAnswerId("Question 1")

        viewModel.setFeedback(answerId, Feedback.LIKE)
        advanceUntilIdle()
        viewModel.setFeedback(answerId, Feedback.DISLIKE)
        advanceUntilIdle()

        assertEquals(2, service.sentFeedback.size)
        assertEquals(Feedback.DISLIKE, service.sentFeedback[1].feedback)
    }

    @Test
    fun failedFeedbackIsRetriedWithTheNextVote() = runTest(dispatcher) {
        val firstId = askAndGetAnswerId("Question 1")
        val secondId = askAndGetAnswerId("Question 2")

        // The first rating fails: it stays in the queue.
        service.failFeedback = true
        viewModel.setFeedback(firstId, Feedback.LIKE)
        advanceUntilIdle()
        assertTrue(service.sentFeedback.isEmpty())

        // The next rating succeeds and delivers both, in order.
        service.failFeedback = false
        viewModel.setFeedback(secondId, Feedback.DISLIKE)
        advanceUntilIdle()

        assertEquals(
            listOf(firstId, secondId),
            service.sentFeedback.map { it.messageId }
        )

        // The user never sees an error for a failed rating.
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun feedbackScores() {
        assertEquals(1, Feedback.LIKE.score)
        assertEquals(-1, Feedback.DISLIKE.score)
    }

    // ---------- streaming (#83) ----------

    @Test
    fun streamedPiecesAreJoinedInOneAnswer() = runTest(dispatcher) {
        service.stream = flow {
            emit("Check ")
            emit("the ")
            emit("filter.")
        }

        viewModel.sendTextMessage("Why does it leak?")
        advanceUntilIdle()

        val answer = viewModel.uiState.value.messages.last()
        assertEquals("Check the filter.", answer.text)
        assertFalse(answer.isStreaming)
        assertEquals("Why does it leak?", answer.question)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun partialAnswerIsShownWhileStreaming() = runTest(dispatcher) {
        service.stream = flow {
            emit("Hel")
            delay(1_000)
            emit("lo")
        }

        viewModel.sendTextMessage("Hi")

        // Before the first piece: only "thinking".
        assertTrue(viewModel.uiState.value.isWaitingForAnswer)

        runCurrent()

        val partial = viewModel.uiState.value.messages.last()
        assertEquals("Hel", partial.text)
        assertTrue(partial.isStreaming)
        assertFalse(viewModel.uiState.value.isWaitingForAnswer)

        // A message that is still streaming cannot be rated.
        viewModel.setFeedback(partial.id, Feedback.LIKE)
        assertNull(viewModel.uiState.value.messages.last().feedback)

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals("Hello", viewModel.uiState.value.messages.last().text)
        assertFalse(viewModel.uiState.value.messages.last().isStreaming)
    }

    @Test
    fun errorInTheMiddleKeepsThePartialAnswer() = runTest(dispatcher) {
        service.stream = flow {
            emit("Part of")
            throw IllegalStateException("connection lost")
        }

        viewModel.sendTextMessage("Hi")
        advanceUntilIdle()

        val answer = viewModel.uiState.value.messages.last()
        assertEquals("Part of", answer.text)
        assertFalse(answer.isStreaming)
        assertEquals("connection lost", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun demoServiceStreamsTheAnswerWordByWord() = runTest {
        val demo = DemoHoDoWoAiService()
        val machine = Appliance("Washing machine", "Bosch", null, 0.9f)

        val pieces = demo.streamAnswer("Water everywhere", machine).toList()

        assertTrue(pieces.size > 1)
        assertEquals(demo.askQuestion("Water everywhere", machine), pieces.joinToString(""))
    }

    // ---------- DemoHoDoWoAiService ----------

    @Test
    fun demoServiceIdentifiesAWashingMachine() = runTest {
        val appliance = DemoHoDoWoAiService().identifyAppliance(mockk<Uri>())

        assertEquals("Washing machine", appliance.name)
        assertEquals("Bosch", appliance.brand)
    }

    @Test
    fun demoServiceAnswersByKeyword() = runTest {
        val demo = DemoHoDoWoAiService()
        val machine = Appliance("Washing machine", "Bosch", null, 0.9f)

        assertTrue(demo.askQuestion("Error E24", machine).contains("error code"))
        assertTrue(demo.askQuestion("It won't start", machine).contains("plugged in"))
        assertTrue(demo.askQuestion("Water everywhere", machine).contains("water tap"))
        assertTrue(demo.askQuestion("Hello", machine).contains("Washing machine"))
    }

    @Test
    fun demoServiceWorksWithoutAppliance() = runTest {
        val answer = DemoHoDoWoAiService().askQuestion("Hello", null)

        assertTrue(answer.contains("appliance"))
    }

    @Test
    fun demoServiceAcceptsFeedback() = runTest {
        DemoHoDoWoAiService().sendFeedback(
            FeedbackReport(
                messageId = 1L,
                appliance = null,
                question = "q",
                answer = "a",
                feedback = Feedback.LIKE,
                comment = null
            )
        )
    }

    @Test
    fun demoServiceReturnsPanelControls() = runTest {
        val controls = DemoHoDoWoAiService().analyzePanel(mockk<Uri>(), null)

        assertTrue(controls.isNotEmpty())
        assertEquals("c1", controls.first().id)
        assertEquals(ControlType.BUTTON, controls.first().type)
        controls.forEach { control ->
            val box = control.box
            assertTrue(box.x in 0f..1f && box.y in 0f..1f)
            assertTrue(box.w in 0f..1f && box.h in 0f..1f)
        }
    }

    // ---------- panel detection (S1-14) + overlay state (S1-15) ----------

    @Test
    fun analyzePanelStoresTheDetectedControls() = runTest(dispatcher) {
        service.controls = listOf(
            PanelControl(
                id = "c1",
                label = "Start/Pause",
                type = ControlType.BUTTON,
                description = "Starts the program",
                box = ControlBox(0.62f, 0.40f, 0.10f, 0.12f)
            )
        )

        // The detect button works on the last analyzed photo.
        viewModel.analyzeImage(mockk<Uri>())
        advanceUntilIdle()

        assertTrue(viewModel.analyzePanel())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.panelControls.size)
        assertEquals("Start/Pause", state.panelControls[0].label)
        assertNotNull(state.panelImageUri)
        assertFalse(state.isAnalyzing)
    }

    @Test
    fun analyzePanelClearsPreviousControlsWhileLoading() = runTest(dispatcher) {
        service.controls = listOf(
            PanelControl(
                id = "c1",
                label = "Start/Pause",
                type = ControlType.BUTTON,
                description = "Starts the program",
                box = ControlBox(0.62f, 0.40f, 0.10f, 0.12f)
            )
        )

        viewModel.analyzeImage(mockk<Uri>())
        advanceUntilIdle()
        viewModel.analyzePanel()
        advanceUntilIdle()

        // A second run keeps the old boxes off the screen until the new ones arrive.
        service.controls = emptyList()
        viewModel.analyzePanel()

        assertTrue(viewModel.uiState.value.panelControls.isEmpty())
        assertTrue(viewModel.uiState.value.isAnalyzing)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.panelControls.isEmpty())
    }

    @Test
    fun analyzePanelWithoutAPhotoShowsAnErrorAndDoesNotStart() = runTest(dispatcher) {
        assertFalse(viewModel.analyzePanel())
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    @Test
    fun failedPanelAnalysisShowsError() = runTest(dispatcher) {
        service.analyzeError = IllegalStateException("boom")

        viewModel.analyzeImage(mockk<Uri>())
        advanceUntilIdle()
        viewModel.analyzePanel()
        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    // ---------- helpers ----------

    /** Sends a question and returns the id of the AI answer. */
    private suspend fun kotlinx.coroutines.test.TestScope.askAndGetAnswerId(
        question: String
    ): Long {
        viewModel.sendTextMessage(question)
        advanceUntilIdle()
        return viewModel.uiState.value.messages.last().id
    }

    /** Fake service: no network, results and failures are set by each test. */
    private class FakeAiService : HoDoWoAiService {

        var appliance = Appliance("Dishwasher", "Bosch", "X1", 0.5f)
        var controls = emptyList<PanelControl>()
        var askError: Exception? = null
        var identifyError: Exception? = null
        var analyzeError: Exception? = null
        var failFeedback = false

        var lastAppliance: Appliance? = null

        /** When set, the answer arrives as these pieces instead of one block. */
        var stream: Flow<String>? = null
        val sentFeedback = mutableListOf<FeedbackReport>()

        override suspend fun identifyAppliance(uri: Uri): Appliance {
            identifyError?.let { throw it }
            return appliance
        }

        override suspend fun askQuestion(
            question: String,
            appliance: Appliance?
        ): String {
            askError?.let { throw it }
            lastAppliance = appliance
            return "fake answer"
        }

        override suspend fun analyzePanel(uri: Uri, appliance: Appliance?): List<PanelControl> {
            analyzeError?.let { throw it }
            lastAppliance = appliance
            return controls
        }

        override suspend fun sendFeedback(report: FeedbackReport) {
            if (failFeedback) throw IllegalStateException("network down")
            sentFeedback += report
        }

        override fun streamAnswer(question: String, appliance: Appliance?): Flow<String> =
            stream ?: super.streamAnswer(question, appliance)
    }
}