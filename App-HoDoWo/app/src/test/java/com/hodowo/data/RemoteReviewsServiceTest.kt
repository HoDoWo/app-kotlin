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

/** Calls of [RemoteReviewsService] to `/api/marketplace/products/{id}/reviews` (app #200). */
class RemoteReviewsServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: RemoteReviewsService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteReviewsService(backendUrl = server.url("/").toString(), tokenProvider = { "token-abc" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun reviewsAreLoadedWithTheirAverage() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"summary": {"average": 4.5, "count": 2, "stars": {"5": 1, "4": 1}},
                    "can_review": true,
                    "my_review": {"id": "a", "rating": 5, "text": "Great"},
                    "items": [
                    {"id": "a", "author_name": "Anna", "rating": 5, "text": "Great", "verified_purchase": true,
                     "created_at": 1, "updated_at": 2, "mine": true},
                    {"id": "b", "author_name": "", "rating": 4, "text": "", "verified_purchase": true,
                     "created_at": null, "updated_at": null, "mine": false}
                ]}"""
            )
        )

        val result = service.loadReviews("new", "pump 1")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/marketplace/products/new/pump%201/reviews", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        assertEquals(4.5, result.rating)
        assertEquals(2, result.reviewCount)
        assertTrue(result.canReview)
        assertEquals(MyReview(5, "Great"), result.myReview)
        assertEquals("Anna", result.reviews[0].authorName)
        assertEquals("Great", result.reviews[0].body)
        assertTrue(result.reviews[0].verifiedPurchase)
        assertEquals("HoDoWo user", result.reviews[1].authorName)
        assertEquals("", result.reviews[1].postedAgo)
    }

    @Test
    fun aProductWithoutReviewsHasNoRating() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"summary": {"average": 0.0, "count": 0, "stars": {}}, "items": [], "my_review": null, "can_review": false}"""
            )
        )

        val result = service.loadReviews("new", "p1")

        assertNull(result.rating)
        assertNull(result.myReview)
        assertFalse(result.canReview)
        assertEquals(0, result.reviewCount)
        assertTrue(result.reviews.isEmpty())
    }

    @Test
    fun submittingPutsTheRatingAndTheTextOnMyReview() = runTest {
        server.enqueue(MockResponse().setBody("""{"id": "x", "rating": 5}"""))

        service.submitReview("new", "p1", 5, "Works well")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/marketplace/products/new/p1/reviews/mine", request.path)
        val sent = JSONObject(request.body.readUtf8())
        assertEquals(5, sent.getInt("rating"))
        assertEquals("Works well", sent.getString("text"))
    }

    @Test
    fun aRefusalShowsTheBackendMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail": "only buyers of this product can review it"}"""))

        try {
            service.submitReview("new", "nope", 3, "")
            fail("expected a ReviewException")
        } catch (e: ReviewException) {
            assertEquals("only buyers of this product can review it", e.message)
        }
    }

    @Test
    fun anExpiredSessionAsksToSignInAgain() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        try {
            service.loadReviews("new", "p1")
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("sign in"))
        }
    }
}
