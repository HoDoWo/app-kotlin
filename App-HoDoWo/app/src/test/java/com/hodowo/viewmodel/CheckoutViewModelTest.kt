package com.hodowo.viewmodel

import com.hodowo.data.Cart
import com.hodowo.data.CartSelection
import com.hodowo.data.CartService
import com.hodowo.data.CartStore
import com.hodowo.data.CheckoutException
import com.hodowo.data.CheckoutService
import com.hodowo.data.CheckoutSession
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Unit tests for [CheckoutViewModel]: orders, PaymentSheet results and the cart refresh. */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val checkout: CheckoutService = mockk()
    private val cartService: CartService = mockk()
    private val cart = CartStore(cartService)

    private val session = CheckoutSession(
        checkoutId = "c1", orderIds = listOf("o1"), totalCents = 4980, currency = "eur",
        simulated = false, clientSecret = "pi_1_secret_abc"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { cartService.getCart() } returns Cart()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(key: String = "pk_test_123") = CheckoutViewModel(checkout, cart, key)

    @Test
    fun placeOrderOpensThePaymentSheetWithTheClientSecret() = runTest(dispatcher) {
        coEvery { checkout.startCheckout() } returns session
        val vm = viewModel()

        vm.placeOrder()
        advanceUntilIdle()

        assertEquals(PaymentRequest("pi_1_secret_abc", "pk_test_123"), vm.uiState.value.paymentToPresent)
        assertFalse(vm.uiState.value.isPreparing)

        vm.onPaymentPresented()
        assertNull(vm.uiState.value.paymentToPresent)
    }

    @Test
    fun aCompletedPaymentShowsTheOrderAndReloadsTheCart() = runTest(dispatcher) {
        coEvery { checkout.startCheckout() } returns session
        val vm = viewModel()
        vm.placeOrder()
        advanceUntilIdle()
        vm.onPaymentPresented()

        vm.onPaymentResult(PaymentOutcome.Completed)
        advanceUntilIdle()

        assertEquals(session, vm.uiState.value.placedOrder)
        coVerify(exactly = 1) { cartService.getCart() }

        vm.dismissConfirmation()
        assertNull(vm.uiState.value.placedOrder)
    }

    @Test
    fun aCancelledPaymentKeepsTheCartAndAllowsARetry() = runTest(dispatcher) {
        coEvery { checkout.startCheckout() } returns session
        val vm = viewModel()
        vm.placeOrder()
        advanceUntilIdle()

        vm.onPaymentResult(PaymentOutcome.Canceled)
        assertEquals("Payment cancelled: your cart is still here", vm.uiState.value.message)
        assertNull(vm.uiState.value.placedOrder)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)

        vm.placeOrder()
        advanceUntilIdle()
        coVerify(exactly = 2) { checkout.startCheckout() }
    }

    @Test
    fun aFailedPaymentShowsStripeReason() = runTest(dispatcher) {
        coEvery { checkout.startCheckout() } returns session
        val vm = viewModel()
        vm.placeOrder()
        advanceUntilIdle()

        vm.onPaymentResult(PaymentOutcome.Failed("Your card was declined."))
        assertEquals("Payment failed: Your card was declined.", vm.uiState.value.message)

        vm.onPaymentResult(PaymentOutcome.Failed(null))
        assertEquals("Payment failed", vm.uiState.value.message)
    }

    @Test
    fun aSimulatedPurchaseIsConfirmedWithoutThePaymentSheet() = runTest(dispatcher) {
        val simulated = session.copy(simulated = true, clientSecret = null)
        coEvery { checkout.startCheckout() } returns simulated
        val vm = viewModel()

        vm.placeOrder()
        advanceUntilIdle()

        assertNull(vm.uiState.value.paymentToPresent)
        assertEquals(simulated, vm.uiState.value.placedOrder)
        coVerify(exactly = 1) { cartService.getCart() }
    }

    @Test
    fun withoutAPublishableKeyThePaymentSheetIsNotOpened() = runTest(dispatcher) {
        coEvery { checkout.startCheckout() } returns session
        val vm = viewModel(key = "")

        vm.placeOrder()
        advanceUntilIdle()

        assertNull(vm.uiState.value.paymentToPresent)
        assertEquals("Payments are not set up in this app (stripe.publishable.key is missing)", vm.uiState.value.message)
    }

    @Test
    fun aRefusedCheckoutShowsTheBackendReason() = runTest(dispatcher) {
        coEvery { checkout.startCheckout() } throws CheckoutException("some cart items cannot be bought: already sold")
        val vm = viewModel()

        vm.placeOrder()
        advanceUntilIdle()

        assertEquals("some cart items cannot be bought: already sold", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isPreparing)
    }

    @Test
    fun aNetworkErrorAsksToCheckTheConnection() = runTest(dispatcher) {
        coEvery { checkout.startCheckout() } throws IOException("timeout")
        val vm = viewModel()

        vm.placeOrder()
        advanceUntilIdle()

        assertEquals("Could not start the payment, check your connection", vm.uiState.value.message)
    }

    @Test
    fun aSecondTapWhilePreparingIsIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<CheckoutSession>()
        coEvery { checkout.startCheckout() } coAnswers { gate.await() }
        val vm = viewModel()

        vm.placeOrder()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isPreparing)

        vm.placeOrder()
        gate.complete(session)
        advanceUntilIdle()

        coVerify(exactly = 1) { checkout.startCheckout() }
    }

    @Test
    fun aCartRefreshErrorAfterPayingStillShowsTheOrder() = runTest(dispatcher) {
        coEvery { checkout.startCheckout() } returns session
        coEvery { cartService.getCart() } throws IOException("offline")
        val vm = viewModel()
        vm.placeOrder()
        advanceUntilIdle()

        vm.onPaymentResult(PaymentOutcome.Completed)
        advanceUntilIdle()

        assertEquals(session, vm.uiState.value.placedOrder)
    }

    @Test
    fun theCartSelectionIsSentAndGooglePayReachesThePaymentSheet() = runTest(dispatcher) {
        val selection = CartSelection(listOf("c1"), mapOf("voltacasa" to "express"), "google_pay")
        coEvery { checkout.startCheckout(selection) } returns session
        val vm = viewModel()

        vm.placeOrder(selection)
        advanceUntilIdle()

        coVerify(exactly = 1) { checkout.startCheckout(selection) }
        assertEquals(PaymentRequest("pi_1_secret_abc", "pk_test_123", "google_pay"), vm.uiState.value.paymentToPresent)
    }
}