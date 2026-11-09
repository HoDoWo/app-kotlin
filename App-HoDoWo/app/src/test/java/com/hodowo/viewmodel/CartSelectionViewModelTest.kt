package com.hodowo.viewmodel

import com.hodowo.data.Cart
import com.hodowo.data.CartException
import com.hodowo.data.CartGroup
import com.hodowo.data.CartLine
import com.hodowo.data.CartSelection
import com.hodowo.data.CartService
import com.hodowo.data.CartStore
import com.hodowo.data.CartSummary
import com.hodowo.data.DeliveryOption
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** [CartViewModel]: ticking lines, delivery per seller and payment method, always quoted by the backend. */
@OptIn(ExperimentalCoroutinesApi::class)
class CartSelectionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val service: CartService = mockk()
    private val store = CartStore(service)

    private fun line(id: String, seller: String, available: Boolean = true) = CartLine(
        cartItemId = id, productType = "part", productId = id, title = id, imageUrl = null, quantity = 1,
        unitPriceCents = 1000, priceCents = 1000, addedPriceCents = 1000, priceChanged = false,
        available = available, unavailableReason = if (available) null else "already sold", sellerId = seller
    )

    private val lines = listOf(line("c1", "voltacasa"), line("c2", "voltacasa"), line("c3", "seller-9"), line("c4", "gone", false))
    private val options = listOf(
        DeliveryOption("standard", "Standard shipping", 490, null),
        DeliveryOption("express", "Express shipping", 990, null)
    )

    /** What the backend answers for a selection: ticks and delivery methods as asked. */
    private fun quoted(selection: CartSelection = CartSelection()): Cart {
        val chosen = selection.cartItemIds?.toSet()
        val items = lines.map { it.copy(selected = it.available && (chosen == null || it.cartItemId in chosen)) }
        val groups = listOf("voltacasa", "seller-9").map { seller ->
            val own = items.filter { it.sellerId == seller }
            CartGroup(
                sellerId = seller, sellerName = seller, sellerKind = "partner", cartItemIds = own.map { it.cartItemId },
                selectedCount = own.count { it.selected }, options = options,
                method = selection.delivery[seller] ?: "standard", delivery = null, pickupArea = null,
                subtotalCents = own.count { it.selected } * 1000, shippingCents = 0, totalCents = 0
            )
        }
        val count = items.count { it.selected }
        return Cart(
            items = items, itemCount = 3, totalCents = 3000, groups = groups,
            summary = CartSummary(selectedItemCount = count, subtotalCents = count * 1000, totalCents = count * 1000,
                canCheckout = count > 0),
            paymentMethods = listOf("card", "google_pay", "klarna")
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { service.getCart() } returns quoted()
        coEvery { service.quote(any()) } answers { quoted(firstArg()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun loaded(): CartViewModel {
        val vm = CartViewModel(store)
        vm.load()
        return vm
    }

    @Test
    fun withoutChoicesTheCartIsLoadedWithoutAQuote() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()

        coVerify(exactly = 0) { service.quote(any()) }
        assertTrue(vm.uiState.value.allSelected)
        assertEquals(listOf("c1", "c2"), vm.uiState.value.linesOf(vm.uiState.value.cart.groups[0]).map { it.cartItemId })
        assertEquals(listOf("c4"), vm.uiState.value.unavailableLines.map { it.cartItemId })
        // Unavailable lines are never bought.
        assertEquals(listOf("c1", "c2", "c3"), vm.selection().cartItemIds)
    }

    @Test
    fun untickingALineQuotesTheRestAndKeepsItInTheCart() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()

        vm.toggleSelected(lines[1])
        advanceUntilIdle()

        coVerify { service.quote(CartSelection(listOf("c1", "c3"), emptyMap(), "card")) }
        val state = vm.uiState.value
        assertEquals(setOf("c2"), state.deselected)
        assertFalse(state.cart.items.first { it.cartItemId == "c2" }.selected)
        assertEquals(2, state.cart.summary.selectedItemCount)
        assertFalse(state.allSelected)

        vm.toggleSelected(lines[1])
        advanceUntilIdle()
        assertTrue(vm.uiState.value.allSelected)
    }

    @Test
    fun selectAllTicksOrUnticksEveryAvailableLine() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()

        vm.selectAll(false)
        advanceUntilIdle()
        assertEquals(setOf("c1", "c2", "c3"), vm.uiState.value.deselected)
        assertFalse(vm.uiState.value.cart.canCheckout)

        vm.selectAll(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deselected.isEmpty())
        assertTrue(vm.uiState.value.cart.canCheckout)
    }

    @Test
    fun anUnavailableLineCannotBeTicked() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()

        vm.toggleSelected(lines[3])
        advanceUntilIdle()

        assertTrue(vm.uiState.value.deselected.isEmpty())
        coVerify(exactly = 0) { service.quote(any()) }
    }

    @Test
    fun choosingADeliveryQuotesItAndOnlyOfferedOptionsCount() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()

        vm.chooseDelivery("voltacasa", "drone")
        vm.chooseDelivery("voltacasa", "standard") // already the method
        vm.chooseDelivery("nobody", "express")
        advanceUntilIdle()
        coVerify(exactly = 0) { service.quote(any()) }

        vm.chooseDelivery("voltacasa", "express")
        advanceUntilIdle()

        assertEquals(mapOf("voltacasa" to "express"), vm.uiState.value.delivery)
        assertEquals("express", vm.uiState.value.cart.groups[0].method)
        assertEquals(mapOf("voltacasa" to "express"), vm.selection().delivery)
    }

    @Test
    fun thePaymentMethodMustBeOfferedByTheBackend() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()

        vm.choosePaymentMethod("paypal")
        assertEquals("card", vm.uiState.value.paymentMethod)

        vm.choosePaymentMethod("klarna")
        assertEquals("klarna", vm.selection().paymentMethod)
    }

    @Test
    fun withChoicesALoadUsesTheQuote() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()
        vm.toggleSelected(lines[0])
        advanceUntilIdle()

        vm.load()
        advanceUntilIdle()

        coVerify(exactly = 2) { service.quote(CartSelection(listOf("c2", "c3"), emptyMap(), "card")) }
    }

    @Test
    fun aCartChangedElsewhereIsQuotedAgainWithTheChoices() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()
        vm.toggleSelected(lines[0])
        advanceUntilIdle()

        store.refresh() // e.g. after paying: the backend answers with everything ticked
        advanceUntilIdle()

        assertFalse(vm.uiState.value.cart.items.first { it.cartItemId == "c1" }.selected)
    }

    @Test
    fun choicesForRemovedLinesAndSellersAreDropped() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()
        vm.toggleSelected(lines[2])
        vm.chooseDelivery("voltacasa", "express")
        advanceUntilIdle()

        val withoutSeller9 = quoted(CartSelection(delivery = mapOf("voltacasa" to "express"))).let { cart ->
            cart.copy(items = cart.items.filter { it.cartItemId != "c3" }, groups = cart.groups.take(1))
        }
        coEvery { service.removeItem("c3") } returns withoutSeller9
        coEvery { service.quote(any()) } returns withoutSeller9
        vm.remove(lines[2])
        advanceUntilIdle()

        assertTrue(vm.uiState.value.deselected.isEmpty())
        assertEquals(mapOf("voltacasa" to "express"), vm.uiState.value.delivery)
    }

    @Test
    fun aRefusedQuoteResetsTheDeliveryAndExplainsWhy() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()
        coEvery { service.quote(any()) } throws CartException("Express shipping is not available for Marc")

        vm.chooseDelivery("voltacasa", "express")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.delivery.isEmpty())
        assertEquals("Express shipping is not available for Marc", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isQuoting)
    }

    @Test
    fun aNetworkErrorWhileQuotingAsksToCheckTheConnection() = runTest(dispatcher) {
        val vm = loaded()
        advanceUntilIdle()
        coEvery { service.quote(any()) } throws IOException("offline")

        vm.toggleSelected(lines[0])
        advanceUntilIdle()

        assertEquals("Could not update the totals, check your connection", vm.uiState.value.message)
    }
}