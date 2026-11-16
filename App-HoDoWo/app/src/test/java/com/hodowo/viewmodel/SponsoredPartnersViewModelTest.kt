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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SponsoredPartnersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: MarketplaceService

    private fun product(id: String, partner: String?, sponsored: Boolean, seller: String = "Community seller") =
        MarketplaceProduct(id = id, title = "Item $id", description = "", partnerName = partner, sponsored = sponsored, sellerName = seller)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun listsOnlySponsoredProductsGroupedByPartnerInBackendOrder() = runTest(dispatcher) {
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns listOf(
            product("1", "Bosch", sponsored = true),
            product("2", "Bosch", sponsored = false),
            product("3", "Siemens", sponsored = true),
            product("4", "Bosch", sponsored = true)
        )
        val viewModel = SponsoredPartnersViewModel(service)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Bosch", "Siemens"), state.groups.map { it.partnerName })
        assertEquals(listOf("1", "4"), state.groups[0].products.map { it.id })
        assertEquals(listOf("3"), state.groups[1].products.map { it.id })
        assertTrue(state.groups.flatMap { it.products }.all { it.sponsored })
        assertTrue(state.hasLoaded)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun aProductWithoutPartnerNameIsGroupedUnderItsSeller() = runTest(dispatcher) {
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns listOf(
            product("1", null, sponsored = true, seller = "Acme Parts"),
            product("2", "  ", sponsored = true, seller = "Acme Parts")
        )
        val viewModel = SponsoredPartnersViewModel(service)
        advanceUntilIdle()

        assertEquals(listOf("Acme Parts"), viewModel.uiState.value.groups.map { it.partnerName })
        assertEquals(2, viewModel.uiState.value.groups[0].products.size)
    }

    @Test
    fun nothingSponsoredIsEmptyNotAnError() = runTest(dispatcher) {
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns listOf(product("1", "Bosch", sponsored = false))
        val viewModel = SponsoredPartnersViewModel(service)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.groups.isEmpty())
        assertTrue(viewModel.uiState.value.hasLoaded)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun failureShowsTheErrorAndRetryRecovers() = runTest(dispatcher) {
        coEvery { service.search(any(), any(), any(), any(), any(), any()) } throws IOException("Offline")
        val viewModel = SponsoredPartnersViewModel(service)
        advanceUntilIdle()
        assertEquals("Offline", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.groups.isEmpty())

        coEvery { service.search(any(), any(), any(), any(), any(), any()) } returns listOf(product("1", "Bosch", sponsored = true))
        viewModel.load()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.groups.size)
    }
}
