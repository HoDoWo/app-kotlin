package com.hodowo.data

import com.hodowo.viewmodel.PartCompatibility
import com.hodowo.viewmodel.ProductDetail
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Product Detail built from a tapped search result, and its cart actions on the real cart. */
class SelectedProductTest {

    private val part = MarketplaceProduct(
        id = "pump-1", title = "Drain pump", description = "Fits WAN models", type = "part",
        price = 24.9, partnerName = "VoltaCasa", imageUrl = "https://example.test/pump.jpg",
        category = "washing machine", brand = "Bosch", model = "WAN28209GB", partNumber = "00145787"
    )

    @Test
    fun aPartShowsItsSkuSpecsAndUnknownCompatibility() = runTest {
        val detail = SelectedProductService(part).getProductDetail("pump-1") as ProductDetail.Part

        assertEquals("€24.90", detail.priceLabel)
        assertEquals(listOf("https://example.test/pump.jpg"), detail.images)
        assertEquals("00145787", detail.sku)
        assertEquals(PartCompatibility.UNKNOWN, detail.compatibility)
        assertEquals(
            listOf("BRAND" to "Bosch", "MODEL" to "WAN28209GB", "PART NUMBER" to "00145787", "CATEGORY" to "Washing machine"),
            detail.specs
        )
        assertEquals(listOf("pump-1"), SelectedProductService(part).recommendedDemoIds())
    }

    @Test
    fun aNewProductHasNoInventedReviewsOrQuestions() {
        val detail = part.copy(type = "new", description = "", partNumber = null).toProductDetail() as ProductDetail.New

        assertEquals("VoltaCasa", detail.partnerName)
        assertNull(detail.description)
        assertEquals(0, detail.reviewCount)
        assertTrue(detail.questions.isEmpty() && detail.reviews.isEmpty())
        assertNull(detail.matchedAppliance)
        assertEquals("Official partner", part.copy(type = "new", partnerName = null).toProductDetail().let {
            (it as ProductDetail.New).partnerName
        })
    }

    @Test
    fun aListingShowsConditionSellerAndHandover() {
        val listing = MarketplaceProduct(
            id = "fridge-2", title = "Used fridge", description = "Works well", type = "used",
            condition = "like-new", price = 120.0, sellerName = "Marc", handoverMethods = listOf("pickup")
        )

        val detail = listing.toProductDetail() as ProductDetail.SecondHand
        assertEquals("Like new", detail.condition)
        assertEquals("Marc", detail.sellerName)
        assertEquals("Local pickup", detail.location)
        assertEquals("", detail.postedAgo)

        assertEquals("2.5 km away", (listing.copy(distanceKm = 2.5).toProductDetail() as ProductDetail.SecondHand).location)
        val labels = listOf(listOf("pickup", "shipping"), listOf("shipping"), emptyList()).map {
            (listing.copy(handoverMethods = it).toProductDetail() as ProductDetail.SecondHand).location
        }
        assertEquals(listOf("Pickup or shipping", "Shipping available", "Ask the seller"), labels)
        val conditions = listOf("unopened", "used-good", "parts-only", "refurbished", "for-display").map {
            (listing.copy(condition = it).toProductDetail() as ProductDetail.SecondHand).condition
        }
        assertEquals(listOf("Unopened", "Used, good", "For parts", "Refurbished", "For display"), conditions)
    }

    @Test
    fun cartActionsGoToTheRealCart() = runTest {
        val service: CartService = mockk()
        coEvery { service.addItem(any(), any(), any()) } returns Cart()
        val repository = StoreCartRepository(CartStore(service), "part")

        assertTrue(repository.addToCart("pump-1", 2))
        assertTrue(repository.buyNow("pump-1"))

        coVerify { service.addItem("part", "pump-1", 2) }
        coVerify { service.addItem("part", "pump-1", 1) }
    }

    @Test
    fun aProductTheBackendCannotSellIsRefused() = runTest {
        try {
            StoreCartRepository(CartStore(mockk()), null).addToCart("x", 1)
            fail("expected a CartException")
        } catch (e: CartException) {
            assertEquals("This product cannot be bought yet", e.message)
        }
    }
}