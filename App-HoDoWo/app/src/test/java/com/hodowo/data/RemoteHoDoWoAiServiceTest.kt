package com.hodowo.data

import android.net.Uri
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.ControlType
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.FeedbackReport
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tests for [RemoteHoDoWoAiService] against a local fake HTTP server,
 * so no real backend or network is needed.
 */
class RemoteHoDoWoAiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteHoDoWoAiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        service = RemoteHoDoWoAiService(
            backendUrl = server.url("/").toString(),
            loadImage = { byteArrayOf(1, 2, 3) },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ---------- askQuestion ----------

    @Test
    fun questionIsSentAsJsonAndReplyIsReturned() = runTest {
        server.enqueue(reply("Check the filter."))

        val answer = service.askQuestion("Why does it leak?", null)

        assertEquals("Check the filter.", answer)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/chat", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        assertEquals(
            "Why does it leak?",
            JSONObject(request.body.readUtf8()).getString("message")
        )
    }

    @Test
    fun identifiedApplianceIsAddedToTheQuestion() = runTest {
        server.enqueue(reply("ok"))
        val machine = Appliance("Washing machine", "Bosch", null, 0.9f)

        service.askQuestion("Why does it leak?", machine)

        val message = JSONObject(server.takeRequest().body.readUtf8()).getString("message")
        assertEquals("Appliance: Washing machine Bosch\nQuestion: Why does it leak?", message)
    }

    @Test
    fun serverErrorThrowsIoException() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = runCatching { service.askQuestion("Hello", null) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("Server error 500", error?.message)
    }

    @Test
    fun replyWithoutJsonThrowsIoException() = runTest {
        server.enqueue(MockResponse().setBody("not json"))

        val error = runCatching { service.askQuestion("Hello", null) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("Invalid server response", error?.message)
    }

    // ---------- authentication ----------

    @Test
    fun tokenIsSentAsBearerHeader() = runTest {
        server.enqueue(reply("ok"))
        val authenticated = RemoteHoDoWoAiService(
            backendUrl = server.url("/").toString(),
            loadImage = { byteArrayOf(1) },
            tokenProvider = { "abc.def.ghi" },
            ioDispatcher = Dispatchers.Unconfined
        )

        authenticated.askQuestion("Hello", null)

        assertEquals("Bearer abc.def.ghi", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun noAuthorizationHeaderWithoutToken() = runTest {
        server.enqueue(reply("ok"))

        service.askQuestion("Hello", null)

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun unauthorizedAnswerAsksToSignInAgain() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("missing bearer token"))

        val error = runCatching { service.askQuestion("Hello", null) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("Not authorized: please sign in again", error?.message)
    }

    // ---------- identifyAppliance ----------

    @Test
    fun photoIsSentAsMultipartAndApplianceIsParsed() = runTest {
        server.enqueue(
            reply("""Result: {"name": "Oven", "brand": "Bosch", "model": null, "confidence": 0.8}""")
        )

        val appliance = service.identifyAppliance(mockk<Uri>())

        assertEquals("Oven", appliance.name)
        assertEquals("Bosch", appliance.brand)
        assertNull(appliance.model)
        assertEquals(0.8f, appliance.confidence, 0.001f)

        val request = server.takeRequest()
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"image\"; filename=\"appliance.jpg\""))
        assertTrue(body.contains("Identify the household appliance"))
    }

    // ---------- analyzePanel ----------

    @Test
    fun panelIsSentAsMultipartAndControlsAreParsed() = runTest {
        server.enqueue(
            panel(
                """[{"id": "c1", "label": "Start/Pause", "type": "button",
                     "description": "Starts the program",
                     "box": {"x": 0.62, "y": 0.40, "w": 0.10, "h": 0.12}}]"""
            )
        )
        val machine = Appliance("Washing machine", "Bosch", "HW-100", 0.9f)

        val controls = service.analyzePanel(mockk<Uri>(), machine)

        assertEquals(1, controls.size)
        assertEquals("c1", controls[0].id)
        assertEquals("Start/Pause", controls[0].label)
        assertEquals(ControlType.BUTTON, controls[0].type)
        assertEquals("Starts the program", controls[0].description)
        assertEquals(0.62f, controls[0].box.x, 0.001f)
        assertEquals(0.12f, controls[0].box.h, 0.001f)

        val request = server.takeRequest()
        assertEquals("/api/panel/analyze", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"image\"; filename=\"panel.jpg\""))
        assertTrue(body.contains("name=\"brand\""))
        assertTrue(body.contains("Bosch"))
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("name=\"language\""))
    }

    @Test
    fun panelWithoutControlsReturnsEmptyList() = runTest {
        server.enqueue(panel("[]"))

        assertTrue(service.analyzePanel(mockk<Uri>(), null).isEmpty())
    }

    @Test
    fun panelWithoutJsonThrowsIoException() = runTest {
        server.enqueue(MockResponse().setBody("not json"))

        val error = runCatching { service.analyzePanel(mockk<Uri>(), null) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("Invalid server response", error?.message)
    }

    // ---------- locatePanel (S1-16) ----------

    @Test
    fun photoIsSentToTheLocateEndpointAndTheBoxIsParsed() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"box": {"x": 0.1, "y": 0.2, "w": 0.6, "h": 0.5}}""")
        )

        val box = service.locatePanel(mockk<Uri>())

        assertEquals(0.1f, box?.x ?: -1f, 0.001f)
        assertEquals(0.2f, box?.y ?: -1f, 0.001f)
        assertEquals(0.6f, box?.w ?: -1f, 0.001f)
        assertEquals(0.5f, box?.h ?: -1f, 0.001f)

        val request = server.takeRequest()
        assertEquals("/api/panel/locate", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
    }

    @Test
    fun aPhotoWithoutAPanelGivesNull() = runTest {
        server.enqueue(MockResponse().setBody("""{"box": null}"""))

        assertNull(service.locatePanel(mockk<Uri>()))
    }

    // ---------- parseControls ----------

    @Test
    fun panelCoordinatesAreClampedAndBadEntriesSkipped() {
        val json = JSONObject(
            """{"controls": [
                {"label": "Temperature dial", "type": "dial",
                 "box": {"x": -1, "y": 2, "w": 0.5, "h": 0.5}},
                {"label": "No box", "type": "button"},
                {"label": "   ", "type": "button",
                 "box": {"x": 0.0, "y": 0.0, "w": 0.1, "h": 0.1}}]}"""
        )

        val controls = RemoteHoDoWoAiService.parseControls(json)

        assertEquals(1, controls.size)
        assertEquals("c1", controls[0].id)
        assertEquals("Temperature dial", controls[0].label)
        assertEquals(ControlType.DIAL, controls[0].type)
        assertEquals(0f, controls[0].box.x, 0.001f)
        assertEquals(1f, controls[0].box.y, 0.001f)
    }

    @Test
    fun unknownPanelTypeBecomesOther() {
        val json = JSONObject(
            """{"controls": [{"label": "Knob", "type": "knob",
                 "box": {"x": 0.1, "y": 0.1, "w": 0.1, "h": 0.1}}]}"""
        )

        assertEquals(ControlType.OTHER, RemoteHoDoWoAiService.parseControls(json).single().type)
    }

    // ---------- parsePanelBox (S1-16) ----------

    @Test
    fun panelLocationBoxIsClampedAndRead() {
        val json = JSONObject("""{"box": {"x": -0.5, "y": 0.2, "w": 0.6, "h": 2.0}}""")

        val box = RemoteHoDoWoAiService.parsePanelBox(json)

        assertEquals(0f, box?.x ?: -1f, 0.001f)
        assertEquals(0.2f, box?.y ?: -1f, 0.001f)
        assertEquals(0.6f, box?.w ?: -1f, 0.001f)
        assertEquals(1f, box?.h ?: -1f, 0.001f)
    }

    @Test
    fun panelLocationWithoutAUsableBoxGivesNull() {
        assertNull(RemoteHoDoWoAiService.parsePanelBox(JSONObject("""{"box": null}""")))
        assertNull(RemoteHoDoWoAiService.parsePanelBox(JSONObject("{}")))
        assertNull(
            RemoteHoDoWoAiService.parsePanelBox(
                JSONObject("""{"box": {"x": 0.1, "y": 0.1, "w": 0, "h": 0.5}}""")
            )
        )
    }

    // ---------- parseAppliance ----------

    @Test
    fun replyWithoutJsonGivesUnknownAppliance() {
        val appliance = RemoteHoDoWoAiService.parseAppliance("I cannot see the image.")

        assertEquals(RemoteHoDoWoAiService.UNKNOWN_APPLIANCE, appliance.name)
        assertEquals(0f, appliance.confidence)
    }

    @Test
    fun brokenJsonGivesUnknownAppliance() {
        val appliance = RemoteHoDoWoAiService.parseAppliance("{ name: }")

        assertEquals(RemoteHoDoWoAiService.UNKNOWN_APPLIANCE, appliance.name)
    }

    @Test
    fun missingFieldsAreHandled() {
        val appliance = RemoteHoDoWoAiService.parseAppliance("""{"brand": "  "}""")

        assertEquals(RemoteHoDoWoAiService.UNKNOWN_APPLIANCE, appliance.name)
        assertNull(appliance.brand)
        assertEquals(0f, appliance.confidence)
    }

    @Test
    fun confidenceIsKeptBetweenZeroAndOne() {
        val tooHigh = RemoteHoDoWoAiService.parseAppliance("""{"name": "Fridge", "confidence": 7}""")
        val negative = RemoteHoDoWoAiService.parseAppliance("""{"name": "Fridge", "confidence": -1}""")

        assertEquals(1f, tooHigh.confidence)
        assertEquals(0f, negative.confidence)
    }

    // ---------- buildQuestion and sendFeedback ----------

    @Test
    fun questionWithoutApplianceIsUnchanged() {
        assertEquals("Hello", RemoteHoDoWoAiService.buildQuestion("Hello", null))
    }

    @Test
    fun feedbackDoesNotCallTheBackendYet() = runTest {
        service.sendFeedback(
            FeedbackReport(
                messageId = 1L,
                appliance = null,
                question = "q",
                answer = "a",
                feedback = Feedback.LIKE,
                comment = null
            )
        )

        assertEquals(0, server.requestCount)
    }

    @Test
    fun setBookmarkCallsPatchBookmarkEndpoint() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "success", "manual_id": "man-1", "is_bookmarked": true}""")
        )

        val result = service.setBookmark("man-1", true)
        assertTrue(result)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/manuals/man-1/bookmark", request.path)
    }

    @Test
    fun rateManualCallsPostRateEndpoint() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status": "success", "manual_id": "man-1", "current_rating": 1}""")
        )

        val result = service.rateManual("man-1", 1)
        assertEquals(1, result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/manuals/man-1/rate", request.path)
    }

    /** Builds a backend answer: {"reply": text}. */
    private fun reply(text: String) =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("reply", text).toString())

    /** Builds a /api/panel/analyze answer: {"controls": [...]}. */
    private fun panel(controlsJson: String) =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"controls": $controlsJson}""")
}