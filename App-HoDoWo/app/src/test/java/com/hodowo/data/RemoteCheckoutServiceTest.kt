package com.hodowo.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** `POST /api/marketplace/checkout` calls of [RemoteCheckoutService] (Backend#43). */
class RemoteCheckoutServiceTest {

    private lateinit var server: MockWebServer
    private var token: String? = "token-abc"
    private lateinit var service: RemoteCheckoutService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteCheckoutService(backendUrl = server.url("/").toString(), tokenProvider = { token })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun failure(code: Int, body: String) = MockResponse().setResponseCode(code).setBody(body)

    private suspend fun errorMessage(): String? = try {
        service.startCheckout()
        fail("expected an exception")
        null
    } catch (e: CheckoutException) {
        e.message
    }

    @Test
    fun checkoutPostsWithTheTokenAndNoAmount() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"checkout_id": "c1", "order_ids": ["o1", "o2"], "total_cents": 4980,
                    "currency": "eur", "simulated": false, "client_secret": "pi_1_secret_abc"}"""
            )
        )

        val session = service.startCheckout()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/marketplace/checkout", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        assertEquals("{\"delivery\":{}}", request.body.readUtf8())

        assertEquals("c1", session.checkoutId)
        assertEquals(listOf("o1", "o2"), session.orderIds)
        assertEquals(4980, session.totalCents)
        assertEquals("eur", session.currency)
        assertFalse(session.simulated)
        assertEquals("pi_1_secret_abc", session.clientSecret)
    }

    @Test
    fun aSimulatedCheckoutHasNoClientSecretAndNoTokenMeansNoHeader() = runTest {
        token = null
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"checkout_id": "c2", "order_ids": ["o3"], "total_cents": 1200, "simulated": true, "client_secret": null}"""
            )
        )

        val session = service.startCheckout()

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertTrue(session.simulated)
        assertNull(session.clientSecret)
        assertEquals("eur", session.currency)
    }

    @Test
    fun unbuyableItemsListTheReasons() = runTest {
        server.enqueue(
            failure(
                409,
                """{"detail": {"message": "some cart items cannot be bought",
                    "items": [{"product_id": "l1", "reason": "already sold"},
                              {"product_id": "p1", "reason": "not enough stock"},
                              {"product_id": "p2", "reason": "not enough stock"}]}}"""
            )
        )

        assertEquals("some cart items cannot be bought: already sold, not enough stock", errorMessage())
    }

    @Test
    fun aTextDetailIsShownAsItIs() = runTest {
        server.enqueue(failure(400, """{"detail": "the cart is empty"}"""))

        assertEquals("the cart is empty", errorMessage())
    }

    @Test
    fun anObjectDetailWithoutItemsKeepsItsMessage() = runTest {
        server.enqueue(failure(409, """{"detail": {"message": ""}}"""))

        assertEquals("Some items cannot be bought", errorMessage())
    }

    @Test
    fun aResponseWithoutDetailGetsAGenericMessage() = runTest {
        server.enqueue(failure(502, "Bad gateway"))

        assertEquals("The payment could not be started (502)", errorMessage())
    }

    @Test
    fun anExpiredSessionAsksToSignInAgain() = runTest {
        server.enqueue(failure(401, "{}"))

        try {
            service.startCheckout()
            fail("expected an IOException")
        } catch (e: IOException) {
            assertFalse(e is CheckoutException)
            assertEquals("Not authorized: please sign in again", e.message)
        }
    }

    @Test(expected = IOException::class)
    fun anInvalidResponseIsAnIOException() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>"))

        service.startCheckout()
    }

    @Test
    fun detailOfIgnoresOtherShapes() {
        assertNull(RemoteCheckoutService.detailOf("""{"detail": [1, 2]}"""))
        assertNull(RemoteCheckoutService.detailOf("""{"detail": "  "}"""))
        assertNull(RemoteCheckoutService.detailOf("not json"))
    }

    @Test
    fun theSelectionIsSentAndShippingIsRead() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"checkout_id": "c3", "order_ids": ["o1"], "subtotal_cents": 2500, "shipping_cents": 990,
                    "total_cents": 3490, "payment_method": "klarna", "simulated": false, "client_secret": "pi_s"}"""
            )
        )

        val session = service.startCheckout(
            CartSelection(cartItemIds = listOf("c1"), delivery = mapOf("voltacasa" to "express"), paymentMethod = "klarna")
        )

        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("c1", body.getJSONArray("cart_item_ids").getString(0))
        assertEquals("express", body.getJSONObject("delivery").getString("voltacasa"))
        assertEquals("klarna", body.getString("payment_method"))
        assertFalse(body.has("total_cents"))
        assertEquals(2500, session.subtotalCents)
        assertEquals(990, session.shippingCents)
        assertEquals(3490, session.totalCents)
        assertEquals("klarna", session.paymentMethod)
    }
}