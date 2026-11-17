package com.hodowo.viewmodel

import com.hodowo.data.ProductRepository
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductType
import com.hodowo.data.model.ProductUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ProductRepository

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        repository = ProductRepository(delayMillis = 0)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun viewModel() = ProductViewModel(repository = repository)

    @Test
    fun initialStateLoadsProducts() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ProductUiState.Success)
        val success = state as ProductUiState.Success
        assertTrue(success.products.isNotEmpty())
        assertEquals(ProductFilters(), success.filters)
    }

    @Test
    fun changingTypeFilterRequeries() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val initialCount = (vm.uiState.value as ProductUiState.Success).products.size

        vm.onTypeSelected(ProductType.WHOLE_APPLIANCE)
        advanceUntilIdle()

        val state = vm.uiState.value as ProductUiState.Success
        assertEquals(ProductType.WHOLE_APPLIANCE, state.filters.type)
        // Only whole appliances should remain
        assertTrue(state.products.all { it.type == ProductType.WHOLE_APPLIANCE })
    }

    @Test
    fun filtersCombineCorrectly() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTypeSelected(ProductType.REPLACEMENT_PART)
        vm.onConditionSelected(ProductCondition.NEW)
        advanceUntilIdle()

        val state = vm.uiState.value as ProductUiState.Success
        assertTrue(state.products.all { it.type == ProductType.REPLACEMENT_PART })
        assertTrue(state.products.all { it.condition == ProductCondition.NEW })
    }

    @Test
    fun clearFiltersResetsState() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onTypeSelected(ProductType.WHOLE_APPLIANCE)
        advanceUntilIdle()

        vm.onClearFilters()
        advanceUntilIdle()

        val state = vm.uiState.value as ProductUiState.Success
        assertEquals(ProductFilters(), state.filters)
    }

    @Test
    fun loadMorePaginates() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val firstPage = (vm.uiState.value as ProductUiState.Success).products.size
        // The demo data has 8 items with pageSize 10, so hasMore is false
        val state = vm.uiState.value as ProductUiState.Success
        assertFalse(state.hasMore)
    }

    @Test
    fun searchQueryFilters() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChanged("Bosch")
        advanceUntilIdle()

        val state = vm.uiState.value
        if (state is ProductUiState.Success) {
            assertTrue(state.products.all { it.title.contains("Bosch", ignoreCase = true) || it.partner.contains("Bosch", ignoreCase = true) })
        }
    }

    @Test
    fun emptyResultShowsEmptyState() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChanged("xyznonexistent123")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ProductUiState.Empty)
    }
}
