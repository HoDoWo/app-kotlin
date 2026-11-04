package com.hodowo.viewmodel

import com.hodowo.data.Cart
import com.hodowo.data.CartException
import com.hodowo.data.CartLine
import com.hodowo.data.CartService
import com.hodowo.data.CartStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Unit tests for [CartViewModel]: loading, quantity changes, removal and refused changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val service: CartService = mockk()
    private val store = CartStore(service)

    private val pump = CartLine(
        cartItemId = "line-1", productType = "part", productId = "p1", title = "Drain pump",
        imageUrl = null, quantity = 2, unitPriceCents = 2490, priceCents = 4980, addedPriceCents = 2490,
        priceChanged = false, available = true, unavailableReason = null
    )
    private val single = pump.copy(quantity = 1, priceCents = 2490)

    private fun cartOf(vararg lines: CartLine) =
        Cart(items = lines.toList(), itemCount = lines.sumOf { it.quantity }, totalCents = lines.sumOf { it.priceCents ?: 0 })

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { service.getCart() } returns cartOf(pump)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadShowsTheBackendCart() = runTest(dispatcher) {
        val vm = CartViewModel(store)

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(pump), state.cart.items)
        assertEquals(4980, state.cart.totalCents)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun aFailedLoadShowsTheErrorForTheRetryButton() = runTest(dispatcher) {
        coEvery { service.getCart() } throws IOException("Server error 503")
        val vm = CartViewModel(store)

        vm.load()
        advanceUntilIdle()

        assertEquals("Server error 503", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun aFailedLoadWithoutMessageGetsADefaultOne() = runTest(dispatcher) {
        coEvery { service.getCart() } throws IOException()
        val vm = CartViewModel(store)

        vm.load()
        advanceUntilIdle()

        assertEquals("Could not load your cart", vm.uiState.value.error)
    }

    @Test
    fun plusAndMinusChangeTheQuantityOnTheBackend() = runTest(dispatcher) {
        coEvery { service.setQuantity("line-1", 3) } returns cartOf(pump.copy(quantity = 3))
        coEvery { service.setQuantity("line-1", 1) } returns cartOf(single)
        val vm = CartViewModel(store)

        vm.increase(pump)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.cart.items.single().quantity)

        vm.decrease(pump)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.cart.items.single().quantity)
        assertNull(vm.uiState.value.updatingLineId)
    }

    @Test
    fun minusOnTheLastItemRemovesTheLine() = runTest(dispatcher) {
        coEvery { service.removeItem("line-1") } returns Cart()
        val vm = CartViewModel(store)

        vm.decrease(single)
        advanceUntilIdle()

        coVerify(exactly = 1) { service.removeItem("line-1") }
        coVerify(exactly = 0) { service.setQuantity(any(), any()) }
        assertEquals(emptyList<CartLine>(), vm.uiState.value.cart.items)
    }

    @Test
    fun emptyCartRemovesEveryLine() = runTest(dispatcher) {
        val second = single.copy(cartItemId = "line-2")
        coEvery { service.getCart() } returns cartOf(pump, second)
        coEvery { service.removeItem("line-1") } returns cartOf(second)
        coEvery { service.removeItem("line-2") } returns Cart()
        val vm = CartViewModel(store)
        vm.load()
        advanceUntilIdle()

        vm.clear()
        advanceUntilIdle()

        coVerify(exactly = 1) { service.removeItem("line-1") }
        coVerify(exactly = 1) { service.removeItem("line-2") }
        assertEquals(0, vm.uiState.value.cart.items.size)
        assertNull(vm.uiState.value.updatingLineId)
    }

    @Test
    fun emptyCartOnAnEmptyCartDoesNothing() = runTest(dispatcher) {
        val vm = CartViewModel(store)

        vm.clear()
        advanceUntilIdle()

        coVerify(exactly = 0) { service.removeItem(any()) }
    }

    @Test
    fun removeDeletesTheLine() = runTest(dispatcher) {
        coEvery { service.removeItem("line-1") } returns Cart()
        val vm = CartViewModel(store)

        vm.remove(pump)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.cart.itemCount)
    }

    @Test
    fun aRefusedChangeShowsTheBackendReasonOnce() = runTest(dispatcher) {
        coEvery { service.setQuantity("line-1", 3) } throws CartException("not enough stock")
        val vm = CartViewModel(store)

        vm.increase(pump)
        advanceUntilIdle()
        assertEquals("not enough stock", vm.uiState.value.message)

        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun aNetworkErrorDuringAChangeAsksToCheckTheConnection() = runTest(dispatcher) {
        coEvery { service.removeItem("line-1") } throws IOException("timeout")
        val vm = CartViewModel(store)

        vm.remove(pump)
        advanceUntilIdle()

        assertEquals("Could not update your cart, check your connection", vm.uiState.value.message)
        assertNull(vm.uiState.value.updatingLineId)
    }

    @Test
    fun aSecondTapWhileUpdatingIsIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<Cart>()
        coEvery { service.setQuantity("line-1", 3) } coAnswers { gate.await() }
        val vm = CartViewModel(store)

        vm.increase(pump)
        advanceUntilIdle()
        assertEquals("line-1", vm.uiState.value.updatingLineId)

        vm.increase(pump)
        gate.complete(cartOf(pump.copy(quantity = 3)))
        advanceUntilIdle()

        coVerify(exactly = 1) { service.setQuantity("line-1", 3) }
        assertNull(vm.uiState.value.updatingLineId)
    }

    @Test
    fun changesMadeOnOtherScreensAppearInTheCart() = runTest(dispatcher) {
        coEvery { service.addItem("used", "l9", 1) } returns cartOf(pump, single.copy(cartItemId = "line-2"))
        val vm = CartViewModel(store)
        advanceUntilIdle()

        store.add("used", "l9")
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.cart.items.size)
    }
}