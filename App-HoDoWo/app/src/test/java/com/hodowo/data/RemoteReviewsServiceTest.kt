package com.hodowo.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
                """{"rating": 4.5, "review_count": 2, "reviews": [
                    {"id": "a", "author_name": "Anna", "rating": 5, "body": "Great", "verified_purchase": true, "created_at": 1},
                    {"id": "b", "author_name": "", "rating": 4, "body": "", "verified_purchase": false, "created_at": 0}
                ]}"""
            )
        )

        val result = service.loadReviews("pump 1")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/marketplace/products/pump%201/reviews", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))
        assertEquals(4.5, result.rating)
        assertEquals(2, result.reviewCount)
        assertEquals("Anna", result.reviews[0].authorName)
        assertTrue(result.reviews[0].verifiedPurchase)
        assertEquals("HoDoWo customer", result.reviews[1].authorName)
        assertEquals("", result.reviews[1].postedAgo)
    }

    @Test
    fun aProductWithoutReviewsHasNoRating() = runTest {
        server.enqueue(MockResponse().setBody("""{"reviews": [], "rating": null, "review_count": 0}"""))

        val result = service.loadReviews("p1")

        assertNull(result.rating)
        assertEquals(0, result.reviewCount)
        assertTrue(result.reviews.isEmpty())
    }

    @Test
    fun submittingSendsTheRatingAndTheText() = runTest {
        server.enqueue(MockResponse().setBody("""{"review": {}, "rating": 5.0, "review_count": 1}"""))

        service.submitReview("p1", 5, "Works well")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/marketplace/products/p1/reviews", request.path)
        val sent = JSONObject(request.body.readUtf8())
        assertEquals(5, sent.getInt("rating"))
        assertEquals("Works well", sent.getString("body"))
    }

    @Test
    fun aRefusalShowsTheBackendMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail": "product not found"}"""))

        try {
            service.submitReview("nope", 3, "")
            fail("expected a ReviewException")
        } catch (e: ReviewException) {
            assertEquals("product not found", e.message)
        }
    }

    @Test
    fun anExpiredSessionAsksToSignInAgain() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        try {
            service.loadReviews("p1")
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("sign in"))
        }
    }
}
