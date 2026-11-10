package com.hodowo.viewmodel

import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.MarketplaceService
import com.hodowo.data.PartsCache
import com.hodowo.data.PartsSearch
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
class PartsCatalogueViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: MarketplaceService

    private class FakeCache(var saved: PartsSearch? = null) : PartsCache {
        override fun load() = saved
        override fun save(search: PartsSearch) {
            saved = search
        }
    }

    private val pump = MarketplaceProduct(
        id = "p1", title = "Drain pump", description = "", type = "part", brand = "Bosch",
        category = "Pumps", partNumber = "00145787", price = 35.0
    )
    private val door = MarketplaceProduct(
        id = "p2", title = "Door seal", description = "", type = "part", brand = "Siemens",
        category = "Seals", price = 12.0, compatibility = PartCompatibility.FITS
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = mockk()
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns listOf(pump, door)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsThePartsAndSavesTheSearch() = runTest(dispatcher) {
        val cache = FakeCache()
        val viewModel = PartsCatalogueViewModel(service, cache)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(pump, door), state.parts)
        assertFalse(state.isLoading)
        assertTrue(state.hasSearched)
        assertNull(state.error)
        assertEquals(listOf("Pumps", "Seals"), state.categories)
        assertEquals(listOf("Bosch", "Siemens"), state.brands)
        assertEquals(listOf(pump, door), cache.saved?.parts)
    }

    @Test
    fun searchesPartsByTheTypedModelNumber() = runTest(dispatcher) {
        val viewModel = PartsCatalogueViewModel(service, FakeCache())
        advanceUntilIdle()

        viewModel.setQuery("  00145787 ")
        viewModel.search()
        advanceUntilIdle()

        coVerify { service.search(query = "00145787", type = "part") }
    }

    @Test
    fun categoryAndBrandFiltersNarrowTheList() = runTest(dispatcher) {
        val viewModel = PartsCatalogueViewModel(service, FakeCache())
        advanceUntilIdle()

        viewModel.selectCategory("pumps")
        assertEquals(listOf(pump), viewModel.uiState.value.visibleParts)

        viewModel.selectBrand("Siemens")
        assertTrue(viewModel.uiState.value.visibleParts.isEmpty())

        viewModel.clearFilters()
        assertEquals(2, viewModel.uiState.value.visibleParts.size)
    }

    @Test
    fun aFilterThatTheNewResultsLackIsDropped() = runTest(dispatcher) {
        val viewModel = PartsCatalogueViewModel(service, FakeCache())
        advanceUntilIdle()
        viewModel.selectBrand("Siemens")

        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns listOf(pump)
        viewModel.search()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.brand)
        assertEquals(listOf(pump), viewModel.uiState.value.visibleParts)
    }

    @Test
    fun emptyResultIsNotAnError() = runTest(dispatcher) {
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns emptyList()
        val viewModel = PartsCatalogueViewModel(service, FakeCache())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasSearched)
        assertTrue(state.parts.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun showsTheSavedSearchAtOnceAndKeepsItWhenTheNetworkFails() = runTest(dispatcher) {
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } throws IOException("No connection")
        val cache = FakeCache(PartsSearch("bosch", listOf(pump)))
        val viewModel = PartsCatalogueViewModel(service, cache)

        // Before the network answers, the saved search is already there.
        assertEquals("bosch", viewModel.uiState.value.query)
        assertEquals(listOf(pump), viewModel.uiState.value.parts)
        assertTrue(viewModel.uiState.value.isFromCache)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("No connection", state.error)
        assertEquals(listOf(pump), state.parts)
        assertFalse(state.isLoading)
        assertEquals(listOf(pump), cache.saved?.parts)
    }

    @Test
    fun failureWithoutSavedSearchShowsTheErrorAndRetryWorks() = runTest(dispatcher) {
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } throws IOException("Offline")
        val viewModel = PartsCatalogueViewModel(service, FakeCache())
        advanceUntilIdle()
        assertEquals("Offline", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.parts.isEmpty())

        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns listOf(pump)
        viewModel.search()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isFromCache)
        assertEquals(listOf(pump), viewModel.uiState.value.parts)
    }
}
