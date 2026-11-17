package com.hodowo.viewmodel

import com.hodowo.data.Cart
import com.hodowo.data.CartException
import com.hodowo.data.CartSelection
import com.hodowo.data.CartService
import com.hodowo.data.CartStore
import com.hodowo.data.ProductSearchService
import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductPage
import com.hodowo.data.model.ProductType
import com.hodowo.data.model.ProductUiState
import com.hodowo.data.model.activeFilterCount
import com.hodowo.data.model.cartProductType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun product(id: String, type: ProductType = ProductType.REPLACEMENT_PART, condition: ProductCondition = ProductCondition.NEW) =
    Product(
        id = id, title = "Item $id", partner = "Acme", partnerVerified = true, price = 10.0, type = type,
        condition = condition, imageUrl = null, rating = 4.0, reviews = 1
    )

/** Search service of two pages that records the filters it was asked for. */
private class PagedSearch : ProductSearchService {
    val asked = mutableListOf<Pair<ProductFilters, String?>>()
    var failure: Exception? = null
    var empty = false

    override fun search(filters: ProductFilters, cursor: String?, pageSize: Int): Flow<ProductPage> = flow {
        asked.add(filters to cursor)
        failure?.let { throw it }
        emit(
            when {
                empty -> ProductPage(emptyList(), null, false)
                cursor == null -> ProductPage(listOf(product("a"), product("b")), "2", true)
                else -> ProductPage(listOf(product("c")), null, false)
            }
        )
    }

    override fun categories() = listOf("Pumps")
    override fun brands() = listOf("Bosch")
}

private class FakeCart : CartService {
    var failure: Exception? = null
    val added = mutableListOf<Pair<String, String>>()
    private var count = 0

    override suspend fun getCart(): Cart {
        failure?.let { throw it }
        return Cart(itemCount = count)
    }

    override suspend fun addItem(productType: String, productId: String, quantity: Int): Cart {
        failure?.let { throw it }
        added.add(productType to productId)
        count += quantity
        return Cart(itemCount = count)
    }

    override suspend fun setQuantity(cartItemId: String, quantity: Int): Cart = Cart()
    override suspend fun removeItem(cartItemId: String): Cart = Cart()
    override suspend fun quote(selection: CartSelection): Cart = Cart()
}

/** The product search screen's state: filters, pages, errors and the cart badge. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelFlowTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var search: PagedSearch
    private lateinit var cartService: FakeCart

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        search = PagedSearch()
        cartService = FakeCart()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProductViewModel(repository = search, cart = CartStore(cartService))

    private fun ProductViewModel.success() = uiState.value as ProductUiState.Success

    @Test
    fun theScreenKnowsTheCategoriesBrandsTypesAndConditionsToOffer() = runTest(dispatcher) {
        val vm = viewModel()

        assertEquals(listOf("Pumps"), vm.categories)
        assertEquals(listOf("Bosch"), vm.brands)
        assertEquals(listOf(ProductType.WHOLE_APPLIANCE, ProductType.REPLACEMENT_PART), vm.types)
        assertEquals(listOf(ProductCondition.NEW, ProductCondition.SECOND_HAND), vm.conditions)
    }

    @Test
    fun loadMoreAddsTheNextPageAndStopsAtTheLastOne() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), vm.success().products.map { it.id })
        assertTrue(vm.success().hasMore)

        vm.loadMore()
        vm.loadMore() // ignored while the page is loading
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.success().products.map { it.id })
        assertFalse(vm.success().hasMore)
        assertEquals(listOf<String?>(null, "2"), search.asked.map { it.second })

        vm.loadMore() // no more pages: nothing is asked
        advanceUntilIdle()
        assertEquals(2, search.asked.size)
    }

    @Test
    fun loadMoreDoesNothingWithoutResults() = runTest(dispatcher) {
        search.empty = true
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(ProductUiState.Empty, vm.uiState.value)
        assertEquals(1, search.asked.size)
    }

    @Test
    fun everyFilterIsSentToTheSearch() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onCategorySelected("Pumps")
        vm.onBrandSelected("Bosch")
        vm.onConditionSelected(ProductCondition.SECOND_HAND)
        vm.onTypeSelected(ProductType.WHOLE_APPLIANCE)
        vm.onPriceRangeSelected(5.0, 50.0)
        advanceUntilIdle()

        val filters = vm.filters
        assertEquals("Pumps", filters.category)
        assertEquals("Bosch", filters.brand)
        assertEquals(ProductCondition.SECOND_HAND, filters.condition)
        assertEquals(ProductType.WHOLE_APPLIANCE, filters.type)
        assertEquals(5.0, filters.minPrice!!, 0.0)
        assertEquals(50.0, filters.maxPrice!!, 0.0)
        assertEquals(6, filters.activeFilterCount())
        assertEquals(filters, search.asked.last().first)

        vm.onClearFilters()
        advanceUntilIdle()
        assertTrue(vm.filters.isEmpty())
        assertEquals(0, vm.filters.activeFilterCount())
    }

    @Test
    fun theQueryWaitsForTheUserToStopTyping() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChanged("pu")
        advanceTimeBy(100)
        vm.onQueryChanged("pump")
        advanceUntilIdle()

        assertEquals("pump", vm.filters.query)
        assertEquals(listOf("", "pump"), search.asked.map { it.first.query })
    }

    @Test
    fun aFailedSearchShowsAnErrorThatCanBeRetried() = runTest(dispatcher) {
        search.failure = IOException("offline")
        val vm = viewModel()
        advanceUntilIdle()

        val error = vm.uiState.value as ProductUiState.Error
        assertTrue(error.canRetry)

        search.failure = null
        vm.onRefresh()
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), vm.success().products.map { it.id })
    }

    @Test
    fun addingToTheCartSendsTheTypeAndShowsTheBadgeAndAMessage() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.addToCart(product("a", ProductType.REPLACEMENT_PART, ProductCondition.NEW))
        vm.addToCart(product("u", ProductType.WHOLE_APPLIANCE, ProductCondition.SECOND_HAND))
        advanceUntilIdle()

        assertEquals(listOf("part" to "a", "used" to "u"), cartService.added)
        assertEquals(2, vm.cartCount.value)
        assertEquals("Added to cart: Item u", vm.message.value)
        vm.consumeMessage()
        assertNull(vm.message.value)
    }

    @Test
    fun theCartTypeOfEachKindOfResult() {
        assertEquals("new", product("n", ProductType.WHOLE_APPLIANCE, ProductCondition.NEW).cartProductType())
        assertEquals("part", product("p").cartProductType())
        assertEquals("used", product("u", condition = ProductCondition.SECOND_HAND).cartProductType())
    }

    @Test
    fun aRefusedOrOfflineCartShowsWhy() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        cartService.failure = CartException("already sold")
        vm.addToCart(product("a"))
        advanceUntilIdle()
        assertEquals("Could not add to cart: already sold", vm.message.value)

        cartService.failure = IOException("offline")
        vm.addToCart(product("a"))
        advanceUntilIdle()
        assertEquals("Could not add to cart, check your connection", vm.message.value)
        assertEquals(0, vm.cartCount.value)
    }

    @Test
    fun anOfflineCartRefreshKeepsTheLastBadge() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.addToCart(product("a"))
        advanceUntilIdle()

        cartService.failure = IOException("offline")
        vm.refreshCart()
        advanceUntilIdle()

        assertEquals(1, vm.cartCount.value)
    }
}
