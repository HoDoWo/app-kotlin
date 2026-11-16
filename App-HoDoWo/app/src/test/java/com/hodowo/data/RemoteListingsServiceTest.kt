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
    fun theDetailsOfTheSellFormAreReadForEditing() = runTest {
        server.enqueue(
            json(
                """{"results": [{"id": "l1", "title": "Bosch pump", "description": "Works well", "price": 45.5,
                    "category": "washing-machine", "brand": "Bosch", "model": null, "part_number": "P-12",
                    "listing_kind": "part", "condition": "used-good", "condition_label": "Used, good",
                    "image_urls": ["https://x.test/a.jpg", "https://x.test/b.jpg"],
                    "handover_methods": ["pickup"], "pickup_zip": "28001", "shipping_price_cents": null}]}"""
            )
        )

        val details = service.myListings().single().details

        assertEquals(
            com.hodowo.viewmodel.ListingEdit(
                title = "Bosch pump", description = "Works well", category = "washing-machine",
                brand = "Bosch", model = "P-12", listingKind = "part", condition = "used-good", price = 45.5,
                imageUrls = listOf("https://x.test/a.jpg", "https://x.test/b.jpg"),
                handoverMethods = listOf("pickup"), pickupZip = "28001", shippingPriceCents = null
            ),
            details
        )
    }

    @Test
    fun anEditSendsEveryFieldOfTheSellForm() = runTest {
        server.enqueue(json("""{"id": "l1", "title": "Bosch pump", "price": 40.0, "status": "active"}"""))
        val edit = com.hodowo.viewmodel.ListingEdit(
            title = " Bosch pump ", description = "A pump that works perfectly well", category = "dishwasher",
            brand = "Bosch", model = "P-12", listingKind = "part", condition = "like-new", price = 40.0,
            imageUrls = listOf("https://x.test/a.jpg"), handoverMethods = listOf("pickup", "shipping"),
            pickupZip = "28001", shippingPriceCents = 680
        )

        val updated = service.updateListingDetails("l1", edit)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/marketplace/listings/l1", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("Bosch pump", body.getString("title"))
        assertEquals("dishwasher", body.getString("category"))
        assertEquals("like-new", body.getString("condition"))
        assertEquals(40.0, body.getDouble("price"), 0.001)
        assertEquals("https://x.test/a.jpg", body.getJSONArray("image_urls").getString(0))
        assertEquals(2, body.getJSONArray("handover_methods").length())
        assertEquals(680, body.getInt("shipping_price_cents"))
        assertEquals("$40.00", updated.priceLabel)
    }

    @Test
    fun withoutShippingThePriceOfShippingIsCleared() {
        val edit = com.hodowo.viewmodel.ListingEdit(
            "Pump", "A pump that works perfectly well", "other", "", "", "part", "used-good", 10.0,
            listOf("https://x.test/a.jpg"), listOf("pickup"), "28001", null
        )

        assertEquals(true, RemoteListingsService.editJson(edit).isNull("shipping_price_cents"))
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
