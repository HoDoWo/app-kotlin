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

/** Calls of [RemoteMessagesService] to `/api/marketplace/conversations`. */
class RemoteMessagesServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteMessagesService

    private val conversationJson = """{"id": "fridge__b1", "role": "buying", "other_name": "Marc",
        "listing": {"id": "fridge", "title": "Old fridge", "image_url": "https://img.test/f.jpg",
                    "price_cents": 10000, "status": "active"},
        "last_message": {"text": "Offered €80.00", "kind": "offer", "mine": true, "created_at": 5},
        "unread": 0,
        "offer": {"offer_id": "of1", "amount_cents": 8000, "from": "me", "status": "accepted",
                  "created_at": 5, "expires_at": 9, "valid_until": 20, "can_answer": false, "can_pay": true},
        "can_offer": false, "updated_at": 6,
        "messages": [
            {"id": "m1", "kind": "text", "text": "Hi", "amount_cents": null, "mine": true, "from": "me", "created_at": 1},
            {"id": "m2", "kind": "offer_accepted", "text": "Offer of €80.00 accepted", "amount_cents": 8000,
             "mine": false, "from": "them", "created_at": 2}
        ]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteMessagesService(backendUrl = server.url("/").toString(), tokenProvider = { "token-abc" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun aConversationIsParsedWithItsOfferAndMessages() = runTest {
        server.enqueue(ok(conversationJson))

        val conversation = service.open("fridge__b1")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/marketplace/conversations/fridge__b1", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        assertTrue(conversation.buying)
        assertEquals("Marc", conversation.otherName)
        assertEquals(ConversationListing("fridge", "Old fridge", "https://img.test/f.jpg", 10000, "active"), conversation.listing)
        assertEquals(Offer("of1", 8000, true, "accepted", 9, 20, canAnswer = false, canPay = true), conversation.offer)
        assertEquals(listOf("Hi", "Offer of €80.00 accepted"), conversation.messages.map { it.text })
        assertEquals(8000, conversation.messages[1].amountCents)
        assertNull(conversation.messages[0].amountCents)
        assertFalse(conversation.messages[1].mine)
    }

    @Test
    fun theInboxHasTheConversationsAndTheUnreadTotal() = runTest {
        server.enqueue(ok("""{"results": [$conversationJson], "unread": 3}"""))

        val inbox = service.inbox()

        assertEquals("/api/marketplace/conversations", server.takeRequest().path)
        assertEquals(3, inbox.unread)
        assertEquals(listOf("fridge__b1"), inbox.conversations.map { it.id })
        assertEquals("Offered €80.00", inbox.conversations[0].lastMessage?.text)
    }

    @Test
    fun startSendOfferAndAnswerPostTheRightBodies() = runTest {
        repeat(4) { server.enqueue(ok(conversationJson)) }

        service.start("fridge", "Is it available?")
        service.send("fridge__b1", "Hello")
        service.offer("fridge__b1", 8000)
        service.answerOffer("fridge__b1", accept = false)

        val start = server.takeRequest()
        assertEquals("POST" to "/api/marketplace/conversations", start.method to start.path)
        assertEquals("Is it available?", JSONObject(start.body.readUtf8()).getString("text"))
        assertEquals("/api/marketplace/conversations/fridge__b1/messages", server.takeRequest().path)
        val offer = server.takeRequest()
        assertEquals("/api/marketplace/conversations/fridge__b1/offers", offer.path)
        assertEquals(8000, JSONObject(offer.body.readUtf8()).getInt("amount_cents"))
        assertEquals("/api/marketplace/conversations/fridge__b1/offer/decline", server.takeRequest().path)
    }

    @Test
    fun theBackendRefusalIsShownAsItIs() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail": "an offer must be at least €50.00"}"""))

        try {
            service.offer("fridge__b1", 100)
            fail("expected a refusal")
        } catch (e: MessagesException) {
            assertEquals("an offer must be at least €50.00", e.message)
        }
    }

    @Test
    fun amountsAreReadInEuros() {
        assertEquals(8500, parseEuroAmount("85"))
        assertEquals(8550, parseEuroAmount("85,5"))
        assertEquals(8550, parseEuroAmount(" €85.50 "))
        assertNull(parseEuroAmount("85.555"))
        assertNull(parseEuroAmount("abc"))
        assertNull(parseEuroAmount(""))
    }

    @Test
    fun aCartLineBoughtWithAnAcceptedOfferKeepsTheListPrice() {
        val cart = RemoteCartService.parseCart(
            """{"items": [{"cart_item_id": "c1", "product_type": "used", "product_id": "fridge", "title": "Old fridge",
                "quantity": 1, "unit_price_cents": 8000, "price_cents": 8000, "offer_id": "of1",
                "list_price_cents": 10000}], "item_count": 1, "total_cents": 8000}"""
        )

        val line = cart.items.single()
        assertEquals("of1", line.offerId)
        assertEquals(10000, line.listPriceCents)
        assertEquals(8000, line.unitPriceCents)
    }

    @Test
    fun anExpiredSessionAndTooManyMessagesHaveTheirOwnMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        val messages = (1..3).map {
            try {
                service.send("fridge__b1", "Hi")
                null
            } catch (e: java.io.IOException) {
                e.message
            }
        }

        assertEquals(
            listOf(
                "Not authorized: please sign in again",
                "You are sending too fast, wait a minute",
                "Messages are unavailable (500)"
            ),
            messages
        )
    }

    @Test
    fun anInvalidAnswerIsAnError() = runTest {
        server.enqueue(ok("not json"))

        try {
            service.inbox()
            fail("expected an error")
        } catch (e: java.io.IOException) {
            assertEquals("Invalid server response", e.message)
        }
    }

    @Test
    fun withoutATokenNoAuthorizationIsSentAndMissingFieldsGetDefaults() = runTest {
        val anonymous = RemoteMessagesService(backendUrl = server.url("/").toString(), tokenProvider = { null })
        server.enqueue(ok("""{"results": [{"id": "c1", "role": "selling", "listing": {}, "unread": 2}]}"""))

        val inbox = anonymous.inbox()

        assertNull(server.takeRequest().getHeader("Authorization"))
        val row = inbox.conversations.single()
        assertEquals(2, inbox.unread)
        assertFalse(row.buying)
        assertEquals("HoDoWo user", row.otherName)
        assertEquals("active", row.listing.status)
        assertNull(row.offer)
        assertNull(row.lastMessage)
        assertTrue(row.messages.isEmpty())
    }

    @Test
    fun withoutABackendThereAreNoMessages() = runTest {
        assertEquals(Inbox(emptyList(), 0), UnavailableMessagesService.inbox())
        val calls: List<suspend () -> Unit> = listOf(
            { UnavailableMessagesService.open("c") },
            { UnavailableMessagesService.start("l") },
            { UnavailableMessagesService.send("c", "t") },
            { UnavailableMessagesService.offer("c", 1) },
            { UnavailableMessagesService.answerOffer("c", true) }
        )
        calls.forEach { call ->
            try {
                call()
                fail("expected an error")
            } catch (e: java.io.IOException) {
                assertEquals("Messages are not available", e.message)
            }
        }
    }
}
