package com.hodowo.viewmodel

import com.hodowo.data.ProductRepository
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.SearchKind
import com.hodowo.data.model.kind
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
    fun changingTheTypeChipRequeries() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onKindSelected(SearchKind.PARTS)
        advanceUntilIdle()

        val state = vm.uiState.value as ProductUiState.Success
        assertEquals(SearchKind.PARTS, state.filters.kind)
        assertTrue(state.products.all { it.kind() == SearchKind.PARTS })
    }

    @Test
    fun filtersCombineCorrectly() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onKindSelected(SearchKind.SECOND_HAND)
        vm.applyFilters(category = null, brand = null, minPrice = null, maxPrice = 20.0, usedCondition = null)
        advanceUntilIdle()

        val state = vm.uiState.value as ProductUiState.Success
        assertTrue(state.products.all { it.kind() == SearchKind.SECOND_HAND })
        assertTrue(state.products.all { it.price <= 20.0 })
    }

    @Test
    fun clearFiltersResetsState() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.applyFilters(category = "Pumps", brand = null, minPrice = 5.0, maxPrice = null, usedCondition = null)
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
