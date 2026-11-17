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
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RemoteReviewsServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteReviewsService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteReviewsService(server.url("/").toString(), tokenProvider = { "token-abc" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun theReviewsOfAProductAreRead() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"summary": {"average": 4.5, "count": 2, "stars": {"5": 1, "4": 1, "3": 0, "2": 0, "1": 0}},
                    "items": [{"id": "r1", "author_name": "Anna", "rating": 5, "text": "Great",
                               "created_at": 10, "verified_purchase": true, "mine": true},
                              {"id": "r2", "author_name": "", "rating": 4, "text": null, "created_at": null}],
                    "my_review": {"id": "r1", "author_name": "Anna", "rating": 5, "text": "Great", "mine": true},
                    "can_review": true}"""
            )
        )

        val page = service.reviews("part", "HDW-P-001")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/marketplace/products/part/HDW-P-001/reviews", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        assertEquals(4.5, page.summary.average, 0.001)
        assertEquals(2, page.summary.count)
        assertEquals(mapOf(5 to 1, 4 to 1, 3 to 0, 2 to 0, 1 to 0), page.summary.stars)
        assertEquals(2, page.items.size)
        assertTrue(page.items[0].mine && page.items[0].verifiedPurchase)
        assertEquals("HoDoWo user", page.items[1].authorName)
        assertEquals("", page.items[1].text)
        assertNull(page.items[1].createdAt)
        assertEquals("r1", page.myReview?.id)
        assertTrue(page.canReview)
    }

    @Test
    fun aReviewIsSavedAsTheUsersOwn() = runTest {
        server.enqueue(MockResponse().setBody("""{"id": "r1", "author_name": "Anna", "rating": 4, "text": "Ok"}"""))

        val review = service.saveReview("used", "L 1", 4, "  Ok  ")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/marketplace/products/used/L%201/reviews/mine", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(4, body.getInt("rating"))
        assertEquals("Ok", body.getString("text"))
        assertEquals(4, review.rating)
    }

    @Test
    fun aReviewIsDeleted() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        service.deleteReview("part", "P1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/marketplace/products/part/P1/reviews/mine", request.path)
    }

    @Test
    fun theRefusalOfTheServerIsShown() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail": "only buyers of this product can review it"}"""))

        val error = runCatching { service.saveReview("part", "P1", 5, "") }.exceptionOrNull()

        assertTrue(error is ReviewsException)
        assertEquals("only buyers of this product can review it", error?.message)
    }

    @Test
    fun otherErrors() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("not json"))

        val messages = (1..4).map { runCatching { service.reviews("part", "P1") }.exceptionOrNull() }

        assertEquals("Not authorized: please sign in again", messages[0]?.message)
        assertEquals("Too many changes, wait a minute", messages[1]?.message)
        assertEquals("Reviews are unavailable (503)", messages[2]?.message)
        assertTrue(messages[3] is IOException)
        assertFalse(messages[3] is ReviewsException)
    }

    @Test
    fun anEmptyPageHasNoReviews() {
        val page = RemoteReviewsService.parsePage(JSONObject("{}"))

        assertEquals(0, page.summary.count)
        assertEquals(0.0, page.summary.average, 0.0)
        assertTrue(page.items.isEmpty())
        assertNull(page.myReview)
        assertFalse(page.canReview)
    }
}
