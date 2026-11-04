package com.hodowo.data

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared [CartStore], price formatting and the cart helpers. */
class CartStoreTest {

    private val service: CartService = mockk()
    private val store = CartStore(service)

    private fun cart(count: Int) = Cart(itemCount = count, totalCents = count * 1000)

    @Test
    fun everyChangeReplacesTheSharedCart() = runTest {
        coEvery { service.getCart() } returns cart(1)
        coEvery { service.addItem("part", "p1", 1) } returns cart(2)
        coEvery { service.setQuantity("line", 3) } returns cart(3)
        coEvery { service.removeItem("line") } returns cart(0)

        assertEquals(0, store.cart.value.itemCount)
        store.refresh()
        assertEquals(1, store.cart.value.itemCount)
        store.add("part", "p1")
        assertEquals(2, store.cart.value.itemCount)
        store.setQuantity("line", 3)
        assertEquals(3, store.cart.value.itemCount)
        store.remove("line")
        assertEquals(0, store.cart.value.itemCount)
    }

    @Test
    fun aRefusedChangeKeepsTheLastCart() = runTest {
        coEvery { service.getCart() } returns cart(1)
        coEvery { service.addItem(any(), any(), any()) } throws CartException("already sold")
        store.refresh()

        try {
            store.add("used", "l1")
        } catch (e: CartException) {
            assertEquals("already sold", e.message)
        }

        assertEquals(1, store.cart.value.itemCount)
    }

    @Test
    fun theUnavailableServiceKeepsAnEmptyCartAndRefusesChanges() = runTest {
        assertTrue(UnavailableCartService.getCart().items.isEmpty())
        listOf<suspend () -> Unit>(
            { UnavailableCartService.addItem("part", "p1") },
            { UnavailableCartService.setQuantity("line", 2) },
            { UnavailableCartService.removeItem("line") }
        ).forEach { change ->
            try {
                change()
                throw AssertionError("expected a CartException")
            } catch (e: CartException) {
                assertEquals("The cart is not available right now", e.message)
            }
        }
    }

    @Test
    fun onlyBackendProductTypesCanBeBought() {
        fun product(type: String) = MarketplaceProduct(id = "x", title = "X", description = "", type = type)

        assertEquals("new", product("new").cartProductType())
        assertEquals("used", product("used").cartProductType())
        assertEquals("part", product("part").cartProductType())
        assertNull(product("appliance").cartProductType())
    }

    @Test
    fun checkoutNeedsItemsAndNothingUnavailable() {
        val line = CartLine("l", "part", "p", "Pump", null, 1, 100, 100, 100, false, true, null)

        assertFalse(Cart().canCheckout)
        assertTrue(Cart(items = listOf(line)).canCheckout)
        assertFalse(Cart(items = listOf(line), hasUnavailableItems = true).canCheckout)
    }

    @Test
    fun pricesAreFormattedFromCents() {
        assertEquals("€24.90", formatPriceCents(2490, "eur"))
        assertEquals("€0.05", formatPriceCents(5, "EUR"))
        assertEquals("12.00 USD", formatPriceCents(1200, "usd"))
    }
}