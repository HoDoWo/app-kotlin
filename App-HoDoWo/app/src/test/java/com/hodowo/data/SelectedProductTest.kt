package com.hodowo.data

import com.hodowo.viewmodel.PartCompatibility
import com.hodowo.viewmodel.ProductDetail
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedProductTest {

    @Test
    fun selectedProductServiceReturnsProductDetailAndRecommendedIds() = runTest {
        val product = MarketplaceProduct(
            id = "p-1",
            title = "Bosch Motor",
            description = "Motor description",
            type = "part",
            price = 35.0,
            imageUrl = "http://example.com/img.jpg"
        )
        val service = SelectedProductService(product)

        val detail = service.getProductDetail("p-1")
        assertTrue(detail is ProductDetail.Part)
        assertEquals("p-1", detail.id)
        assertEquals("Bosch Motor", detail.title)

        val ids = service.recommendedDemoIds()
        assertEquals(listOf("p-1"), ids)
    }

    @Test
    fun toProductDetailConvertsUsedApplianceWithDistanceAndMethods() {
        val usedProduct = MarketplaceProduct(
            id = "p-used",
            title = "Used Washer",
            description = "Used washer",
            type = "used",
            condition = "like-new",
            price = 150.0,
            distanceKm = 3.5,
            sellerName = "Anna M.",
            handoverMethods = listOf("pickup", "shipping")
        )

        val detail = usedProduct.toProductDetail() as ProductDetail.SecondHand
        assertEquals("p-used", detail.id)
        assertEquals("Like new", detail.condition)
        assertEquals("3.5 km away", detail.location)
        assertEquals("Anna M.", detail.sellerName)

        val noDistanceProduct = usedProduct.copy(distanceKm = null, handoverMethods = listOf("pickup"))
        val detailNoDist = noDistanceProduct.toProductDetail() as ProductDetail.SecondHand
        assertEquals("Local pickup", detailNoDist.location)

        val shippingOnly = usedProduct.copy(distanceKm = null, handoverMethods = listOf("shipping"))
        assertEquals("Shipping available", (shippingOnly.toProductDetail() as ProductDetail.SecondHand).location)

        val noHandover = usedProduct.copy(distanceKm = null, handoverMethods = emptyList())
        assertEquals("Ask the seller", (noHandover.toProductDetail() as ProductDetail.SecondHand).location)
    }

    @Test
    fun toProductDetailConvertsConditionsCorrectly() {
        fun makeProduct(cond: String) = MarketplaceProduct(
            id = "id", title = "Title", description = "Desc", type = "used", condition = cond, price = 10.0
        )

        assertEquals("Unopened", (makeProduct("unopened").toProductDetail() as ProductDetail.SecondHand).condition)
        assertEquals("Used, good", (makeProduct("used-good").toProductDetail() as ProductDetail.SecondHand).condition)
        assertEquals("For parts", (makeProduct("parts-only").toProductDetail() as ProductDetail.SecondHand).condition)
        assertEquals("Refurbished", (makeProduct("refurbished").toProductDetail() as ProductDetail.SecondHand).condition)
        assertEquals("Custom condition", (makeProduct("custom-condition").toProductDetail() as ProductDetail.SecondHand).condition)
    }

    @Test
    fun toProductDetailConvertsNewProduct() {
        val newProduct = MarketplaceProduct(
            id = "p-new",
            title = "New Washer",
            description = "New washer",
            type = "appliance",
            price = 500.0,
            partnerName = "Bosch Official",
            rating = 4.9,
            reviewCount = 100,
            brand = "Bosch",
            model = "Serie 6",
            warrantyInfo = "2 years"
        )

        val detail = newProduct.toProductDetail() as ProductDetail.New
        assertEquals("Bosch Official", detail.partnerName)
        assertEquals(4.9, detail.rating, 0.01)
        assertEquals(100, detail.reviewCount)
        assertEquals(3, detail.specs.size) // BRAND, MODEL, WARRANTY
    }

    @Test
    fun storeCartRepositoryAddsToCartAndBuysNow() = runTest {
        val cartService: CartService = mockk()
        val cartLine1 = CartLine(
            cartItemId = "item-1", productType = "part", productId = "prod-1", title = "Prod 1",
            imageUrl = null, quantity = 2, unitPriceCents = 1000, priceCents = 2000,
            addedPriceCents = 1000, priceChanged = false, available = true, unavailableReason = null
        )
        val cartLine2 = CartLine(
            cartItemId = "item-2", productType = "part", productId = "prod-2", title = "Prod 2",
            imageUrl = null, quantity = 1, unitPriceCents = 1000, priceCents = 1000,
            addedPriceCents = 1000, priceChanged = false, available = true, unavailableReason = null
        )

        coEvery { cartService.addItem("part", "prod-1", 2) } returns Cart(items = listOf(cartLine1))
        coEvery { cartService.addItem("part", "prod-2", 1) } returns Cart(items = listOf(cartLine1, cartLine2))

        val cartStore = CartStore(cartService)
        val repo = StoreCartRepository(cartStore, "part")

        assertTrue(repo.addToCart("prod-1", 2))
        assertEquals(1, cartStore.cart.value.items.size)

        assertTrue(repo.buyNow("prod-2"))
        assertEquals(2, cartStore.cart.value.items.size)
    }

    @Test(expected = CartException::class)
    fun storeCartRepositoryThrowsWhenTypeIsNull() = runTest {
        val cartStore = CartStore(UnavailableCartService)
        val repo = StoreCartRepository(cartStore, null)
        repo.addToCart("prod-1", 1)
    }
}
