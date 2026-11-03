package com.hodowo.viewmodel

import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.MarketplaceService
import io.mockk.coEvery
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

    @Test
    fun addToCartIncrementsCartCount() {
        val count = viewModel.cartCount.value
        val product = MarketplaceProduct(id = "p1", title = "Test Product", description = "Desc")

        viewModel.addToCart(product)

        assertEquals(count + 1, viewModel.cartCount.value)
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
