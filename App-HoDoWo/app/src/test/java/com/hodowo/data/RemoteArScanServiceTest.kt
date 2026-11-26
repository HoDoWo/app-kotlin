package com.hodowo.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** AR tab scan calls of [RemoteArScanService] against a fake backend. */
class RemoteArScanServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteArScanService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteArScanService(server.url("/").toString(), tokenProvider = { "token-abc" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sendsTheFrameAndLanguageAndReadsDeviceAndControls() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"device": {"name": "Lavatrice", "brand": "Bosch", "model": null},
                    "controls": [{"label": "Start", "description": "Avvia", "x": 0.7, "y": 0.3}]}"""
            )
        )

        val result = service.scan(byteArrayOf(1, 2, 3), language = "it")

        assertEquals(ArScanResult("Lavatrice · Bosch", listOf(ScannedControl("Start", "Avvia", 0.7f, 0.3f))), result)
        val recorded = server.takeRequest()
        assertEquals(RemoteArScanService.PATH, recorded.path)
        assertEquals("Bearer token-abc", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"image\"; filename=\"frame.jpg\""))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("\r\n\r\nit\r\n"))
    }

    @Test
    fun noDeviceMeansNoControls() {
        val result = RemoteArScanService.parseScan(
            """{"device": null, "controls": [{"label": "Start", "x": 0.5, "y": 0.5}]}"""
        )
        assertNull(result.deviceName)
        assertTrue(result.controls.isEmpty())
    }

    @Test
    fun incompleteControlsAreSkippedAndTheModelIsShown() {
        val result = RemoteArScanService.parseScan(
            """{"device": {"name": "Keyboard", "brand": "Logitech", "model": "K120"},
                "controls": [{"label": "", "x": 0.1, "y": 0.1}, {"label": "Enter", "x": 0.9}, {"label": "Esc", "x": 0.05, "y": 0.1}]}"""
        )
        assertEquals("Keyboard · Logitech K120", result.deviceName)
        assertEquals(listOf(ScannedControl("Esc", "", 0.05f, 0.1f)), result.controls)
    }

    @Test
    fun serverErrorsAndInvalidAnswersAreIoExceptions() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setBody("not json"))
        repeat(2) {
            try {
                service.scan(byteArrayOf(1), language = "en")
                fail("expected IOException")
            } catch (expected: IOException) {
                // The AR tab offers "Scan again".
            }
        }
    }
}
