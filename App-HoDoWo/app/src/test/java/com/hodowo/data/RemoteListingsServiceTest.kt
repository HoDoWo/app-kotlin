package com.hodowo.data

import com.hodowo.viewmodel.ListingStatus
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** [RemoteListingsService] calls and payload parsing (issue #146). */
class RemoteListingsServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteListingsService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteListingsService(
            backendUrl = server.url("/").toString(),
            tokenProvider = { "token-abc" }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun myListingsAreReadWithTheToken() = runTest {
        server.enqueue(
            json(
                """{"results": [{"id": "l1", "title": "Airfryer", "price": 45.0, "condition": "Like new",
                    "status": "active", "inquiry_count": 1, "posted_ago": "1w ago"}]}"""
            )
        )

        val listings = service.myListings()

        val request = server.takeRequest()
        assertEquals("/api/marketplace/listings?mine=true", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        val listing = listings.single()
        assertEquals("Airfryer", listing.title)
        assertEquals("$45.00", listing.priceLabel)
        assertEquals(ListingStatus.ACTIVE, listing.status)
    }

    @Test
    fun aConditionKeyIsShownWithItsHumanLabel() = runTest {
        server.enqueue(
            json(
                """{"results": [{"id": "l1", "title": "Airfryer", "price": 45.0,
                    "condition": "used-good", "condition_label": "Used, good", "status": "active"}]}"""
            )
        )

        val listing = service.myListings().single()

        assertEquals("Used, good", listing.condition)
    }

    @Test
    fun parseListingParsesSingleImageUrlFallback() = runTest {
        server.enqueue(
            json(
                """{"results": [{"id": "l1", "title": "Airfryer", "price": 45.0,
                    "condition": "used-good", "image_url": "http://example.com/single.jpg"}]}"""
            )
        )

        val listing = service.myListings().single()

        assertEquals("http://example.com/single.jpg", listing.imageUrl)
        assertEquals("used-good", listing.condition)
    }

    @Test
    fun updateListingSendsAPatchWithTheNewFields() = runTest {
        server.enqueue(
            json("""{"id": "l1", "title": "New title", "price": 99.0, "condition": "Like new", "status": "active"}""")
        )

        val updated = service.updateListing("l1", "New title", "New description", "Like new", "$99.00")

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/marketplace/listings/l1", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("New title", body.getString("title"))
        assertEquals(99.0, body.getDouble("price"), 0.001)
        assertEquals("New title", updated.title)
    }

    @Test
    fun updateListingWithInvalidPriceLabelDefaultsToZero() = runTest {
        server.enqueue(
            json("""{"id": "l1", "title": "Free Item", "price": 0.0, "condition": "used", "status": "active"}""")
        )

        val updated = service.updateListing("l1", "Free Item", "Free desc", "used", "free")

        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertEquals(0.0, body.getDouble("price"), 0.001)
    }

    @Test
    fun markAsSoldSendsAPostAndReadsTheSoldStatus() = runTest {
        server.enqueue(json("""{"id": "l1", "title": "Airfryer", "price": 45.0, "status": "sold"}"""))

        val updated = service.markAsSold("l1")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/marketplace/listings/l1/sold", request.path)
        assertEquals(ListingStatus.SOLD, updated.status)
    }

    @Test
    fun deleteListingSendsADeleteRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        service.deleteListing("l1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/marketplace/listings/l1", request.path)
    }

    @Test(expected = IOException::class)
    fun aServerErrorThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        service.myListings()
    }

    @Test(expected = IOException::class)
    fun anUnauthorizedResponseThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        service.deleteListing("l1")
    }

    @Test(expected = IOException::class)
    fun malformedJsonResponseThrowsIOException() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        service.myListings()
    }

    @Test
    fun aPayloadWithoutResultsGivesAnEmptyList() = runTest {
        server.enqueue(json("{}"))

        assertEquals(emptyList<Any>(), service.myListings())
    }

    // ---------- UnavailableListingsService ----------

    @Test(expected = IOException::class)
    fun unavailableListingsServiceMyListingsThrows() = runTest {
        UnavailableListingsService().myListings()
    }

    @Test(expected = IOException::class)
    fun unavailableListingsServiceUpdateListingThrows() = runTest {
        UnavailableListingsService("Custom reason").updateListing("1", "t", "d", "c", "$10")
    }

    @Test(expected = IOException::class)
    fun unavailableListingsServiceMarkAsSoldThrows() = runTest {
        UnavailableListingsService().markAsSold("1")
    }

    @Test(expected = IOException::class)
    fun unavailableListingsServiceDeleteListingThrows() = runTest {
        UnavailableListingsService().deleteListing("1")
    }
}
