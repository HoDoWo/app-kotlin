package com.hodowo.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A reply in the appliance forum (Backend#44). Uids never reach the app: only [isMine]. */
data class ForumReply(
    val id: String,
    val body: String,
    val authorName: String,
    /** "seller" or "verified_buyer", decided by the backend; null for everyone else. */
    val authorBadge: String?,
    val createdAt: Long?,
    val upvotes: Int,
    val votedByMe: Boolean,
    val isMine: Boolean
)

/** A forum thread; on a product page it is a question about that product. */
data class ForumThread(
    val id: String,
    val title: String,
    val body: String?,
    val authorName: String,
    val authorBadge: String?,
    val createdAt: Long?,
    val lastActivityAt: Long?,
    val upvotes: Int,
    val replyCount: Int,
    val votedByMe: Boolean,
    val isMine: Boolean,
    /** The most upvoted reply, shown under the question on the product page. */
    val topReply: ForumReply? = null
)

data class ProductThreads(val threads: List<ForumThread>, val total: Int)

/** One page of replies; [nextCursor] is null on the last page. */
data class ForumReplyPage(val replies: List<ForumReply>, val nextCursor: String?)

data class ForumVote(val upvotes: Int, val votedByMe: Boolean)

/** Reasons accepted by the backend for a report. */
enum class ForumReportReason(val apiValue: String, val label: String) {
    SPAM("spam", "Spam or advertising"),
    OFFENSIVE("offensive", "Offensive or abusive"),
    UNSAFE("unsafe", "Unsafe repair advice"),
    OFF_TOPIC("off_topic", "Off topic"),
    OTHER("other", "Something else")
}

/** The backend refused the action; the message can be shown as it is. */
class ForumException(message: String) : IOException(message)

interface ForumService {
    suspend fun productThreads(productType: String, productId: String, sort: String = "popular"): ProductThreads
    suspend fun replies(threadId: String, cursor: String? = null, limit: Int = PAGE_SIZE): ForumReplyPage
    suspend fun askAboutProduct(productType: String, productId: String, title: String, body: String?): ForumThread
    suspend fun reply(threadId: String, body: String): ForumReply
    suspend fun vote(threadId: String, replyId: String?, up: Boolean): ForumVote
    suspend fun report(threadId: String, replyId: String?, reason: ForumReportReason)

    companion object {
        const val PAGE_SIZE = 20
    }
}

/** [ForumService] backed by the `/api/forum` endpoints. */
class RemoteForumService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ForumService {

    override suspend fun productThreads(productType: String, productId: String, sort: String): ProductThreads {
        val json = call("GET", listOf("products", productType, productId, "threads"), query = mapOf("sort" to sort))
        val threads = json.optJSONArray("threads")
        val parsed = (0 until (threads?.length() ?: 0)).mapNotNull { threads?.optJSONObject(it)?.let(::parseThread) }
        return ProductThreads(parsed, json.optInt("total", parsed.size))
    }

    override suspend fun replies(threadId: String, cursor: String?, limit: Int): ForumReplyPage {
        val query = mutableMapOf("limit" to limit.toString())
        if (cursor != null) query["cursor"] = cursor
        val json = call("GET", listOf("threads", threadId, "replies"), query = query)
        val replies = json.optJSONArray("replies")
        return ForumReplyPage(
            replies = (0 until (replies?.length() ?: 0)).mapNotNull { replies?.optJSONObject(it)?.let(::parseReply) },
            nextCursor = json.textOrNull("next_cursor")
        )
    }

    override suspend fun askAboutProduct(productType: String, productId: String, title: String, body: String?): ForumThread {
        val request = JSONObject()
            .put("title", title)
            .put("body", body ?: JSONObject.NULL)
            .put("product_type", productType)
            .put("product_id", productId)
        return parseThread(call("POST", listOf("threads"), body = request))
    }

    override suspend fun reply(threadId: String, body: String): ForumReply =
        parseReply(call("POST", listOf("threads", threadId, "replies"), body = JSONObject().put("body", body)))

    override suspend fun vote(threadId: String, replyId: String?, up: Boolean): ForumVote {
        val json = call(if (up) "PUT" else "DELETE", postPath(threadId, replyId) + "vote")
        return ForumVote(json.optInt("upvotes", 0), json.optBoolean("voted_by_me", up))
    }

    override suspend fun report(threadId: String, replyId: String?, reason: ForumReportReason) {
        call("POST", postPath(threadId, replyId) + "report", body = JSONObject().put("reason", reason.apiValue))
    }

    private fun postPath(threadId: String, replyId: String?): List<String> =
        if (replyId == null) listOf("threads", threadId) else listOf("threads", threadId, "replies", replyId)

    private suspend fun call(
        method: String,
        /** Path segments after `/api/forum`, each one URL-encoded. */
        segments: List<String>,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null
    ): JSONObject {
        val url = "${backendUrl.trimEnd('/')}$FORUM_PATH".toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val requestBody: RequestBody? = when {
            body != null -> body.toString().toRequestBody(JSON)
            method == "PUT" || method == "POST" -> "{}".toRequestBody(JSON)
            else -> null
        }
        val builder = Request.Builder().url(url).method(method, requestBody)
        val token = tokenProvider()
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")

        val text = withContext(ioDispatcher) {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> responseBody
                    response.code == HTTP_UNAUTHORIZED -> throw IOException("Not authorized: please sign in again")
                    response.code == HTTP_TOO_MANY -> throw ForumException("You are posting too fast, wait a minute")
                    else -> throw ForumException(detailOf(responseBody) ?: "The forum is unavailable (${response.code})")
                }
            }
        }
        return try {
            JSONObject(text.ifBlank { "{}" })
        } catch (e: JSONException) {
            throw IOException("Invalid server response", e)
        }
    }

    companion object {
        internal const val FORUM_PATH = "/api/forum"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY = 429
        private val JSON = "application/json".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()

        /** FastAPI "detail": a text, or for validation errors a list whose first "msg" is used. */
        internal fun detailOf(body: String): String? {
            val detail = try {
                JSONObject(body).opt("detail")
            } catch (e: JSONException) {
                return null
            }
            return when (detail) {
                is String -> detail.takeIf { it.isNotBlank() }
                is org.json.JSONArray -> detail.optJSONObject(0)?.textOrNull("msg")
                else -> null
            }
        }

        internal fun parseReply(json: JSONObject) = ForumReply(
            id = json.optString("reply_id"),
            body = json.optString("body"),
            authorName = json.textOrNull("author_name") ?: DEFAULT_AUTHOR,
            authorBadge = json.textOrNull("author_badge"),
            createdAt = json.longOrNull("created_at"),
            upvotes = json.optInt("upvotes", 0),
            votedByMe = json.optBoolean("voted_by_me", false),
            isMine = json.optBoolean("is_mine", false)
        )

        internal fun parseThread(json: JSONObject) = ForumThread(
            id = json.optString("thread_id"),
            title = json.optString("title"),
            body = json.textOrNull("body"),
            authorName = json.textOrNull("author_name") ?: DEFAULT_AUTHOR,
            authorBadge = json.textOrNull("author_badge"),
            createdAt = json.longOrNull("created_at"),
            lastActivityAt = json.longOrNull("last_activity_at"),
            upvotes = json.optInt("upvotes", 0),
            replyCount = json.optInt("reply_count", 0),
            votedByMe = json.optBoolean("voted_by_me", false),
            isMine = json.optBoolean("is_mine", false),
            topReply = json.optJSONObject("top_reply")?.let(::parseReply)
        )

        private const val DEFAULT_AUTHOR = "HoDoWo user"

        private fun JSONObject.textOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun JSONObject.longOrNull(key: String): Long? =
            if (isNull(key) || !has(key)) null else optLong(key)
    }
}

/** Label of a badge decided by the backend. */
fun forumBadgeLabel(badge: String?): String? = when (badge) {
    "seller" -> "Seller"
    "verified_buyer" -> "Verified buyer"
    else -> null
}

/** "just now", "5m ago", "3h ago", "2d ago", "4w ago" from an epoch in milliseconds. */
fun formatTimeAgo(epochMs: Long?, nowMs: Long = System.currentTimeMillis()): String {
    if (epochMs == null) return ""
    val minutes = ((nowMs - epochMs).coerceAtLeast(0L)) / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> "${minutes / (60 * 24 * 7)}w ago"
    }
}