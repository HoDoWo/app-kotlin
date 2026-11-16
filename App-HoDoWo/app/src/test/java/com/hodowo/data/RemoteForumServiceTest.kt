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

/** Calls of [RemoteForumService] to the `/api/forum` endpoints (Backend#44). */
class RemoteForumServiceTest {

    private lateinit var server: MockWebServer
    private var token: String? = "token-abc"
    private lateinit var service: RemoteForumService

    private val replyJson = """{"reply_id": "r1", "body": "Yes, in the box.", "author_name": "Anna",
        "author_badge": "verified_buyer", "created_at": 1700000000000, "upvotes": 3,
        "voted_by_me": true, "is_mine": false}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = RemoteForumService(backendUrl = server.url("/").toString(), tokenProvider = { token })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun productThreadsAreParsedWithTheirBestReply() = runTest {
        server.enqueue(
            ok(
                """{"total": 1, "threads": [{"thread_id": "t1", "title": "Does it include the seal?",
                    "body": null, "author_name": "Davide", "author_badge": null, "created_at": 1,
                    "last_activity_at": 2, "upvotes": 5, "reply_count": 2, "voted_by_me": false,
                    "is_mine": true, "top_reply": $replyJson}]}"""
            )
        )

        val page = service.productThreads("part", "pump 1")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/forum/products/part/pump%201/threads?sort=popular", request.path)
        assertEquals("Bearer token-abc", request.getHeader("Authorization"))

        assertEquals(1, page.total)
        val thread = page.threads.single()
        assertEquals("t1", thread.id)
        assertNull(thread.body)
        assertEquals(2, thread.replyCount)
        assertTrue(thread.isMine)
        val top = thread.topReply!!
        assertEquals("verified_buyer", top.authorBadge)
        assertEquals(1700000000000L, top.createdAt)
        assertTrue(top.votedByMe)
    }

    @Test
    fun repliesAreRequestedWithTheCursor() = runTest {
        server.enqueue(ok("""{"replies": [$replyJson], "next_cursor": "r1"}"""))
        server.enqueue(ok("""{"replies": [], "next_cursor": null}"""))

        val first = service.replies("t1")
        val last = service.replies("t1", cursor = "r1", limit = 5)

        assertEquals("/api/forum/threads/t1/replies?limit=20", server.takeRequest().path)
        assertEquals("/api/forum/threads/t1/replies?limit=5&cursor=r1", server.takeRequest().path)
        assertEquals("r1", first.nextCursor)
        assertEquals("Anna", first.replies.single().authorName)
        assertNull(last.nextCursor)
    }

    @Test
    fun askingSendsTheProductAndNoAuthorFields() = runTest {
        token = null
        server.enqueue(ok("""{"thread_id": "t9", "title": "Does it fit?", "reply_count": 0}"""))

        val thread = service.askAboutProduct("used", "fridge-2", "Does it fit?", null)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/forum/threads", request.path)
        assertNull(request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("Does it fit?", body.getString("title"))
        assertTrue(body.isNull("body"))
        assertEquals("used", body.getString("product_type"))
        assertEquals("fridge-2", body.getString("product_id"))
        assertFalse(body.has("author_name"))
        assertEquals("t9", thread.id)
        assertEquals("HoDoWo user", thread.authorName)
    }

    @Test
    fun replyVoteAndReportUseTheRightPaths() = runTest {
        server.enqueue(ok(replyJson))
        server.enqueue(ok("""{"upvotes": 4, "voted_by_me": true}"""))
        server.enqueue(ok("""{"upvotes": 3, "voted_by_me": false}"""))
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"reported": true}"""))

        assertEquals("r1", service.reply("t1", "Yes").id)
        assertEquals(ForumVote(4, true), service.vote("t1", null, up = true))
        assertEquals(ForumVote(3, false), service.vote("t1", "r1", up = false))
        service.report("t1", "r1", ForumReportReason.UNSAFE)

        val reply = server.takeRequest()
        assertEquals("POST /api/forum/threads/t1/replies", "${reply.method} ${reply.path}")
        assertEquals("Yes", JSONObject(reply.body.readUtf8()).getString("body"))
        val up = server.takeRequest()
        assertEquals("PUT /api/forum/threads/t1/vote", "${up.method} ${up.path}")
        val down = server.takeRequest()
        assertEquals("DELETE /api/forum/threads/t1/replies/r1/vote", "${down.method} ${down.path}")
        val report = server.takeRequest()
        assertEquals("POST /api/forum/threads/t1/replies/r1/report", "${report.method} ${report.path}")
        assertEquals("unsafe", JSONObject(report.body.readUtf8()).getString("reason"))
    }

    private suspend fun errorOf(code: Int, body: String): IOException {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
        return try {
            service.productThreads("part", "p1")
            fail("expected an exception")
            throw AssertionError()
        } catch (e: IOException) {
            e
        }
    }

    @Test
    fun backendErrorsKeepTheirReason() = runTest {
        val refused = errorOf(409, """{"detail": "this listing is no longer active"}""")
        assertTrue(refused is ForumException)
        assertEquals("this listing is no longer active", refused.message)

        assertEquals("String should have at most 300 characters",
            errorOf(422, """{"detail": [{"msg": "String should have at most 300 characters"}]}""").message)
        assertEquals("You are posting too fast, wait a minute", errorOf(429, "Too many").message)
        assertEquals("The forum is unavailable (503)", errorOf(503, "").message)

        val expired = errorOf(401, "{}")
        assertFalse(expired is ForumException)
        assertEquals("Not authorized: please sign in again", expired.message)
    }

    @Test(expected = IOException::class)
    fun anInvalidResponseIsAnIOException() = runTest {
        server.enqueue(ok("<html>"))
        service.productThreads("part", "p1")
    }

    @Test
    fun detailOfIgnoresOtherShapes() {
        assertNull(RemoteForumService.detailOf("""{"detail": {"x": 1}}"""))
        assertNull(RemoteForumService.detailOf("""{"detail": "  "}"""))
        assertNull(RemoteForumService.detailOf("not json"))
    }

    @Test
    fun badgesAndTimesHaveReadableLabels() {
        assertEquals("Seller", forumBadgeLabel("seller"))
        assertEquals("Verified buyer", forumBadgeLabel("verified_buyer"))
        assertNull(forumBadgeLabel(null))
        assertNull(forumBadgeLabel("admin"))

        val now = 10L * 7 * 24 * 60 * 60_000
        assertEquals("", formatTimeAgo(null, now))
        assertEquals("just now", formatTimeAgo(now - 30_000, now))
        assertEquals("just now", formatTimeAgo(now + 60_000, now)) // clock skew
        assertEquals("5m ago", formatTimeAgo(now - 5 * 60_000, now))
        assertEquals("3h ago", formatTimeAgo(now - 3 * 60 * 60_000, now))
        assertEquals("2d ago", formatTimeAgo(now - 2 * 24 * 60 * 60_000L, now))
        assertEquals("4w ago", formatTimeAgo(now - 4 * 7 * 24 * 60 * 60_000L, now))
    }
}