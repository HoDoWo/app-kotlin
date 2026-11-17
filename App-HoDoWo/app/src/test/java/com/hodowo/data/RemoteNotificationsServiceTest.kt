package com.hodowo.data

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RemoteNotificationsServiceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun service(token: String? = "token-abc") =
        RemoteNotificationsService(server.url("/").toString(), tokenProvider = { token })

    @Test
    fun notificationsAreReadWithTheToken() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"items": [
                    {"id": "conversation:c1:5", "kind": "offer", "title": "New offer: €80.00",
                     "body": "Anna wants to buy Old fridge", "image_url": "https://x.test/f.jpg",
                     "created_at": 5, "unread": true, "target": {"type": "conversation", "id": "c1"}},
                    {"id": "price:c2:2500", "kind": "price_drop", "title": "Price drop in your cart",
                     "body": "Pump now costs €25.00", "image_url": null, "created_at": null,
                     "unread": true, "target": {"type": "cart"}}
                ], "unread": 2}"""
            )
        )

        val items = service().notifications()

        val request = server.takeRequest()
        assertEquals("/api/notifications", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        assertEquals(2, items.size)
        assertEquals(NotificationTarget.Conversation("c1"), items[0].target)
        assertEquals("https://x.test/f.jpg", items[0].imageUrl)
        assertEquals(5L, items[0].createdAt)
        assertEquals(NotificationTarget.Cart, items[1].target)
        assertNull(items[1].imageUrl)
        assertNull(items[1].createdAt)
    }

    @Test
    fun signedOutThereIsNothingToAsk() = runTest {
        assertEquals(emptyList<AppNotification>(), service(token = null).notifications())
        assertEquals(0, server.requestCount)
    }

    @Test(expected = IOException::class)
    fun aServerErrorIsAnIOException() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        service().notifications()
    }

    @Test(expected = IOException::class)
    fun anInvalidAnswerIsAnIOException() = runTest {
        server.enqueue(MockResponse().setBody("not json"))
        service().notifications()
    }

    @Test
    fun aSponsoredProductOpensItsPage() {
        val items = RemoteNotificationsService.parse(
            JSONObject(
                """{"items": [{"id": "sponsored:p1", "kind": "sponsored", "title": "Sponsored", "body": "Filter",
                    "unread": false, "target": {"type": "product", "id": "p1", "product": {
                    "id": "p1", "type": "new", "title": "Bosch filter", "partner_name": "Bosch Store",
                    "price_cents": 1999, "sponsored": true, "listing_kind": "part", "image_url": "https://x.test/p.jpg"}}}]}"""
            )
        )

        val product = (items.single().target as NotificationTarget.Product).product
        assertEquals("p1", product.id)
        assertEquals("new", product.type)
        assertEquals(19.99, product.price, 0.001)
        assertEquals("Bosch Store", product.partnerName)
        assertTrue(product.sponsored)
        assertTrue(product.isPart)
    }

    @Test
    fun itemsWithoutIdOrWithAnUnknownTargetAreHandled() {
        val items = RemoteNotificationsService.parse(
            JSONObject(
                """{"items": [{"kind": "message"}, {"id": "x", "target": {"type": "somewhere"}},
                    {"id": "y", "target": {"type": "product"}}, "not an object"]}"""
            )
        )

        assertEquals(listOf("x", "y"), items.map { it.id })
        assertTrue(items.all { it.target == null })
        assertEquals("message", items[0].kind)
    }

    @Test
    fun withoutABackendThereAreNoNotifications() = runTest {
        assertEquals(emptyList<AppNotification>(), UnavailableNotificationsService.notifications())
    }

    @Test
    fun seenNotificationsAreKeptOnThePhone() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs = mockk<SharedPreferences> {
            every { getStringSet("seen_notifications", emptySet()) } returns setOf("a")
            every { edit() } returns editor
        }
        every { editor.putStringSet(any(), any()) } returns editor
        val store = PrefsNotificationSeenStore(prefs)

        assertEquals(setOf("a"), store.seen())
        store.save(setOf("a", "b"))

        verify { editor.putStringSet("seen_notifications", setOf("a", "b")) }
        verify { editor.apply() }
    }
}
