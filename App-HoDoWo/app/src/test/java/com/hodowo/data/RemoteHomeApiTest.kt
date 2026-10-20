package com.hodowo.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Home screen calls of [RemoteHoDoWoAiService]: recent manuals and the daily tip. */
class RemoteHomeApiTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteHoDoWoAiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteHoDoWoAiService(
            backendUrl = server.url("/").toString(),
            loadImage = { byteArrayOf(1) },
            tokenProvider = { "token-abc" },
            language = "en"
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    // ---------- recentManuals ----------

    @Test
    fun recentManualsAreReadWithTheTokenAndTheLimit() = runTest {
        server.enqueue(
            json(
                """
                {"manuals": [{
                  "id": "m1",
                  "appliance_name": "Bosch Washer",
                  "issue_title": "Error E18: Drain Pump Clog",
                  "overview": "Rinse the drain pump filter.",
                  "steps": [{"number": 1, "title": "Place towel", "description": "Under the flap"}],
                  "warnings": ["Unplug first"],
                  "image_url": "https://example.test/guide.png",
                  "is_bookmarked": true,
                  "created_at_ms": 1800000000000
                }]}
                """.trimIndent()
            )
        )

        val manuals = service.recentManuals(5)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/manuals?limit=5", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))

        val manual = manuals.single()
        assertEquals("m1", manual.guide.manualId)
        assertEquals("Bosch Washer", manual.guide.applianceName)
        assertEquals("Error E18: Drain Pump Clog", manual.guide.issueTitle)
        assertEquals("Place towel", manual.guide.steps.single().title)
        assertEquals(listOf("Unplug first"), manual.guide.warnings)
        assertEquals("https://example.test/guide.png", manual.guide.imageUrl)
        assertEquals(1_800_000_000_000L, manual.createdAtMillis)
        assertTrue(manual.isBookmarked)
    }

    @Test(expected = IOException::class)
    fun recentManualsThrowOnServerError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        service.recentManuals(5)
    }

    @Test
    fun manualsWithoutTitleAreSkippedAndMissingFieldsGetDefaults() {
        val payload = JSONObject(
            """
            {"manuals": [
              {"id": "x", "issue_title": ""},
              {"issue_title": "Descaling", "image_url": null, "created_at_ms": null,
               "steps": [{"description": "Run the cycle"}]}
            ]}
            """.trimIndent()
        )

        val manuals = RemoteHoDoWoAiService.parseRecentManuals(payload)

        val manual = manuals.single()
        assertEquals("Descaling", manual.guide.issueTitle)
        assertEquals("Appliance", manual.guide.applianceName)
        assertNull(manual.guide.imageUrl)
        assertNull(manual.guide.manualId)
        assertNull(manual.createdAtMillis)
        assertFalse(manual.isBookmarked)
        assertEquals("Step 1", manual.guide.steps.single().title)
        assertEquals(1, manual.guide.steps.single().number)
    }

    @Test
    fun aPayloadWithoutManualsGivesAnEmptyList() {
        assertTrue(RemoteHoDoWoAiService.parseRecentManuals(JSONObject("{}")).isEmpty())
    }

    // ---------- dailyTip ----------

    @Test
    fun dailyTipIsReadFromTheBackend() = runTest {
        server.enqueue(json("""{"tip": "  Wash at 30 degrees.  "}"""))

        val tip = service.dailyTip()

        assertEquals("Wash at 30 degrees.", tip)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/appliances/environmental-tip", request.path)
    }

    @Test
    fun anEmptyTipIsNull() = runTest {
        server.enqueue(json("""{"tip": ""}"""))

        assertNull(service.dailyTip())
    }

    @Test(expected = IOException::class)
    fun aTipErrorThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))

        service.dailyTip()
    }
}
