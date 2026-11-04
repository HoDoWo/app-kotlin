package com.hodowo.viewmodel

import com.hodowo.data.Cart
import com.hodowo.data.CartException
import com.hodowo.data.CartService
import com.hodowo.data.CartStore
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.MarketplaceService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: MarketplaceService
    private lateinit var viewModel: MarketplaceViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = mockk(relaxed = true)

        val product = MarketplaceProduct(id = "p1", title = "Bosch Drain Pump", description = "Pump", price = 35.0)
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns listOf(product)

        viewModel = MarketplaceViewModel(service)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initPerformsInitialSearch() = runTest(dispatcher) {
        advanceUntilIdle()

        assertEquals(1, viewModel.products.value.size)
        assertEquals("Bosch Drain Pump", viewModel.products.value[0].title)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun setQueryUpdatesQueryAndTriggersSearch() = runTest(dispatcher) {
        viewModel.setQuery("Filter")
        advanceUntilIdle()

        assertEquals("Filter", viewModel.query.value)
    }

    @Test
    fun clearQueryResetsQueryToEmptyAndTriggersSearch() = runTest(dispatcher) {
        viewModel.clearQuery()
        advanceUntilIdle()

        assertEquals("", viewModel.query.value)
    }

    @Test
    fun setClassificationAndConditionUpdatesStateAndSearches() = runTest(dispatcher) {
        viewModel.setClassification("appliance")
        viewModel.setCondition("used")
        advanceUntilIdle()

        assertEquals("appliance", viewModel.classification.value)
        assertEquals("used", viewModel.condition.value)
    }

    @Test
    fun toggleFitsBoschAndToggleUnder50TogglesBooleans() = runTest(dispatcher) {
        val initialBosch = viewModel.fitsBoschSerie4.value
        viewModel.toggleFitsBosch()
        advanceUntilIdle()
        assertEquals(!initialBosch, viewModel.fitsBoschSerie4.value)

        val initialUnder50 = viewModel.under50.value
        viewModel.toggleUnder50()
        advanceUntilIdle()
        assertEquals(!initialUnder50, viewModel.under50.value)
    }

    // ---------- cart (#142) ----------

    private val cartService: CartService = mockk()
    private val pump = MarketplaceProduct(id = "HDW-P-001", title = "Drain pump", description = "", type = "part")

    private fun viewModelWithCart(): MarketplaceViewModel {
        coEvery { cartService.getCart() } returns Cart(itemCount = 1)
        return MarketplaceViewModel(service, CartStore(cartService))
    }

    @Test
    fun theBadgeShowsTheBackendCartWhenTheMarketplaceOpens() = runTest(dispatcher) {
        val vm = viewModelWithCart()
        advanceUntilIdle()

        assertEquals(1, vm.cartCount.value)
    }

    @Test
    fun addToCartSendsTheProductTypeAndUpdatesTheBadge() = runTest(dispatcher) {
        coEvery { cartService.addItem("part", "HDW-P-001", 1) } returns Cart(itemCount = 2)
        val vm = viewModelWithCart()

        vm.addToCart(pump)
        advanceUntilIdle()

        coVerify { cartService.addItem("part", "HDW-P-001", 1) }
        assertEquals(2, vm.cartCount.value)
        assertEquals("Added to cart: Drain pump", vm.cartMessage.value)

        vm.consumeCartMessage()
        assertNull(vm.cartMessage.value)
    }

    @Test
    fun aRefusedAddShowsTheBackendReason() = runTest(dispatcher) {
        coEvery { cartService.addItem(any(), any(), any()) } throws CartException("already sold")
        val vm = viewModelWithCart()

        vm.addToCart(pump.copy(type = "used"))
        advanceUntilIdle()

        assertEquals("Could not add to cart: already sold", vm.cartMessage.value)
        assertEquals(1, vm.cartCount.value)
    }

    @Test
    fun aNetworkErrorWhileAddingAsksToCheckTheConnection() = runTest(dispatcher) {
        coEvery { cartService.addItem(any(), any(), any()) } throws IOException("timeout")
        val vm = viewModelWithCart()

        vm.addToCart(pump)
        advanceUntilIdle()

        assertEquals("Could not add to cart, check your connection", vm.cartMessage.value)
    }

    @Test
    fun aProductTheBackendCannotSellIsNotSent() = runTest(dispatcher) {
        val vm = viewModelWithCart()

        vm.addToCart(pump.copy(type = "appliance"))
        advanceUntilIdle()

        assertEquals("This product cannot be bought yet", vm.cartMessage.value)
        coVerify(exactly = 0) { cartService.addItem(any(), any(), any()) }
    }

    @Test
    fun aFailedCartLoadKeepsTheBadgeAtZero() = runTest(dispatcher) {
        coEvery { cartService.getCart() } throws IOException("offline")
        val vm = MarketplaceViewModel(service, CartStore(cartService))

        vm.refreshCart()
        advanceUntilIdle()

        assertEquals(0, vm.cartCount.value)
    }

    @Test
    fun withoutACartServiceAddingIsRefused() = runTest(dispatcher) {
        viewModel.addToCart(pump)
        advanceUntilIdle()

        assertEquals("Could not add to cart: The cart is not available right now", viewModel.cartMessage.value)
    }

    @Test
    fun searchWithParametersUpdatesAllStateValues() = runTest(dispatcher) {
        viewModel.search(
            query = "Dryer belt",
            type = "part",
            condition = "new",
            fitsBosch = false,
            under50 = false
        )
        advanceUntilIdle()

        assertEquals("Dryer belt", viewModel.query.value)
        assertEquals("part", viewModel.classification.value)
        assertEquals("new", viewModel.condition.value)
        assertFalse(viewModel.fitsBoschSerie4.value)
        assertFalse(viewModel.under50.value)
    }

    @Test
    fun searchFailureSetsErrorInState() = runTest(dispatcher) {
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } throws RuntimeException("Network down")

        viewModel.setQuery("Fail query")
        advanceUntilIdle()

        assertEquals("Network down", viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }
}