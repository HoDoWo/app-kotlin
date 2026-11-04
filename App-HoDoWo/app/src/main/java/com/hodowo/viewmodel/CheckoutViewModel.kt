package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.CartStore
import com.hodowo.data.CheckoutException
import com.hodowo.data.CheckoutService
import com.hodowo.data.CheckoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** What the Stripe PaymentSheet needs to open. */
data class PaymentRequest(val clientSecret: String, val publishableKey: String)

/** Result of the PaymentSheet, without Stripe types, so the logic can be unit tested. */
sealed interface PaymentOutcome {
    data object Completed : PaymentOutcome
    data object Canceled : PaymentOutcome
    data class Failed(val message: String?) : PaymentOutcome
}

data class CheckoutUiState(
    /** Waiting for the backend to create the orders and the PaymentIntent. */
    val isPreparing: Boolean = false,
    /** Set when the PaymentSheet must open; cleared by [CheckoutViewModel.onPaymentPresented]. */
    val paymentToPresent: PaymentRequest? = null,
    /** The order just placed, for the confirmation dialog. */
    val placedOrder: CheckoutSession? = null,
    /** One-off feedback ("Payment cancelled"…); cleared by [CheckoutViewModel.consumeMessage]. */
    val message: String? = null
)

/**
 * "Place Order" (#143): asks the backend to create the orders (Backend#43), opens the Stripe
 * PaymentSheet with the returned client secret and, once paid, reloads the cart, which the
 * Stripe webhook empties on the server.
 *
 * Card data never passes through the app or the backend: it is entered in Stripe's sheet.
 */
class CheckoutViewModel(
    private val checkout: CheckoutService,
    private val cart: CartStore,
    private val publishableKey: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    /** Session being paid in the PaymentSheet. */
    private var pendingSession: CheckoutSession? = null

    fun placeOrder() {
        val state = _uiState.value
        if (state.isPreparing || state.paymentToPresent != null || pendingSession != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPreparing = true, message = null)
            try {
                val session = checkout.startCheckout()
                val secret = session.clientSecret
                when {
                    session.simulated || secret == null -> {
                        // No Stripe keys on the server: the purchase is already recorded as paid.
                        refreshCart()
                        _uiState.value = _uiState.value.copy(placedOrder = session)
                    }
                    publishableKey.isBlank() -> _uiState.value = _uiState.value.copy(
                        message = "Payments are not set up in this app (stripe.publishable.key is missing)"
                    )
                    else -> {
                        pendingSession = session
                        _uiState.value = _uiState.value.copy(paymentToPresent = PaymentRequest(secret, publishableKey))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CheckoutException) {
                _uiState.value = _uiState.value.copy(message = e.message)
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(message = "Could not start the payment, check your connection")
            } finally {
                _uiState.value = _uiState.value.copy(isPreparing = false)
            }
        }
    }

    /** The screen opened the PaymentSheet: do not open it again on recomposition. */
    fun onPaymentPresented() {
        _uiState.value = _uiState.value.copy(paymentToPresent = null)
    }

    fun onPaymentResult(outcome: PaymentOutcome) {
        val session = pendingSession
        pendingSession = null
        _uiState.value = _uiState.value.copy(paymentToPresent = null)
        when (outcome) {
            PaymentOutcome.Completed -> {
                _uiState.value = _uiState.value.copy(placedOrder = session)
                viewModelScope.launch { refreshCart() }
            }
            PaymentOutcome.Canceled ->
                _uiState.value = _uiState.value.copy(message = "Payment cancelled: your cart is still here")
            is PaymentOutcome.Failed ->
                _uiState.value = _uiState.value.copy(
                    message = "Payment failed" + (outcome.message?.let { ": $it" } ?: "")
                )
        }
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(placedOrder = null)
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private suspend fun refreshCart() {
        try {
            cart.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The cart screen reloads it when opened again.
        }
    }
}