package com.hodowo.data

import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.StepPin
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** AR auto-alignment calls of [RemoteGuideLocateService] against a fake backend. */
class RemoteGuideLocateServiceTest {

    private lateinit var server: MockWebServer
    private var token: String? = "token-abc"
    private lateinit var service: RemoteGuideLocateService

    private val steps = listOf(
        ManualStep(1, "Open the door", "Pull the handle", StepPin(0.2f, 0.3f)),
        ManualStep(2, "Wait", "", null),
        ManualStep(3, "Press Start", "", StepPin(0.8f, 0.9f))
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteGuideLocateService(backendUrl = server.url("/").toString(), tokenProvider = { token })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun request(reference: ByteArray? = null, appliance: String? = null) = GuideLocateRequest(
        frameJpeg = byteArrayOf(1, 2, 3),
        steps = steps,
        targets = listOf(1, 3),
        referenceJpeg = reference,
        appliance = appliance
    )

    @Test
    fun sendsFrameStepsTargetsAndTokenAndReadsThePoints() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"points": [{"step": 1, "x": 0.25, "y": 0.4, "confidence": 0.9}, {"step": 3, "x": 0.7, "y": 0.6}]}"""
            )
        )

        val points = service.locate(request(reference = byteArrayOf(9), appliance = "Bosch washing machine"))

        assertEquals(
            listOf(LocatedPoint(1, 0.25f, 0.4f, 0.9f), LocatedPoint(3, 0.7f, 0.6f, 1f)),
            points
        )
        val recorded = server.takeRequest()
        assertEquals(RemoteGuideLocateService.PATH, recorded.path)
        assertEquals("Bearer token-abc", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"image\"; filename=\"frame.jpg\""))
        assertTrue(body.contains("name=\"reference\"; filename=\"photo.jpg\""))
        assertTrue(body.contains("name=\"targets\""))
        assertTrue(body.contains("1,3"))
        assertTrue(body.contains("Bosch washing machine"))
    }

    @Test
    fun withoutReferenceOrApplianceThoseFieldsAreLeftOut() = runTest {
        token = null
        server.enqueue(MockResponse().setBody("""{"points": []}"""))

        assertTrue(service.locate(request()).isEmpty())

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertFalse(body.contains("name=\"reference\""))
        assertFalse(body.contains("name=\"appliance\""))
    }

    @Test
    fun stepsCarryTheirPinsOnlyWhenTheyHaveOne() {
        val json = JSONArray(RemoteGuideLocateService.stepsJson(steps))
        assertEquals(3, json.length())
        assertEquals(1, json.getJSONObject(0).getInt("step"))
        assertEquals("Pull the handle", json.getJSONObject(0).getString("description"))
        assertEquals(0.2, json.getJSONObject(0).getDouble("pin_x"), 1e-6)
        assertFalse(json.getJSONObject(1).has("pin_x"))
    }

    @Test
    fun incompletePointsAreSkipped() {
        val points = RemoteGuideLocateService.parsePoints(
            """{"points": [{"step": 1, "x": 0.1}, {"x": 0.1, "y": 0.2}, "x", {"step": 2, "x": 0.3, "y": 0.4}]}"""
        )
        assertEquals(listOf(LocatedPoint(2, 0.3f, 0.4f, 1f)), points)
        assertTrue(RemoteGuideLocateService.parsePoints("{}").isEmpty())
    }

    @Test
    fun serverErrorsAndInvalidAnswersAreIoExceptions() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setBody("not json"))
        repeat(2) {
            try {
                service.locate(request())
                fail("expected IOException")
            } catch (expected: IOException) {
                // The AR screen falls back to manual aiming.
            }
        }
    }
}
