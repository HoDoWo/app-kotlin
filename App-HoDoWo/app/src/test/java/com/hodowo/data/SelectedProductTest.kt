package com.hodowo.data

import com.hodowo.viewmodel.Listing
import com.hodowo.viewmodel.ListingStatus
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

    private fun cartLine(productType: String, sellerKind: String) = CartLine(
        cartItemId = "c-1", productType = productType, productId = "p-9", title = "Drain pump",
        imageUrl = "http://example.com/p.jpg", quantity = 1, unitPriceCents = 3850, priceCents = 3850,
        addedPriceCents = 3500, priceChanged = true, available = true, unavailableReason = null,
        sellerName = "Bosch Store", sellerKind = sellerKind
    )

    @Test
    fun cartLineOpensTheDetailOfItsProductType() {
        val part = cartLine("part", SELLER_PARTNER).toMarketplaceProduct()
        assertEquals("p-9", part.id)
        assertEquals("part", part.type)
        assertEquals(38.5, part.price, 0.001)
        assertEquals("Bosch Store", part.partnerName)
        assertTrue(part.toProductDetail() is ProductDetail.Part)

        val used = cartLine("used", SELLER_COMMUNITY).toMarketplaceProduct()
        assertEquals(null, used.partnerName)
        assertTrue(used.toProductDetail() is ProductDetail.SecondHand)

        assertTrue(cartLine("new", SELLER_PARTNER).toMarketplaceProduct().toProductDetail() is ProductDetail.New)
    }

    @Test
    fun cartLineWithoutLivePriceFallsBackToThePriceWhenAdded() {
        val line = cartLine("new", SELLER_PARTNER).copy(unitPriceCents = null)
        assertEquals(35.0, line.toMarketplaceProduct().price, 0.001)
        assertEquals(0.0, line.copy(addedPriceCents = null).toMarketplaceProduct().price, 0.001)
    }

    @Test
    fun listingOpensAsASecondHandProduct() {
        val listing = Listing(
            id = "l-1", title = "Used oven", description = "Works fine", condition = "Used, good",
            priceLabel = "$120.50", status = ListingStatus.ACTIVE, inquiryCount = 0, postedAgo = "1d ago",
            imageUrl = "http://example.com/o.jpg"
        )
        val product = listing.toMarketplaceProduct()
        assertEquals("used", product.type)
        assertEquals(120.5, product.price, 0.001)
        val detail = product.toProductDetail() as ProductDetail.SecondHand
        assertEquals("Used, good", detail.condition)
        assertEquals("Used oven", detail.title)
    }
}
