package com.hodowo.viewmodel

import com.hodowo.data.CheckoutSession
import com.hodowo.data.Order
import com.hodowo.data.OrderItem
import com.hodowo.data.OrderStatus
import com.hodowo.data.OrdersService
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

private fun order(
    id: String,
    checkout: String = "c1",
    status: OrderStatus = OrderStatus.PAID,
    seller: String = "Seller $id",
    method: String = "standard",
    simulated: Boolean = false,
    createdAt: Long = 1_000L,
    title: String = "Item $id"
) = Order(
    orderId = id, checkoutId = checkout, sellerName = seller,
    items = listOf(OrderItem("part", "p-$id", title, null, 1, 1000, 1000)),
    subtotalCents = 1000, shippingMethod = method, shippingCents = 490, deliveryFrom = null, deliveryTo = null,
    pickupZip = null, paymentMethod = "card", totalCents = 1490, currency = "eur", status = status,
    simulated = simulated, createdAt = createdAt, paidAt = null
)

/** Orders on the "server": the webhook can change a status between two reads. */
private class FakeOrders : OrdersService {
    val orders = mutableMapOf<String, Order>()
    var failure: IOException? = null
    var reads = 0
    /** Applied after each read of one order, like the webhook arriving. */
    var afterRead: (FakeOrders) -> Unit = {}

    override suspend fun listOrders(): List<Order> {
        failure?.let { throw it }
        return orders.values.toList()
    }

    override suspend fun getOrder(orderId: String): Order {
        failure?.let { throw it }
        reads++
        val found = orders.getValue(orderId)
        afterRead(this)
        return found
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: FakeOrders

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = FakeOrders()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun session(vararg ids: String, simulated: Boolean = false) = CheckoutSession(
        checkoutId = "c1", orderIds = ids.toList(), totalCents = 2980, currency = "eur",
        simulated = simulated, clientSecret = if (simulated) null else "secret"
    )

    @Test
    fun ordersOfOneCheckoutAreOnePurchaseNewestFirst() = runTest(dispatcher) {
        service.orders["a"] = order("a", checkout = "old", createdAt = 1)
        service.orders["b"] = order("b", checkout = "new", createdAt = 5, status = OrderStatus.PENDING)
        service.orders["c"] = order("c", checkout = "new", createdAt = 5)
        val vm = OrdersViewModel(service)

        vm.loadHistory()
        advanceUntilIdle()

        val purchases = vm.history.value.purchases
        assertEquals(listOf("new", "old"), purchases.map { it.checkoutId })
        assertEquals(2, purchases[0].orders.size)
        assertEquals(2980, purchases[0].totalCents)
        assertEquals(OrderStatus.PENDING, purchases[0].status) // one seller still waiting for Stripe
        assertEquals("#NEW", purchases[0].code)
        assertTrue(vm.history.value.loaded)
    }

    @Test
    fun theStatusChipsAndTheSearchBoxFilterThePurchases() = runTest(dispatcher) {
        service.orders["a"] = order("a", checkout = "c-paid", title = "Drain pump")
        service.orders["b"] = order("b", checkout = "c-wait", status = OrderStatus.PENDING, method = "pickup")
        service.orders["c"] = order("c", checkout = "c-off", status = OrderStatus.CANCELLED)
        val vm = OrdersViewModel(service)
        vm.loadHistory()
        advanceUntilIdle()

        vm.onFilterSelected(OrderFilter.IN_PROGRESS)
        assertEquals(listOf("c-wait"), vm.history.value.visible.map { it.checkoutId })
        vm.onFilterSelected(OrderFilter.PICKUP)
        assertEquals(listOf("c-wait"), vm.history.value.visible.map { it.checkoutId })
        vm.onFilterSelected(OrderFilter.CANCELLED)
        assertEquals(listOf("c-off"), vm.history.value.visible.map { it.checkoutId })
        assertEquals(1, vm.history.value.count(OrderFilter.PAID))

        vm.onFilterSelected(OrderFilter.ALL)
        vm.onQueryChanged("PUMP")
        assertEquals(listOf("c-paid"), vm.history.value.visible.map { it.checkoutId })
    }

    @Test
    fun aFailedLoadShowsTheErrorAndPullToRefreshKeepsTheList() = runTest(dispatcher) {
        service.failure = IOException("offline")
        val vm = OrdersViewModel(service)
        vm.loadHistory()
        advanceUntilIdle()
        assertEquals("offline", vm.history.value.error)
        assertFalse(vm.history.value.loaded)

        service.failure = null
        service.orders["a"] = order("a")
        vm.loadHistory()
        advanceUntilIdle()
        assertNull(vm.history.value.error)

        service.failure = IOException("offline")
        vm.loadHistory(refresh = true)
        assertTrue(vm.history.value.isRefreshing)
        assertFalse(vm.history.value.isLoading)
        advanceUntilIdle()
        assertEquals(1, vm.history.value.purchases.size) // still on screen
        assertEquals("offline", vm.history.value.error)
    }

    @Test
    fun theConfirmationFollowsTheOrderUntilStripeConfirmsIt() = runTest(dispatcher) {
        service.orders["a"] = order("a", status = OrderStatus.PENDING)
        // The webhook arrives after the second read.
        service.afterRead = { fake ->
            if (fake.reads == 2) fake.orders["a"] = order("a", status = OrderStatus.PAID)
        }
        val vm = OrdersViewModel(service, pollDelayMs = 10)

        vm.showConfirmation(session("a"))
        assertTrue(vm.confirmation.value.isLoading)
        advanceUntilIdle()

        assertEquals(OrderStatus.PAID, vm.confirmation.value.status)
        assertEquals(3, service.reads)
        // The paid order is in the history straight away.
        assertEquals(listOf("c1"), vm.history.value.purchases.map { it.checkoutId })
    }

    @Test
    fun theConfirmationStopsWaitingAfterAWhile() = runTest(dispatcher) {
        service.orders["a"] = order("a", status = OrderStatus.PENDING)
        val vm = OrdersViewModel(service, pollDelayMs = 10, maxPolls = 3)

        vm.showConfirmation(session("a"))
        advanceUntilIdle()

        assertEquals(3, service.reads)
        assertEquals(OrderStatus.PENDING, vm.confirmation.value.status)
        assertFalse(vm.confirmation.value.isLoading)
    }

    @Test
    fun aSimulatedPurchaseIsMarkedAsSimulated() = runTest(dispatcher) {
        service.orders["a"] = order("a", simulated = true)
        val vm = OrdersViewModel(service)

        vm.showConfirmation(session("a", simulated = true))
        advanceUntilIdle()

        assertTrue(vm.confirmation.value.simulated)
        assertTrue(vm.history.value.purchases.single().simulated)
        assertEquals(1, service.reads)
    }

    @Test
    fun aConfirmationThatCannotLoadCanBeRetriedAndClosed() = runTest(dispatcher) {
        service.failure = IOException("offline")
        val vm = OrdersViewModel(service)

        vm.showConfirmation(session("a"))
        advanceUntilIdle()
        assertTrue(vm.confirmation.value.error!!.contains("offline"))

        service.failure = null
        service.orders["a"] = order("a")
        vm.retryConfirmation()
        advanceUntilIdle()
        assertNull(vm.confirmation.value.error)
        assertEquals(OrderStatus.PAID, vm.confirmation.value.status)

        vm.closeConfirmation()
        assertNull(vm.confirmation.value.session)
    }
}
