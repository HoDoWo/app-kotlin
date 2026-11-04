package com.hodowo.viewmodel

import com.hodowo.data.ListingsService
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

private fun listing(id: String = "l1", status: ListingStatus = ListingStatus.ACTIVE) = Listing(
    id = id,
    title = "De'Longhi Dedica Espresso",
    description = "Refurbished pump, descaled.",
    condition = "Refurbished",
    priceLabel = "$85.00",
    status = status,
    inquiryCount = 2,
    postedAgo = "3d ago"
)

/** Unit tests for [MyListingsViewModel] (issue #146). */
@OptIn(ExperimentalCoroutinesApi::class)
class MyListingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: FakeListingsService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = FakeListingsService()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MyListingsViewModel(service = service)

    // ---------- load ----------

    @Test
    fun loadShowsTheListings() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"), listing("l2"))
        val vm = viewModel()

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("l1", "l2"), state.listings.map { it.id })
        assertNull(state.error)
    }

    @Test
    fun aFailedLoadReportsTheError() = runTest(dispatcher) {
        service.loadError = IOException("offline")
        val vm = viewModel()

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.listings.isEmpty())
        assertEquals("offline", state.error)
    }

    // ---------- edit ----------

    @Test
    fun startEditingOpensTheDialogForThatListing() = runTest(dispatcher) {
        val vm = viewModel()
        val target = listing("l1")

        vm.startEditing(target)

        assertEquals(target, vm.uiState.value.editingListing)
    }

    @Test
    fun cancelEditingClosesTheDialogWithoutSaving() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startEditing(listing("l1"))

        vm.cancelEditing()

        assertNull(vm.uiState.value.editingListing)
        assertEquals(0, service.updateCalls)
    }

    @Test
    fun saveEditUpdatesTheListingInPlace() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.startEditing(vm.uiState.value.listings.single())

        vm.saveEdit("New title", "New description", "Like new", "$99.00")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.editingListing)
        assertEquals("New title", state.listings.single().title)
        assertEquals("$99.00", state.listings.single().priceLabel)
        assertEquals("Listing updated", state.message)
    }

    // ---------- mark as sold ----------

    @Test
    fun requestMarkAsSoldAsksForConfirmationFirst() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        vm.requestMarkAsSold(vm.uiState.value.listings.single())

        assertEquals("l1", vm.uiState.value.pendingMarkAsSold?.id)
        assertEquals(0, service.markAsSoldCalls)
    }

    @Test
    fun confirmingMarksTheListingAsSold() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.requestMarkAsSold(vm.uiState.value.listings.single())

        vm.confirmMarkAsSold()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.pendingMarkAsSold)
        assertEquals(ListingStatus.SOLD, state.listings.single().status)
        assertEquals(1, service.markAsSoldCalls)
    }

    @Test
    fun dismissingMarkAsSoldDoesNotTouchTheService() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.requestMarkAsSold(vm.uiState.value.listings.single())

        vm.dismissMarkAsSold()

        assertNull(vm.uiState.value.pendingMarkAsSold)
        assertEquals(0, service.markAsSoldCalls)
    }

    // ---------- delete ----------

    @Test
    fun requestDeleteAsksForConfirmationFirst() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        vm.requestDelete(vm.uiState.value.listings.single())

        assertEquals("l1", vm.uiState.value.pendingDelete?.id)
        assertEquals(0, service.deleteCalls)
    }

    @Test
    fun confirmingDeleteRemovesTheListing() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.requestDelete(vm.uiState.value.listings.single())

        vm.confirmDelete()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.pendingDelete)
        assertTrue(state.listings.isEmpty())
        assertEquals("Listing deleted", state.message)
    }

    @Test
    fun dismissingDeleteDoesNotTouchTheService() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.requestDelete(vm.uiState.value.listings.single())

        vm.dismissDelete()

        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(0, service.deleteCalls)
    }

    // ---------- consumeMessage ----------

    @Test
    fun consumingTheMessageClearsIt() = runTest(dispatcher) {
        service.listings = listOf(listing("l1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.requestDelete(vm.uiState.value.listings.single())
        vm.confirmDelete()
        advanceUntilIdle()

        vm.consumeMessage()

        assertNull(vm.uiState.value.message)
    }
}

/** Fake [ListingsService] that records calls and mutates an in-memory list like the real Demo one. */
private class FakeListingsService : ListingsService {
    var listings: List<Listing> = emptyList()
    var loadError: Exception? = null
    var updateCalls = 0
    var markAsSoldCalls = 0
    var deleteCalls = 0

    override suspend fun myListings(): List<Listing> {
        loadError?.let { throw it }
        return listings
    }

    override suspend fun updateListing(id: String, title: String, description: String, condition: String, priceLabel: String): Listing {
        updateCalls++
        val updated = listings.first { it.id == id }
            .copy(title = title, description = description, condition = condition, priceLabel = priceLabel)
        listings = listings.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun markAsSold(id: String): Listing {
        markAsSoldCalls++
        val updated = listings.first { it.id == id }.copy(status = ListingStatus.SOLD)
        listings = listings.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteListing(id: String) {
        deleteCalls++
        listings = listings.filterNot { it.id == id }
    }
}
