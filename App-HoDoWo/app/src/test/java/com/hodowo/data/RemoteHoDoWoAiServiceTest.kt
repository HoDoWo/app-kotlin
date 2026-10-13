package com.hodowo.data

import android.net.Uri
import com.hodowo.viewmodel.Appliance
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

    /** Builds a backend answer: {"reply": text}. */
    private fun reply(text: String) =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("reply", text).toString())
}