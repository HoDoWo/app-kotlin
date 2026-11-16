package com.hodowo.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Cart API calls of [RemoteCartService] (Backend#42) against a fake backend. */
class RemoteCartServiceTest {

    private lateinit var server: MockWebServer
    private var token: String? = "token-abc"
    private lateinit var service: RemoteCartService

    private val cartJson = """
        {
          "items": [
            {"cart_item_id": "u1_part_HDW-P-001", "product_type": "part", "product_id": "HDW-P-001",
             "title": "Drain pump", "image_url": "https://example.test/pump.jpg", "quantity": 2,
             "unit_price_cents": 2490, "price_cents": 4980, "added_price_cents": 2290,
             "price_changed": true, "available": true, "unavailable_reason": null},
            {"cart_item_id": "u1_used_l9", "product_type": "used", "product_id": "l9",
             "title": "Old fridge", "image_url": null, "quantity": 1,
             "unit_price_cents": null, "price_cents": null, "added_price_cents": 12000,
             "price_changed": false, "available": false, "unavailable_reason": "already sold"}
          ],
          "item_count": 2,
          "total_cents": 4980,
          "currency": "eur",
          "has_unavailable_items": true
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteCartService(backendUrl = server.url("/").toString(), tokenProvider = { token })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String = cartJson) = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun getCartReadsEveryFieldWithTheToken() = runTest {
        server.enqueue(ok())

        val cart = service.getCart()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/marketplace/cart", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))

        assertEquals(2, cart.itemCount)
        assertEquals(4980, cart.totalCents)
        assertEquals("eur", cart.currency)
        assertTrue(cart.hasUnavailableItems)
        assertFalse(cart.canCheckout)

        val pump = cart.items[0]
        assertEquals("u1_part_HDW-P-001", pump.cartItemId)
        assertEquals("part", pump.productType)
        assertEquals("HDW-P-001", pump.productId)
        assertEquals("Drain pump", pump.title)
        assertEquals("https://example.test/pump.jpg", pump.imageUrl)
        assertEquals(2, pump.quantity)
        assertEquals(2490, pump.unitPriceCents)
        assertEquals(4980, pump.priceCents)
        assertEquals(2290, pump.addedPriceCents)
        assertTrue(pump.priceChanged)
        assertTrue(pump.available)
        assertNull(pump.unavailableReason)

        val fridge = cart.items[1]
        assertNull(fridge.imageUrl)
        assertNull(fridge.unitPriceCents)
        assertNull(fridge.priceCents)
        assertFalse(fridge.available)
        assertEquals("already sold", fridge.unavailableReason)
    }

    @Test
    fun anEmptyResponseIsAnEmptyCartAndNoTokenMeansNoHeader() = runTest {
        token = null
        server.enqueue(ok("{}"))

        val cart = service.getCart()

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertTrue(cart.items.isEmpty())
        assertEquals(0, cart.itemCount)
        assertEquals("eur", cart.currency)
        assertFalse(cart.canCheckout)
    }

    @Test
    fun addItemPostsTypeIdAndQuantity() = runTest {
        server.enqueue(ok())

        service.addItem("part", "HDW-P-001", 2)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/marketplace/cart/items", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("part", body.getString("product_type"))
        assertEquals("HDW-P-001", body.getString("product_id"))
        assertEquals(2, body.getInt("quantity"))
    }

    @Test
    fun setQuantityPatchesTheLine() = runTest {
        server.enqueue(ok())

        service.setQuantity("u1_part_HDW-P-001", 3)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/marketplace/cart/items/u1_part_HDW-P-001", request.path)
        assertEquals(3, JSONObject(request.body.readUtf8()).getInt("quantity"))
    }

    @Test
    fun removeItemDeletesTheLineWithAnEncodedId() = runTest {
        server.enqueue(ok("""{"items": [], "item_count": 0, "total_cents": 0, "currency": "eur"}"""))

        val cart = service.removeItem("u1 used l9")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/marketplace/cart/items/u1%20used%20l9", request.path)
        assertTrue(cart.items.isEmpty())
    }

    @Test
    fun aRefusedChangeCarriesTheBackendReason() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail": "already sold"}"""))

        try {
            service.addItem("used", "l9")
            fail("expected a CartException")
        } catch (e: CartException) {
            assertEquals("already sold", e.message)
        }
    }

    @Test
    fun aValidationErrorGetsAGenericMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail": [{"msg": "too big"}]}"""))

        try {
            service.setQuantity("line", 500)
            fail("expected a CartException")
        } catch (e: CartException) {
            assertEquals("This cart change is not possible", e.message)
        }
    }

    @Test
    fun aRefusalWithoutJsonGetsAGenericMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        try {
            service.removeItem("line")
            fail("expected a CartException")
        } catch (e: CartException) {
            assertEquals("This cart change is not possible", e.message)
        }
    }

    @Test
    fun anExpiredSessionAsksToSignInAgain() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        try {
            service.getCart()
            fail("expected an IOException")
        } catch (e: IOException) {
            assertFalse(e is CartException)
            assertEquals("Not authorized: please sign in again", e.message)
        }
    }

    @Test
    fun aServerErrorIsAnIOException() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        try {
            service.getCart()
            fail("expected an IOException")
        } catch (e: IOException) {
            assertFalse(e is CartException)
            assertEquals("Server error 503", e.message)
        }
    }

    @Test(expected = IOException::class)
    fun anInvalidResponseIsAnIOException() = runTest {
        server.enqueue(ok("<html>"))

        service.getCart()
    }

    private val quotedJson = """
        {
          "items": [
            {"cart_item_id": "c1", "product_type": "part", "product_id": "pump-1", "title": "Drain pump",
             "subtitle": "Bosch · #00145787", "seller_id": "voltacasa", "seller_name": "VoltaCasa",
             "seller_kind": "partner", "quantity": 1, "unit_price_cents": 2500, "price_cents": 2500,
             "available": true, "selected": true},
            {"cart_item_id": "c3", "product_type": "used", "product_id": "fridge-2", "title": "Used fridge",
             "seller_id": "seller-9", "seller_name": "Marc", "seller_kind": "community", "quantity": 1,
             "unit_price_cents": 12000, "price_cents": 12000, "available": true, "selected": false}
          ],
          "item_count": 2, "total_cents": 14500, "currency": "eur", "has_unavailable_items": false,
          "groups": [
            {"seller_id": "voltacasa", "seller_name": "VoltaCasa", "seller_kind": "partner",
             "cart_item_ids": ["c1"], "selected_count": 1,
             "options": [
               {"method": "standard", "label": "Standard shipping", "price_cents": 490,
                "delivery": {"from": "2026-11-11", "to": "2026-11-13"}},
               {"method": "express", "label": "Express shipping", "price_cents": 990,
                "delivery": {"from": "2026-11-09", "to": "2026-11-10"}}
             ],
             "method": "express", "delivery": {"from": "2026-11-09", "to": "2026-11-10"},
             "pickup_area": null, "subtotal_cents": 2500, "shipping_cents": 990, "total_cents": 3490},
            {"seller_id": "seller-9", "seller_name": "Marc", "seller_kind": "community",
             "cart_item_ids": ["c3"], "selected_count": 0,
             "options": [{"method": "pickup", "label": "Local pickup", "price_cents": 0, "delivery": null}],
             "method": "pickup", "delivery": null, "pickup_area": "080xx",
             "subtotal_cents": 0, "shipping_cents": 0, "total_cents": 0}
          ],
          "summary": {"selected_item_count": 1, "subtotal_cents": 2500, "shipping_cents": 990,
                      "total_cents": 3490, "vat_included_cents": 606, "can_checkout": true},
          "payment_methods": ["card", "google_pay", "klarna"]
        }
    """.trimIndent()

    @Test
    fun quotePostsTheSelectionAndReadsGroupsAndSummary() = runTest {
        server.enqueue(ok(quotedJson))

        val cart = service.quote(CartSelection(cartItemIds = listOf("c1"), delivery = mapOf("voltacasa" to "express")))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/marketplace/checkout/quote", request.path)
        val body = org.json.JSONObject(request.body.readUtf8())
        assertEquals("express", body.getJSONObject("delivery").getString("voltacasa"))

        assertEquals("Bosch · #00145787", cart.items[0].subtitle)
        assertEquals("VoltaCasa", cart.items[0].sellerName)
        assertEquals(false, cart.items[1].selected)
        assertEquals("community", cart.items[1].sellerKind)

        val partner = cart.groups[0]
        assertEquals(listOf("standard", "express"), partner.options.map { it.method })
        assertEquals(DeliveryWindow("2026-11-09", "2026-11-10"), partner.delivery)
        assertEquals("express", partner.method)
        assertEquals(990, partner.shippingCents)
        val community = cart.groups[1]
        assertEquals("080xx", community.pickupArea)
        assertEquals(null, community.options[0].delivery)

        assertEquals(CartSummary(1, 2500, 990, 3490, 606, true), cart.summary)
        assertEquals(true, cart.canCheckout)
        assertEquals(listOf("card", "google_pay", "klarna"), cart.paymentMethods)
    }

    @Test
    fun anOlderCartWithoutGroupsStillWorks() = runTest {
        server.enqueue(ok())

        val cart = service.getCart()

        assertEquals(emptyList<CartGroup>(), cart.groups)
        assertEquals(listOf("card"), cart.paymentMethods)
        // An unavailable line blocks the old-style summary, as before.
        assertEquals(false, cart.canCheckout)
        assertEquals("community", cart.items[1].sellerKind)
        assertEquals(false, cart.items[1].selected)
    }
}