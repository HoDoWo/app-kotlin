package com.hodowo.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class MarketplaceServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: MarketplaceService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        service = MarketplaceService(
            backendUrl = baseUrl,
            tokenProvider = { "token-123" }
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun searchReturnsTheFilteredBackendProducts() = runTest {
        val remoteJson = """{
            "items": [
                {
                    "id": "remote-1",
                    "title": "Bosch Pump Motor",
                    "description": "Drain pump",
                    "condition": "used",
                    "price": 25.0,
                    "brand": "Bosch",
                    "listing_kind": "part",
                    "image_url": "http://example.com/img.jpg",
                    "original_price": 50.0,
                    "discount_percent": 50
                }
            ]
        }"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(remoteJson))

        val results = service.search(
            query = "Bosch",
            type = "part",
            condition = "used",
            brand = "Bosch",
            maxPrice = 50.0
        )

        // Only backend products: the sample catalogue is no longer mixed in.
        assertEquals(listOf("remote-1"), results.map { it.id })
        val item = results.single()
        assertEquals("Bosch Pump Motor", item.title)
        assertEquals(25.0, item.price, 0.01)
        assertEquals(50.0, item.originalPrice!!, 0.01)
        assertEquals(50, item.discountPercent)
        assertTrue(item.isPart)
    }

    @Test(expected = IOException::class)
    fun searchReportsABackendFailureInsteadOfShowingSampleData() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        service.search(query = "Bosch", type = "all")
    }

    @Test
    fun searchShowsNoStarsWithoutRealReviews() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items": [{"id": "p1", "title": "Pump", "rating": null, "review_count": null},
                              {"id": "p2", "title": "Belt", "rating": 4.5, "review_count": 3}]}"""
            )
        )

        val results = service.search()

        assertEquals(0.0, results[0].rating, 0.0)
        assertEquals(0, results[0].reviewCount)
        assertEquals(4.5, results[1].rating, 0.0)
        assertEquals(3, results[1].reviewCount)
    }

    @Test
    fun searchFiltersByConditionNewAndUsed() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"items": []}"""))
        val newItems = service.search(type = "all", condition = "brand-new")
        assertTrue(newItems.all { it.condition in listOf("new", "unopened", "brand-new") })

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"items": []}"""))
        val usedItems = service.search(type = "all", condition = "second-hand")
        assertTrue(usedItems.all { it.condition !in listOf("new", "unopened", "brand-new") })
    }

    @Test
    fun searchFiltersByMaxPrice() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"items": []}"""))
        val cheapItems = service.search(type = "all", maxPrice = 20.0)
        assertTrue(cheapItems.all { it.price <= 20.0 })
    }

    @Test
    fun createListingBuildsPostRequestAndParsesProduct() = runTest {
        val responseJson = """{
            "id": "listing-100",
            "title": "KitchenAid Mixer",
            "description": "Used mixer in great condition",
            "condition": "used-good",
            "price_cents": 15000,
            "seller_name": "David M.",
            "image_urls": ["http://example.com/mixer.jpg"]
        }"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val product = service.createListing(
            title = "KitchenAid Mixer",
            description = "Used mixer in great condition",
            category = "mixer",
            brand = "KitchenAid",
            model = "Artisan",
            partNumber = "K45",
            listingKind = "appliance",
            condition = "used-good",
            price = 150.0,
            imageUrls = listOf("http://example.com/mixer.jpg"),
            diagnosticMediaUrls = emptyList(),
            handoverMethods = listOf("pickup"),
            pickupZip = "90210",
            shippingPriceCents = 1000
        )

        val request = mockWebServer.takeRequest()
        assertEquals("/api/marketplace/listings", request.path)
        assertEquals("POST", request.method)

        assertEquals("listing-100", product.id)
        assertEquals("KitchenAid Mixer", product.title)
        assertEquals(150.0, product.price, 0.01)
        assertEquals("http://example.com/mixer.jpg", product.imageUrl)
    }

    @Test(expected = IOException::class)
    fun createListingThrowsIOExceptionOnServerError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        service.createListing(
            title = "Title",
            description = "Description",
            category = "Category",
            brand = "",
            model = "",
            partNumber = "",
            listingKind = "part",
            condition = "new",
            price = 10.0,
            imageUrls = emptyList(),
            diagnosticMediaUrls = emptyList(),
            handoverMethods = listOf("shipping"),
            pickupZip = "",
            shippingPriceCents = null
        )
    }
}