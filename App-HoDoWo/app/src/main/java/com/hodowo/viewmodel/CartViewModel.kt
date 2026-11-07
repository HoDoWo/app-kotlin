package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.Cart
import com.hodowo.data.CartException
import com.hodowo.data.CartGroup
import com.hodowo.data.CartLine
import com.hodowo.data.CartSelection
import com.hodowo.data.CartStore
import com.hodowo.data.PAYMENT_CARD
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Everything the cart screen shows. */
data class CartUiState(
    val cart: Cart = Cart(),
    val isLoading: Boolean = false,
    /** Line being changed, so its buttons can be disabled while waiting. */
    val updatingLineId: String? = null,
    /** The totals are being recomputed for a new selection or delivery choice. */
    val isQuoting: Boolean = false,
    /** Loading the cart failed: the screen shows it with a retry button. */
    val error: String? = null,
    /** One-off feedback of a refused change ("not enough stock"); cleared by [CartViewModel.consumeMessage]. */
    val message: String? = null,
    /** Lines the user unticked: they stay in the cart and are not bought now. */
    val deselected: Set<String> = emptySet(),
    /** Delivery chosen per seller; a seller without a choice gets its cheapest option. */
    val delivery: Map<String, String> = emptyMap(),
    /** "card", "google_pay" or "klarna". */
    val paymentMethod: String = PAYMENT_CARD
) {
    /** Lines of one seller's group, in cart order. */
    fun linesOf(group: CartGroup): List<CartLine> = cart.items.filter { it.cartItemId in group.cartItemIds }

    /** Lines the backend can no longer sell: shown apart, never bought. */
    val unavailableLines: List<CartLine> get() = cart.items.filter { !it.available }

    val allSelected: Boolean get() = cart.items.filter { it.available }.all { it.cartItemId !in deselected }
}

/**
 * State of the cart screen (#142): lines split by seller, which ones to buy now, the delivery of
 * each seller and the payment method. Totals, shipping and availability always come from the
 * backend (quote), so the screen shows exactly what the checkout will charge.
 */
class CartViewModel(private val store: CartStore) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState(cart = store.cart.value))
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    init {
        // The cart can also change from other screens (add to cart, payment).
        viewModelScope.launch {
            store.cart.collect { cart ->
                _uiState.update { it.copy(cart = cart).pruned() }
                if (!matchesChoices(cart)) requote()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                if (hasChoices()) store.quote(selection()) else store.refresh()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load your cart") }
            }
        }
    }

    // ---------- quantities ----------

    fun increase(line: CartLine) = change(line) { store.setQuantity(line.cartItemId, line.quantity + 1) }

    /** Lowers the quantity; at 1 the line is removed, as the "−" button suggests. */
    fun decrease(line: CartLine) = change(line) {
        if (line.quantity <= 1) store.remove(line.cartItemId) else store.setQuantity(line.cartItemId, line.quantity - 1)
    }

    fun remove(line: CartLine) = change(line) { store.remove(line.cartItemId) }

    /** "Empty Cart": removes every line (the cart API has no single "clear" call). */
    fun clear() {
        val lines = _uiState.value.cart.items
        val first = lines.firstOrNull() ?: return
        change(first) {
            lines.forEach { store.remove(it.cartItemId) }
        }
    }

    // ---------- selection, delivery, payment ----------

    /** Ticks or unticks a line; unticked lines stay in the cart for later. */
    fun toggleSelected(line: CartLine) {
        if (!line.available) return
        _uiState.update {
            val deselected = if (line.cartItemId in it.deselected) it.deselected - line.cartItemId else it.deselected + line.cartItemId
            it.copy(deselected = deselected)
        }
        requote()
    }

    fun selectAll(selected: Boolean) {
        _uiState.update {
            it.copy(deselected = if (selected) emptySet() else it.cart.items.filter { line -> line.available }.map { line -> line.cartItemId }.toSet())
        }
        requote()
    }

    fun chooseDelivery(sellerId: String, method: String) {
        val group = _uiState.value.cart.groups.firstOrNull { it.sellerId == sellerId } ?: return
        if (group.options.none { it.method == method } || group.method == method) return
        _uiState.update { it.copy(delivery = it.delivery + (sellerId to method)) }
        requote()
    }

    fun choosePaymentMethod(method: String) {
        if (method in _uiState.value.cart.paymentMethods) _uiState.update { it.copy(paymentMethod = method) }
    }

    /** What "Place Order" pays: the ticked available lines, each seller's delivery, the method. */
    fun selection(): CartSelection {
        val state = _uiState.value
        return CartSelection(
            cartItemIds = state.cart.items.filter { it.available && it.cartItemId !in state.deselected }.map { it.cartItemId },
            delivery = state.delivery,
            paymentMethod = state.paymentMethod
        )
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ---------- helpers ----------

    private fun hasChoices(): Boolean = _uiState.value.let { it.deselected.isNotEmpty() || it.delivery.isNotEmpty() }

    /** True when [cart] already reflects the ticks and delivery choices (no new quote needed). */
    private fun matchesChoices(cart: Cart): Boolean {
        val state = _uiState.value
        val ticksMatch = cart.items.filter { it.available }.all { it.selected == (it.cartItemId !in state.deselected) }
        val deliveryMatches = state.delivery.all { (seller, method) -> cart.groups.firstOrNull { it.sellerId == seller }?.method == method }
        return ticksMatch && deliveryMatches
    }

    /** Asks the backend for the totals of the current selection and delivery choices. */
    private fun requote() {
        viewModelScope.launch {
            _uiState.update { it.copy(isQuoting = true) }
            val message = try {
                store.quote(selection())
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: CartException) {
                // A choice the backend refused (e.g. an option no longer offered): back to its default.
                _uiState.update { it.copy(delivery = emptyMap()) }
                e.message
            } catch (e: IOException) {
                "Could not update the totals, check your connection"
            }
            _uiState.update { it.copy(isQuoting = false, message = message ?: it.message) }
        }
    }

    private fun change(line: CartLine, action: suspend () -> Unit) {
        if (_uiState.value.updatingLineId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(updatingLineId = line.cartItemId, message = null) }
            val message = try {
                action()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: CartException) {
                e.message
            } catch (e: IOException) {
                "Could not update your cart, check your connection"
            }
            _uiState.update { it.copy(updatingLineId = null, message = message) }
        }
    }
}

/** Drops ticks and delivery choices that refer to lines or sellers no longer in the cart. */
private fun CartUiState.pruned(): CartUiState {
    val lineIds = cart.items.map { it.cartItemId }.toSet()
    val sellers = cart.groups.map { it.sellerId }.toSet()
    return copy(
        deselected = deselected.filter { it in lineIds }.toSet(),
        // Groups are only known after a backend answer; before that, keep the choices.
        delivery = if (cart.groups.isEmpty()) delivery else delivery.filterKeys { it in sellers }
    )
}