package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.CheckoutSession
import com.hodowo.data.Order
import com.hodowo.data.OrderStatus
import com.hodowo.data.OrdersService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * One checkout: the orders of every seller paid together. The history shows purchases,
 * like the cart showed them, with one section per seller.
 */
data class Purchase(val checkoutId: String, val orders: List<Order>) {
    val createdAt: Long get() = orders.maxOfOrNull { it.createdAt } ?: 0L
    val totalCents: Int get() = orders.sumOf { it.totalCents }
    val subtotalCents: Int get() = orders.sumOf { it.subtotalCents }
    val shippingCents: Int get() = orders.sumOf { it.shippingCents }
    val currency: String get() = orders.firstOrNull()?.currency ?: "eur"
    val itemCount: Int get() = orders.sumOf { it.itemCount }
    val simulated: Boolean get() = orders.any { it.simulated }
    val hasPickup: Boolean get() = orders.any { it.isPickup }
    val paymentMethod: String? get() = orders.firstNotNullOfOrNull { it.paymentMethod }

    /** Short code to tell purchases apart ("#3F9A1C2B"). */
    val code: String get() = "#" + checkoutId.take(CODE_LENGTH).uppercase()

    /** Still waiting for Stripe while any order is; paid when every order is. */
    val status: OrderStatus get() = statusOf(orders)

    companion object {
        private const val CODE_LENGTH = 8
    }
}

/** The status of a group of orders paid together. */
fun statusOf(orders: List<Order>): OrderStatus = when {
    orders.isEmpty() -> OrderStatus.UNKNOWN
    orders.any { it.status == OrderStatus.PENDING } -> OrderStatus.PENDING
    orders.all { it.status == OrderStatus.PAID } -> OrderStatus.PAID
    orders.any { it.status == OrderStatus.FAILED } -> OrderStatus.FAILED
    orders.all { it.status == OrderStatus.CANCELLED } -> OrderStatus.CANCELLED
    orders.any { it.status == OrderStatus.PAID } -> OrderStatus.PAID
    else -> OrderStatus.UNKNOWN
}

/** Orders grouped by checkout, newest first. */
fun List<Order>.toPurchases(): List<Purchase> =
    groupBy { it.checkoutId.ifBlank { it.orderId } }
        .map { (checkoutId, orders) -> Purchase(checkoutId, orders.sortedBy { it.sellerName }) }
        .sortedByDescending { it.createdAt }

/** The status chips of the purchase history. */
enum class OrderFilter(val label: String) {
    ALL("All"),
    IN_PROGRESS("In progress"),
    PAID("Paid"),
    PICKUP("Local pickup"),
    CANCELLED("Cancelled");

    fun matches(purchase: Purchase): Boolean = when (this) {
        ALL -> true
        IN_PROGRESS -> purchase.status == OrderStatus.PENDING
        PAID -> purchase.status == OrderStatus.PAID
        PICKUP -> purchase.hasPickup
        CANCELLED -> purchase.status == OrderStatus.CANCELLED || purchase.status == OrderStatus.FAILED
    }
}

data class PurchaseHistoryState(
    val purchases: List<Purchase> = emptyList(),
    /** True once the history was loaded at least once (the empty state needs it). */
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    /** Pull to refresh: the list stays on screen. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val filter: OrderFilter = OrderFilter.ALL,
    val query: String = ""
) {
    /** The purchases the chips and the search box let through. */
    val visible: List<Purchase>
        get() {
            val needle = query.trim().lowercase()
            return purchases.filter { purchase ->
                filter.matches(purchase) && (
                    needle.isEmpty() ||
                        purchase.code.lowercase().contains(needle) ||
                        purchase.orders.any { order ->
                            order.sellerName.lowercase().contains(needle) ||
                                order.items.any { it.title.lowercase().contains(needle) }
                        }
                    )
            }
        }

    fun count(filter: OrderFilter): Int = purchases.count(filter::matches)
}

/** The page shown right after paying. */
data class OrderConfirmationState(
    val session: CheckoutSession? = null,
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /** Paid once Stripe confirmed it (the webhook); a simulated purchase is paid at once. */
    val status: OrderStatus
        get() = when {
            orders.isNotEmpty() -> statusOf(orders)
            session?.simulated == true -> OrderStatus.PAID
            else -> OrderStatus.PENDING
        }
    val simulated: Boolean get() = session?.simulated == true || orders.any { it.simulated }
}

/**
 * Order confirmation and purchase history (#144). After paying, the confirmation follows
 * the orders until Stripe's webhook marks them paid; the history lists every purchase.
 */
class OrdersViewModel(
    private val orders: OrdersService,
    private val pollDelayMs: Long = POLL_DELAY_MS,
    private val maxPolls: Int = MAX_POLLS
) : ViewModel() {

    private val _history = MutableStateFlow(PurchaseHistoryState())
    val history: StateFlow<PurchaseHistoryState> = _history.asStateFlow()

    private val _confirmation = MutableStateFlow(OrderConfirmationState())
    val confirmation: StateFlow<OrderConfirmationState> = _confirmation.asStateFlow()

    private var historyJob: Job? = null
    private var confirmationJob: Job? = null

    // ---------- history ----------

    /** Loads the history; with [refresh] the list stays on screen (pull to refresh). */
    fun loadHistory(refresh: Boolean = false) {
        historyJob?.cancel()
        _history.value = _history.value.copy(
            isLoading = !refresh || !_history.value.loaded,
            isRefreshing = refresh && _history.value.loaded,
            error = null
        )
        historyJob = viewModelScope.launch {
            _history.value = try {
                _history.value.copy(
                    purchases = orders.listOrders().toPurchases(),
                    loaded = true,
                    isLoading = false,
                    isRefreshing = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _history.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Could not load your orders"
                )
            }
        }
    }

    fun onFilterSelected(filter: OrderFilter) {
        _history.value = _history.value.copy(filter = filter)
    }

    fun onQueryChanged(query: String) {
        _history.value = _history.value.copy(query = query)
    }

    // ---------- confirmation ----------

    /** Shows the purchase just paid and follows it until Stripe confirms the payment. */
    fun showConfirmation(session: CheckoutSession) {
        _confirmation.value = OrderConfirmationState(session = session, isLoading = true)
        followConfirmation()
    }

    fun retryConfirmation() {
        if (_confirmation.value.session == null) return
        _confirmation.value = _confirmation.value.copy(isLoading = true, error = null)
        followConfirmation()
    }

    fun closeConfirmation() {
        confirmationJob?.cancel()
        _confirmation.value = OrderConfirmationState()
    }

    private fun followConfirmation() {
        val session = _confirmation.value.session ?: return
        confirmationJob?.cancel()
        confirmationJob = viewModelScope.launch {
            var polls = 0
            while (true) {
                try {
                    val loaded = session.orderIds.map { orders.getOrder(it) }
                    _confirmation.value = _confirmation.value.copy(orders = loaded, isLoading = false, error = null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    _confirmation.value = _confirmation.value.copy(
                        isLoading = false,
                        error = "Could not load the order details: ${e.message ?: "check your connection"}"
                    )
                    break
                }
                polls++
                val settled = _confirmation.value.status != OrderStatus.PENDING || session.orderIds.isEmpty()
                if (settled || polls >= maxPolls) break
                delay(pollDelayMs)
            }
            // The history shows the new purchase straight away.
            loadHistory(refresh = _history.value.loaded)
        }
    }

    companion object {
        const val POLL_DELAY_MS = 2_000L
        const val MAX_POLLS = 15
    }
}
