package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.Cart
import com.hodowo.data.CartException
import com.hodowo.data.CartLine
import com.hodowo.data.CartStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Everything the cart screen shows. */
data class CartUiState(
    val cart: Cart = Cart(),
    val isLoading: Boolean = false,
    /** Line being changed, so its buttons can be disabled while waiting. */
    val updatingLineId: String? = null,
    /** Loading the cart failed: the screen shows it with a retry button. */
    val error: String? = null,
    /** One-off feedback of a refused change ("not enough stock"); cleared by [CartViewModel.consumeMessage]. */
    val message: String? = null
)

/**
 * State of the cart screen (#142). Totals, availability and price changes come from the
 * backend through the shared [CartStore]; the app never computes a total itself.
 */
class CartViewModel(private val store: CartStore) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState(cart = store.cart.value))
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    init {
        // The cart can also change from other screens (add to cart, payment).
        viewModelScope.launch {
            store.cart.collect { cart -> _uiState.value = _uiState.value.copy(cart = cart) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            _uiState.value = try {
                store.refresh()
                _uiState.value.copy(isLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load your cart")
            }
        }
    }

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

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun change(line: CartLine, action: suspend () -> Unit) {
        if (_uiState.value.updatingLineId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(updatingLineId = line.cartItemId, message = null)
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
            _uiState.value = _uiState.value.copy(updatingLineId = null, message = message)
        }
    }
}