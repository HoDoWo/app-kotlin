package com.hodowo.data

import com.hodowo.viewmodel.PartCompatibility
import com.hodowo.viewmodel.ProductDetail
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

/** [RemoteMarketplaceService] calls and payload parsing (issue #140). */
class RemoteMarketplaceServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteMarketplaceService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteMarketplaceService(
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
    fun aPartIsReadFromThePartsEndpointFirst() = runTest {
        server.enqueue(
            json(
                """
                {"id": "p1", "title": "Door Seal", "price": 22.9, "images": ["https://example.test/a.jpg"],
                 "sku": "SKU-1", "compatibility": {"status": "true", "evidence": "Matches model WAN28209GB"},
                 "specs": {"MATERIAL": "EPDM"}}
                """.trimIndent()
            )
        )

        val product = service.getProductDetail("p1") as ProductDetail.Part

        val request = server.takeRequest()
        assertEquals("/api/marketplace/parts/p1", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        assertEquals("SKU-1", product.sku)
        assertEquals(PartCompatibility.FITS, product.compatibility)
        assertEquals("Matches model WAN28209GB", product.compatibilityEvidence)
        assertEquals("$22.90", product.priceLabel)
        assertEquals(listOf("https://example.test/a.jpg"), product.images)
    }

    @Test
    fun aListingIsReadFromTheListingsEndpointWhenThePartLookupFails() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            json(
                """
                {"id": "l1", "title": "Airfryer", "price": 45.0, "images": [],
                 "condition": "Like new", "location": "Lleida", "seller_name": "Marc", "posted_ago": "4d ago"}
                """.trimIndent()
            )
        )

        val product = service.getProductDetail("l1") as ProductDetail.SecondHand

        server.takeRequest()
        val secondRequest = server.takeRequest()
        assertEquals("/api/marketplace/listings/l1", secondRequest.path)
        assertEquals("Like new", product.condition)
        assertEquals("Marc", product.sellerName)
        assertEquals("$45.00", product.priceLabel)
    }

    @Test
    fun createListingSendsTheDatabaseFieldsIncludingLocation() = runTest {
        server.enqueue(
            json("""{"id": "l1", "title": "Bosch motor", "price": 45.0, "condition": "used-good"}""")
        )

        service.createListing(
            title = "Bosch motor",
            description = "Working motor pulled from a Bosch Serie 4 washer.",
            category = "washing-machine",
            brand = "Bosch",
            model = "WAN28201GB",
            partNumber = "145787",
            listingKind = "part",
            condition = "used-good",
            price = 45.0,
            imageUrls = listOf("https://example.test/motor.jpg"),
            diagnosticMediaUrls = emptyList(),
            handoverMethods = listOf("pickup", "shipping"),
            pickupZip = "98101",
            shippingPriceCents = 680
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/marketplace/listings", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("98101", body.getString("pickup_zip"))
        assertEquals("98101", body.getString("location"))
        assertEquals(680, body.getInt("shipping_price_cents"))
        assertEquals("used-good", body.getString("condition"))
        assertEquals("part", body.getString("listing_kind"))
    }

    @Test
    fun jsonNullFieldsAreNotShownAsTheTextNull() = runTest {
        server.enqueue(
            json(
                """{"id": "p1", "title": "Brezza fridge", "price": 549.0, "description": null, "seller_name": null,
                    "partner_name": null, "warranty_info": null, "part_number": null, "model": null, "brand": null,
                    "category": null, "image_url": null}"""
            )
        )

        val product = service.createListing(
            title = "Brezza fridge", description = "A fridge freezer in good condition, collected from home.",
            category = "refrigerator", brand = "", model = "", partNumber = "", listingKind = "appliance",
            condition = "used-good", price = 549.0, imageUrls = emptyList(), diagnosticMediaUrls = emptyList(),
            handoverMethods = listOf("pickup"), pickupZip = "28001", shippingPriceCents = null
        )

        assertNull(product.warrantyInfo)
        assertNull(product.partNumber)
        assertNull(product.model)
        assertNull(product.brand)
        assertNull(product.category)
        assertNull(product.partnerName)
        assertNull(product.imageUrl)
        assertEquals("", product.description)
        assertEquals("Community seller", product.sellerName)
    }

    @Test
    fun uploadListingImagePostsThePhotoAsMultipart() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"url": "https://storage.example/listing.jpg"}""")
        )

        val url = service.uploadListingImage(byteArrayOf(1, 2, 3, 4))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/marketplace/listings/images", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(request.body.readUtf8().contains("listing.jpg"))
        assertEquals("https://storage.example/listing.jpg", url)
    }

    @Test(expected = IOException::class)
    fun aServerErrorOnBothEndpointsThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))

        service.getProductDetail("x")
    }

    @Test
    fun parseProductReadsANewProductWithoutSkuOrCondition() {
        val json = JSONObject(
            """
            {"id": "n1", "title": "OEM Pump", "price": 38.5, "images": [],
             "partner_name": "Partner Certified", "rating": 4.9, "review_count": 1248,
             "matched_appliance": "WAN28209GB", "specs": {"WARRANTY": "24-Month"}}
            """.trimIndent()
        )

        val product = RemoteMarketplaceService.parseProduct(json) as ProductDetail.New

        assertEquals("Partner Certified", product.partnerName)
        assertEquals(4.9, product.rating, 0.0001)
        assertEquals(1248, product.reviewCount)
        assertEquals("WAN28209GB", product.matchedAppliance)
    }

    @Test
    fun parseProductReadsAnUnknownCompatibilityWhenMissing() {
        val json = JSONObject(
            """{"id": "p2", "title": "Generic Seal", "price": 10.0, "images": [], "sku": "SKU-2"}"""
        )

        val product = RemoteMarketplaceService.parseProduct(json) as ProductDetail.Part

        assertEquals(PartCompatibility.UNKNOWN, product.compatibility)
        assertNull(product.compatibilityEvidence)
    }

    @Test
    fun recommendedDemoIdsAreReadFromTheSearchEndpoint() = runTest {
        server.enqueue(
            json("""{"results": [{"id": "a"}, {"id": "b"}, {"id": ""}]}""")
        )

        val ids = service.recommendedDemoIds()

        assertEquals("/api/marketplace/search", server.takeRequest().path)
        assertEquals(listOf("a", "b"), ids)
    }

    @Test(expected = IOException::class)
    fun anUnauthorizedResponseThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        service.recommendedDemoIds()
    }

    @Test
    fun aPayloadWithoutResultsGivesAnEmptyList() = runTest {
        server.enqueue(json("{}"))

        assertEquals(emptyList<String>(), service.recommendedDemoIds())
    }

    @Test
    fun aSearchPageSendsEveryFilterAndReadsTheNextCursor() = runTest {
        server.enqueue(
            json(
                """{"items": [
                    {"id": "oven-1", "title": "Oven", "type": "new", "listing_kind": "appliance", "price": 399.0,
                     "partner_name": "VoltaCasa", "sponsored": true},
                    {"id": "belt-1", "title": "Drum belt", "type": "part", "price": 12.9},
                    {"id": "l1", "title": "Pump", "type": "used", "listing_kind": "part", "condition": "like-new",
                     "seller_name": "Marc", "price": 15.0}
                   ], "next_cursor": "used:l1"}"""
            )
        )

        val page = service.searchPage(
            query = " pump ", type = "used", condition = "like-new", category = "washing machine",
            brand = "Bosch", minPrice = 5.0, maxPrice = 50.0, cursor = "new:oven-1", limit = 20
        )

        val path = server.takeRequest().path!!
        assertTrue(path.startsWith("/api/marketplace/search?type=used&limit=20"))
        listOf("q=pump", "condition=like-new", "category=washing+machine", "brand=Bosch", "min_price=5.0",
            "max_price=50.0", "cursor=new%3Aoven-1").forEach { assertTrue(it, path.contains(it)) }
        assertEquals("used:l1", page.nextCursor)
        assertEquals(listOf("oven-1", "belt-1", "l1"), page.items.map { it.id })
        assertEquals(false, page.items[0].isPart) // a partner appliance is not a part
        assertEquals(true, page.items[1].isPart) // a catalogue part without listing_kind
        assertEquals(true, page.items[2].isPart)
        assertEquals("Marc", page.items[2].sellerName)
    }

    @Test
    fun theLastSearchPageHasNoCursorAndNoFilterIsSentWhenUnset() = runTest {
        server.enqueue(json("""{"items": [], "next_cursor": null}"""))

        val page = service.searchPage()

        assertEquals("/api/marketplace/search?type=all&limit=20", server.takeRequest().path)
        assertNull(page.nextCursor)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun theFilterSheetOptionsComeFromTheBackend() = runTest {
        server.enqueue(json("""{"categories": ["dishwasher", " ", "oven"], "brands": ["Bosch"]}"""))

        val (categories, brands) = service.searchFilters()

        assertEquals("/api/marketplace/search/filters", server.takeRequest().path)
        assertEquals(listOf("dishwasher", "oven"), categories)
        assertEquals(listOf("Bosch"), brands)
    }
}
