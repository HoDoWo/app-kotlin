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

/** `GET /api/marketplace/orders` and `/orders/{id}` of [RemoteOrdersService] (#144). */
class RemoteOrdersServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteOrdersService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteOrdersService(backendUrl = server.url("/").toString(), tokenProvider = { "token-abc" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun theHistoryIsReadWithTheTokenAndEveryFieldIsParsed() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"orders": [
                    {"order_id": "o1", "checkout_id": "c1", "seller_name": "FixParts Hub",
                     "items": [{"product_type": "part", "product_id": "p1", "title": "Drain pump",
                                "image_url": "https://img/p1.webp", "quantity": 2, "unit_price_cents": 2490,
                                "price_cents": 4980}],
                     "subtotal_cents": 4980, "shipping_method": "standard", "shipping_cents": 490,
                     "delivery": {"from": "2026-11-24", "to": "2026-11-26"}, "pickup_zip": null,
                     "payment_method": "card", "total_cents": 5470, "currency": "eur", "status": "paid",
                     "simulated": false, "created_at": 1700000000000, "paid_at": 1700000005000},
                    {"order_id": "o2", "checkout_id": "c1", "seller_name": "Marc",
                     "items": [{"product_type": "used", "product_id": "l1", "title": "Fridge", "quantity": 1,
                                "price_cents": 12000}],
                     "shipping_method": "pickup", "delivery": null, "pickup_zip": "08012",
                     "total_cents": 12000, "status": "pending", "simulated": true, "created_at": 1700000000000,
                     "paid_at": null}
                ]}"""
            )
        )

        val orders = service.listOrders()

        val request = server.takeRequest()
        assertEquals("/api/marketplace/orders", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        val paid = orders[0]
        assertEquals(OrderStatus.PAID, paid.status)
        assertEquals("FixParts Hub", paid.sellerName)
        assertEquals("2026-11-24", paid.deliveryFrom)
        assertEquals(5470, paid.totalCents)
        assertEquals(1700000005000L, paid.paidAt)
        assertEquals(2, paid.itemCount)
        assertEquals("https://img/p1.webp", paid.items.single().imageUrl)
        assertFalse(paid.isPickup)
        val pickup = orders[1]
        assertTrue(pickup.isPickup && pickup.simulated)
        assertEquals(OrderStatus.PENDING, pickup.status)
        assertNull(pickup.deliveryFrom)
        assertNull(pickup.paidAt)
        assertNull(pickup.items.single().imageUrl) // orders saved before the photo was kept
        assertEquals(12000, pickup.items.single().unitPriceCents)
    }

    @Test
    fun oneOrderIsReadByItsId() = runTest {
        server.enqueue(MockResponse().setBody("""{"order_id": "o 1", "checkout_id": "c1", "status": "failed", "items": []}"""))

        val order = service.getOrder("o 1")

        assertEquals("/api/marketplace/orders/o+1", server.takeRequest().path)
        assertEquals(OrderStatus.FAILED, order.status)
    }

    @Test
    fun errorsSayWhatHappened() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals("Please sign in to see your orders", messageOf { service.listOrders() })
        assertEquals("Order not found", messageOf { service.getOrder("x") })
        assertEquals("Could not load your orders (503)", messageOf { service.listOrders() })
    }

    @Test
    fun unknownStatusesAreNotShownAsPaid() {
        assertEquals(OrderStatus.CANCELLED, OrderStatus.of("canceled"))
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.of("refunded"))
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.of(null))
    }

    private suspend fun messageOf(call: suspend () -> Unit): String? = try {
        call()
        fail("expected an IOException")
        null
    } catch (e: IOException) {
        e.message
    }
}
