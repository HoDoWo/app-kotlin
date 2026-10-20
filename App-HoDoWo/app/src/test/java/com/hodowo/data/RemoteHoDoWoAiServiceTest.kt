package com.hodowo.data

import android.net.Uri
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.ChatMessageHistory
import com.hodowo.viewmodel.ChatStreamEvent
import com.hodowo.viewmodel.FeedbackReport
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RemoteHoDoWoAiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: RemoteHoDoWoAiService
    private val mockUri: Uri = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()

        service = RemoteHoDoWoAiService(
            backendUrl = baseUrl,
            loadImage = { byteArrayOf(1, 2, 3, 4) },
            tokenProvider = { "mock-token-123" },
            language = "en"
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ==========================================
    // 1. askQuestion
    // ==========================================

    @Test
    fun askQuestionWithoutApplianceReturnsReply() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"reply": "Check filter."}"""))

        val result = service.askQuestion("How do I fix E18?", null)

        assertEquals("Check filter.", result)
        val request = mockWebServer.takeRequest()
        assertEquals("/api/chat", request.path)
        assertEquals("Bearer mock-token-123", request.getHeader("Authorization"))
    }

    @Test
    fun askQuestionWithApplianceIncludesContextInBody() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"reply": "Unplug the machine."}"""))
        val appliance = Appliance("Washer", "Bosch", "Serie 6", 0.9f)

        val result = service.askQuestion("Leaking water", appliance)

        assertEquals("Unplug the machine.", result)
        val request = mockWebServer.takeRequest()
        val bodyText = request.body.readUtf8()
        assertTrue(bodyText.contains("Washer Bosch Serie 6"))
    }

    @Test(expected = IOException::class)
    fun askQuestionThrowsOnInvalidJsonResponse() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"invalid": "data"}"""))
        service.askQuestion("Test", null)
    }

    @Test(expected = IOException::class)
    fun askQuestionThrowsOn401Unauthorized() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        service.askQuestion("Test", null)
    }

    @Test(expected = IOException::class)
    fun askQuestionThrowsOn500ServerError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        service.askQuestion("Test", null)
    }

    // ==========================================
    // 2. identifyAppliance
    // ==========================================

    @Test
    fun identifyApplianceParsesValidJsonResponse() = runTest {
        val json = """{"reply": "{\"name\": \"Dishwasher\", \"brand\": \"Miele\", \"model\": \"G7000\", \"confidence\": 0.88}"}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val appliance = service.identifyAppliance(mockUri)

        assertEquals("Dishwasher", appliance.name)
        assertEquals("Miele", appliance.brand)
        assertEquals("G7000", appliance.model)
        assertEquals(0.88f, appliance.confidence)
    }

    @Test
    fun identifyApplianceReturnsUnknownOnMalformedJson() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"reply": "Not a JSON object"}"""))

        val appliance = service.identifyAppliance(mockUri)

        assertEquals(RemoteHoDoWoAiService.UNKNOWN_APPLIANCE, appliance.name)
        assertNull(appliance.brand)
        assertEquals(0f, appliance.confidence)
    }

    // ==========================================
    // 3. analyzePanel & locatePanel
    // ==========================================

    @Test
    fun analyzePanelParsesControlsCorrectly() = runTest {
        val payload = """{
            "controls": [
                {
                    "id": "btn-1",
                    "label": "Start",
                    "type": "button",
                    "description": "Starts program",
                    "box": {"x": 0.1, "y": 0.2, "w": 0.3, "h": 0.4}
                }
            ]
        }"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payload))
        val appliance = Appliance("Oven", "Whirlpool", "W9", 0.9f)

        val controls = service.analyzePanel(mockUri, appliance)

        assertEquals(1, controls.size)
        assertEquals("btn-1", controls[0].id)
        assertEquals("Start", controls[0].label)
        assertEquals(0.1f, controls[0].box.x)
    }

    @Test
    fun locatePanelReturnsValidPanelBox() = runTest {
        val payload = """{"box": {"x": 0.05, "y": 0.1, "w": 0.8, "h": 0.5}}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(payload))

        val box = service.locatePanel(mockUri)

        assertNotNull(box)
        assertEquals(0.8f, box?.w)
    }

    @Test
    fun locatePanelReturnsNullWhenNoBoxOrZeroSize() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"box": {"x": 0.0, "y": 0.0, "w": 0.0, "h": 0.0}}"""))

        val box = service.locatePanel(mockUri)

        assertNull(box)
    }

    // ==========================================
    // 4. setBookmark & rateManual
    // ==========================================

    @Test
    fun setBookmarkReturnsBackendResponseValue() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"is_bookmarked": true}"""))

        val result = service.setBookmark("m-123", true)

        assertTrue(result)
        val request = mockWebServer.takeRequest()
        assertEquals("/api/manuals/m-123/bookmark", request.path)
        assertEquals("PATCH", request.method)
    }

    @Test
    fun setBookmarkHandlesServerErrorGracefully() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = service.setBookmark("m-123", true)

        assertTrue(result) // Fallback returns input state
    }

    @Test
    fun setBookmarkHandlesEmptyResponseBody() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = service.setBookmark("m-123", false)

        assertFalse(result)
    }

    @Test
    fun rateManualReturnsUpdatedRating() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"current_rating": 5}"""))

        val rating = service.rateManual("m-123", 5)

        assertEquals(5, rating)
        val request = mockWebServer.takeRequest()
        assertEquals("/api/manuals/m-123/rate", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun rateManualHandlesEmptyResponseBodyAndError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val rating = service.rateManual("m-123", 4)

        assertEquals(4, rating)
    }

    @Test
    fun sendFeedbackExecutesWithoutErrors() = runTest {
        val report = mockk<FeedbackReport>(relaxed = true)
        service.sendFeedback(report)
    }

    // ==========================================
    // 5. streamAnswerEvents (SSE Streaming)
    // ==========================================

    @Test
    fun streamAnswerEventsEmitsStepTokenAndResponse() = runTest {
        val sseResponse = """
            event: step
            data: {"title": "Analyzing issue...", "detail": "Checking codes"}

            event: token
            data: {"token": "Clean "}

            event: token
            data: {"token": "the filter."}

            event: response
            data: {"reply": "Clean the filter.", "is_manual": true, "manual_id": "man-1", "steps": [{"number": 1, "title": "Filter", "description": "Unscrew filter"}]}
            
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sseResponse))

        val history = listOf(ChatMessageHistory("user", "Hello"))
        val events = service.streamAnswerEvents("Help E18", null, null, history).toList()

        assertEquals(4, events.size)
        assertTrue(events[0] is ChatStreamEvent.Step)
        assertTrue(events[1] is ChatStreamEvent.Token)
        assertTrue(events[2] is ChatStreamEvent.Token)
        assertTrue(events[3] is ChatStreamEvent.Response)

        val responseEvent = events[3] as ChatStreamEvent.Response
        assertEquals("Clean the filter.", responseEvent.fullText)
        assertNotNull(responseEvent.manualGuide)
        assertEquals("man-1", responseEvent.manualGuide?.manualId)
        assertEquals(1, responseEvent.manualGuide?.steps?.size)
    }

    @Test
    fun streamAnswerEventsWithImageBuildsMultipartRequest() = runTest {
        val sseResponse = """
            event: response
            data: {"reply": "Appliance identified."}
            
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sseResponse))

        val history = listOf(ChatMessageHistory("user", "Hi"))
        val events = service.streamAnswerEvents("Identify this", null, mockUri, history).toList()

        assertEquals(1, events.size)
        val request = mockWebServer.takeRequest()
        assertEquals("/api/chat/stream", request.path)
        assertTrue(request.getHeader("Content-Type")?.contains("multipart/form-data") == true)
    }

    @Test
    fun streamAnswerEventsWithHistoryNoImageSendsJsonBody() = runTest {
        val sseResponse = """
            event: response
            data: {"reply": "Continuous answer."}
            
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sseResponse))

        val history = listOf(ChatMessageHistory("user", "Previous question"))
        val events = service.streamAnswerEvents("Next question", null, null, history).toList()

        assertEquals(1, events.size)
        val request = mockWebServer.takeRequest()
        val bodyText = request.body.readUtf8()
        assertTrue(bodyText.contains("Previous question"))
    }

    @Test(expected = IOException::class)
    fun streamAnswerEventsThrowsOnServerError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        service.streamAnswerEvents("Hello", null, null, emptyList()).toList()
    }

    @Test
    fun streamAnswerEventsWithFailingImageLoadFallsBackToTextRequest() = runTest {
        val failingService = RemoteHoDoWoAiService(
            backendUrl = mockWebServer.url("/").toString(),
            loadImage = { throw RuntimeException("Failed to load image") },
            tokenProvider = { "token" }
        )

        val sseResponse = """
            event: response
            data: {"reply": "Text fallback response."}
            
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sseResponse))

        val events = failingService.streamAnswerEvents("Question", null, mockUri, emptyList()).toList()

        assertEquals(1, events.size)
        assertTrue(events[0] is ChatStreamEvent.Response)
        assertEquals("Text fallback response.", (events[0] as ChatStreamEvent.Response).fullText)
    }

    // ==========================================
    // 6. Companion Helper Methods (Static Tests)
    // ==========================================

    @Test
    fun buildQuestionFormatsWithAndWithoutAppliance() {
        val qNoAppliance = RemoteHoDoWoAiService.buildQuestion("How to clean?", null)
        assertEquals("How to clean?", qNoAppliance)

        val appliance = Appliance("Dryer", "LG", "D12", 0.9f)
        val qWithAppliance = RemoteHoDoWoAiService.buildQuestion("How to clean?", appliance)
        assertEquals("Appliance: Dryer LG D12\nQuestion: How to clean?", qWithAppliance)
    }

    @Test
    fun parseApplianceHandlesInvalidFormattingAndNaN() {
        val invalidFormat = RemoteHoDoWoAiService.parseAppliance("No json brackets here")
        assertEquals(RemoteHoDoWoAiService.UNKNOWN_APPLIANCE, invalidFormat.name)

        val nanConfidence = RemoteHoDoWoAiService.parseAppliance("""{"name": "Mixer", "confidence": "NaN"}""")
        assertEquals("Mixer", nanConfidence.name)
        assertEquals(0f, nanConfidence.confidence)
    }

    @Test
    fun parseControlsFiltersEmptyLabelsAndClampsCoordinates() {
        val payload = JSONObject("""{
            "controls": [
                {"label": "", "box": {"x": 0.1, "y": 0.1, "w": 0.1, "h": 0.1}},
                {"label": "Power", "box": {"x": -0.5, "y": 1.5, "w": 0.2, "h": 0.2}}
            ]
        }""")

        val controls = RemoteHoDoWoAiService.parseControls(payload)

        assertEquals(1, controls.size)
        assertEquals("Power", controls[0].label)
        assertEquals(0f, controls[0].box.x) // Clamped from -0.5 to 0.0
        assertEquals(1f, controls[0].box.y) // Clamped from 1.5 to 1.0
    }

    // ---------- sendFeedback (#40) ----------

    @Test
    fun likeIsSentToTheFeedbackEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"status":"stored"}"""))

        service.sendFeedback(
            FeedbackReport(
                messageId = 1L,
                appliance = Appliance("Washing machine", "Bosch", null, 0.9f),
                question = "Why does it leak?",
                answer = "Check the door seal.",
                feedback = Feedback.LIKE,
                comment = null
            )
        )

        val request = server.takeRequest()
        assertEquals("/api/feedback", request.path)
        assertEquals("POST", request.method)

        val body = JSONObject(request.body.readUtf8())
        assertEquals("Why does it leak?", body.getString("question"))
        assertEquals("Check the door seal.", body.getString("answer"))
        assertEquals(1, body.getInt("rating"))
        assertTrue(body.isNull("comment"))
        assertEquals("Bosch", body.getJSONObject("appliance").getString("brand"))
        assertTrue(body.getJSONObject("appliance").isNull("model"))
    }

    @Test
    fun dislikeIsSentWithItsComment() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"status":"stored"}"""))

        service.sendFeedback(
            FeedbackReport(
                messageId = 2L,
                appliance = null,
                question = "Error E18?",
                answer = "Call a technician.",
                feedback = Feedback.DISLIKE,
                comment = "too generic"
            )
        )

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(-1, body.getInt("rating"))
        assertEquals("too generic", body.getString("comment"))
        assertTrue(!body.has("appliance"))
    }

    @Test
    fun feedbackErrorIsThrownSoTheRatingIsRetried() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"not configured"}"""))

        val error = runCatching {
            service.sendFeedback(
                FeedbackReport(1L, null, "q", "a", Feedback.LIKE, null)
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("Server error 503", error?.message)
    }

    @Test
    fun questionCarriesTheApplianceAsStructuredData() = runTest {
        server.enqueue(reply("ok"))
        val machine = Appliance("Washing machine", "Bosch", "WAN28", 0.9f)

        service.askQuestion("Why does it leak?", machine)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val appliance = body.getJSONObject("appliance")
        assertEquals("Washing machine", appliance.getString("name"))
        assertEquals("Bosch", appliance.getString("brand"))
        assertEquals("WAN28", appliance.getString("model"))
    }

    @Test
    fun questionWithoutApplianceHasNoApplianceField() = runTest {
        server.enqueue(reply("ok"))

        service.askQuestion("Hello", null)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(!body.has("appliance"))
    fun parsePanelBoxReturnsNullForInvalidOrNegativeWidthHeight() {
        val jsonInvalidWidth = JSONObject("""{"box": {"x": 0.1, "y": 0.1, "w": -0.5, "h": 0.5}}""")
        assertNull(RemoteHoDoWoAiService.parsePanelBox(jsonInvalidWidth))

        val jsonNoBox = JSONObject("""{"other": "data"}""")
        assertNull(RemoteHoDoWoAiService.parsePanelBox(jsonNoBox))
    }
}
