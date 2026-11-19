package com.hodowo.data

import com.hodowo.data.model.FilterOptions
import com.hodowo.data.model.Product
import com.hodowo.data.model.SearchKind
import com.hodowo.data.model.cartProductType
import com.hodowo.data.model.key
import com.hodowo.data.model.kind
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductType
import com.hodowo.data.model.toMarketplaceProduct
import com.hodowo.viewmodel.ProductDetail
import com.hodowo.viewmodel.PartCompatibility
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The product search screen reads the marketplace search: every type, filters done by the backend. */
class MarketplaceProductSearchServiceTest {

    private val pump = MarketplaceProduct(
        id = "p1", title = "OEM Pump", description = "Drain pump", type = "new", condition = "new",
        price = 38.5, partnerName = "Acme", sellerName = "Acme", imageUrl = "https://img/p1.jpg",
        rating = 4.5, reviewCount = 12, isPart = true, sponsored = true, brand = "Bosch"
    )
    private val airfryer = MarketplaceProduct(
        id = "u1", title = "Airfryer", description = "Like new", type = "used", condition = "like-new",
        price = 45.0, sellerName = "Marc", distanceKm = 2.5, isPart = false, discountPercent = 10
    )
    private val refurbished = airfryer.copy(id = "u2", condition = "refurbished", isRefurbished = true)

    private class FakeMarketplace(
        private val items: List<MarketplaceProduct>,
        private val next: String? = null
    ) : MarketplaceService {
        var lastQuery: List<Any?> = emptyList()
        override suspend fun getProductDetail(id: String): ProductDetail = throw UnsupportedOperationException()
        override suspend fun recommendedDemoIds(): List<String> = emptyList()
        override suspend fun searchPage(
            query: String, type: String, condition: String?, category: String?, brand: String?,
            minPrice: Double?, maxPrice: Double?, cursor: String?, limit: Int
        ): MarketplaceSearchPage {
            lastQuery = listOf(query, type, condition, category, brand, minPrice, maxPrice, cursor, limit)
            return MarketplaceSearchPage(items, next)
        }

        override suspend fun searchFilters(): Pair<List<String>, List<String>> =
            listOf("dishwasher", "washing machine") to listOf("Bosch")
    }

    @Test
    fun resultsKeepTheirSourceAndAreMappedForTheScreen() = runTest {
        val service = MarketplaceProductSearchService(FakeMarketplace(listOf(pump, airfryer, refurbished)))

        val page = service.search(ProductFilters()).first()

        assertEquals(listOf("p1", "u1", "u2"), page.products.map { it.id })
        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
        val first = page.products[0]
        assertEquals("Acme", first.partner)
        assertTrue(first.partnerVerified)
        assertEquals(ProductType.REPLACEMENT_PART, first.type)
        assertEquals(ProductCondition.NEW, first.condition)
        assertTrue(first.sponsored)
        assertSame(pump, first.source)
        val second = page.products[1]
        assertEquals("Marc", second.partner)
        assertFalse(second.partnerVerified)
        assertEquals(ProductType.WHOLE_APPLIANCE, second.type)
        assertEquals(ProductCondition.SECOND_HAND, second.condition)
        assertEquals(2.5, second.distanceKm!!, 0.0)
        assertEquals(10, second.discountPercent)
        assertTrue(page.products[2].refurbished)
    }

    @Test
    fun everyFilterAndTheCursorGoToTheBackend() = runTest {
        val fake = FakeMarketplace(emptyList())
        val service = MarketplaceProductSearchService(fake)

        service.search(
            ProductFilters(
                query = "pump", kind = SearchKind.SECOND_HAND, category = "washing machine", brand = "Bosch",
                minPrice = 5.0, maxPrice = 50.0, usedCondition = "like-new"
            ),
            cursor = "used:l1",
            pageSize = 20
        ).first()
        assertEquals(
            listOf("pump", "used", "like-new", "washing machine", "Bosch", 5.0, 50.0, "used:l1", 20),
            fake.lastQuery
        )

        // A second-hand condition is not sent with another type.
        service.search(ProductFilters(kind = SearchKind.PARTS, usedCondition = "like-new")).first()
        assertEquals("part", fake.lastQuery[1])
        assertNull(fake.lastQuery[2])

        service.search(ProductFilters()).first()
        assertEquals(listOf("", "all", null, null, null, null, null, null, 10), fake.lastQuery)
    }

    @Test
    fun thePagesFollowTheBackendCursor() = runTest {
        val more = MarketplaceProductSearchService(FakeMarketplace(listOf(pump), next = "new:p1"))
        val last = MarketplaceProductSearchService(FakeMarketplace(listOf(pump)))

        val first = more.search(ProductFilters()).first()
        assertTrue(first.hasMore)
        assertEquals("new:p1", first.nextCursor)
        assertFalse(last.search(ProductFilters()).first().hasMore)
    }

    @Test
    fun theSheetOffersWhatIsOnSaleAndNoInventedLists() = runTest {
        val service = MarketplaceProductSearchService(FakeMarketplace(emptyList()))

        assertEquals(FilterOptions(listOf("dishwasher", "washing machine"), listOf("Bosch")), service.filterOptions())
        assertTrue(service.categories().isEmpty() && service.brands().isEmpty())
    }

    @Test
    fun aBrandNewPartListedByAUserIsSecondHandForTheBadgeAndTheCart() {
        val listing = MarketplaceProduct(
            id = "l1", title = "Pump", description = "", type = "used", condition = "unopened", isPart = true
        )

        val product = listing.toProduct()

        assertEquals(ProductCondition.SECOND_HAND, product.condition)
        assertEquals("used", product.cartProductType())
        assertEquals(SearchKind.SECOND_HAND, product.kind())
        assertEquals(SearchKind.NEW, pump.copy(isPart = false).toProduct().kind())
        assertEquals("used:l1", product.key())
    }

    @Test
    fun aTappedResultOpensItsFullSourceOnTheDetailPage() {
        val product = MarketplaceProductSearchService(FakeMarketplace(emptyList())).let { pump.toProductForTest() }

        assertSame(pump, product.toMarketplaceProduct())
    }

    @Test
    fun aResultWithoutSourceIsRebuiltForTheDetailPage() {
        val base = Product(
            id = "x", title = "Door seal", partner = "Acme", partnerVerified = true, price = 22.9,
            type = ProductType.REPLACEMENT_PART, condition = ProductCondition.NEW, imageUrl = null,
            rating = 4.0, reviews = 3, sponsored = true
        )

        val part = base.toMarketplaceProduct()
        assertEquals("part", part.type)
        assertEquals("new", part.condition)
        assertEquals("Acme", part.partnerName)
        assertTrue(part.isPart)
        assertTrue(part.sponsored)

        val used = base.copy(condition = ProductCondition.SECOND_HAND, type = ProductType.WHOLE_APPLIANCE, partnerVerified = false)
            .toMarketplaceProduct()
        assertEquals("used", used.type)
        assertEquals("used-good", used.condition)
        assertNull(used.partnerName)
        assertFalse(used.isPart)

        val refurbished = base.copy(condition = ProductCondition.SECOND_HAND, refurbished = true).toMarketplaceProduct()
        assertEquals("refurbished", refurbished.condition)
        assertTrue(refurbished.isRefurbished)
        assertEquals(PartCompatibility.UNKNOWN, refurbished.compatibility)
    }

    private fun MarketplaceProduct.toProductForTest(): Product = toProduct()
}
