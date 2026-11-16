package com.hodowo.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/** The listing a conversation is about. */
data class ConversationListing(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val priceCents: Int?,
    /** "active", "sold" or "removed". */
    val status: String
)

/**
 * The current offer of a conversation, as the backend sees it now.
 * [status] is "pending", "accepted", "declined", "expired" or "used".
 */
data class Offer(
    val offerId: String,
    val amountCents: Int,
    val fromMe: Boolean,
    val status: String,
    val expiresAt: Long?,
    val validUntil: Long?,
    /** The other person made it and it is still pending: accept or decline. */
    val canAnswer: Boolean,
    /** Accepted and the user is the buyer: the cart charges this price. */
    val canPay: Boolean
)

/** A message, an offer or an offer answer. [kind]: "text", "offer", "counter_offer", "offer_accepted", "offer_declined". */
data class ConversationMessage(
    val id: String,
    val kind: String,
    val text: String,
    val amountCents: Int?,
    val mine: Boolean,
    val createdAt: Long?
)

data class LastMessage(val text: String, val kind: String, val mine: Boolean, val createdAt: Long?)

/** A conversation with the seller (when [buying]) or with a buyer. Uids never reach the app. */
data class Conversation(
    val id: String,
    val buying: Boolean,
    val otherName: String,
    val listing: ConversationListing,
    val lastMessage: LastMessage?,
    val unread: Int,
    val offer: Offer?,
    val canOffer: Boolean,
    val updatedAt: Long?,
    /** Only when the conversation is opened, oldest first. */
    val messages: List<ConversationMessage> = emptyList()
)

data class Inbox(val conversations: List<Conversation>, val unread: Int)

/** The backend refused the action ("an offer must be at least €50.00"); shown as it is. */
class MessagesException(message: String) : IOException(message)

/** Messages and offers between buyers and sellers of second-hand listings. */
interface MessagesService {
    suspend fun inbox(): Inbox
    suspend fun open(conversationId: String): Conversation
    /** Opens (or reopens) the conversation with the seller of a listing. */
    suspend fun start(listingId: String, text: String? = null): Conversation
    suspend fun send(conversationId: String, text: String): Conversation
    suspend fun offer(conversationId: String, amountCents: Int): Conversation
    suspend fun answerOffer(conversationId: String, accept: Boolean): Conversation
}

/** Without a backend there are no messages. */
object UnavailableMessagesService : MessagesService {
    private fun unavailable(): Nothing = throw IOException("Messages are not available")
    override suspend fun inbox() = Inbox(emptyList(), 0)
    override suspend fun open(conversationId: String) = unavailable()
    override suspend fun start(listingId: String, text: String?) = unavailable()
    override suspend fun send(conversationId: String, text: String) = unavailable()
    override suspend fun offer(conversationId: String, amountCents: Int) = unavailable()
    override suspend fun answerOffer(conversationId: String, accept: Boolean) = unavailable()
}

/** [MessagesService] backed by `/api/marketplace/conversations`. */
class RemoteMessagesService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MessagesService {

    override suspend fun inbox(): Inbox = parseInbox(call("GET", emptyList()))

    override suspend fun open(conversationId: String): Conversation =
        parseConversation(call("GET", listOf(conversationId)))

    override suspend fun start(listingId: String, text: String?): Conversation {
        val body = JSONObject().put("listing_id", listingId)
        if (!text.isNullOrBlank()) body.put("text", text)
        return parseConversation(call("POST", emptyList(), body))
    }

    override suspend fun send(conversationId: String, text: String): Conversation =
        parseConversation(call("POST", listOf(conversationId, "messages"), JSONObject().put("text", text)))

    override suspend fun offer(conversationId: String, amountCents: Int): Conversation =
        parseConversation(call("POST", listOf(conversationId, "offers"), JSONObject().put("amount_cents", amountCents)))

    override suspend fun answerOffer(conversationId: String, accept: Boolean): Conversation =
        parseConversation(call("POST", listOf(conversationId, "offer", if (accept) "accept" else "decline"), JSONObject()))

    private suspend fun call(method: String, segments: List<String>, body: JSONObject? = null): JSONObject {
        val url = "${backendUrl.trimEnd('/')}$PATH".toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
        }.build()
        val builder = Request.Builder().url(url).method(method, body?.toString()?.toRequestBody(JSON))
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }

        val text = withContext(ioDispatcher) {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> responseBody
                    response.code == HTTP_UNAUTHORIZED -> throw IOException("Not authorized: please sign in again")
                    response.code == HTTP_TOO_MANY -> throw MessagesException("You are sending too fast, wait a minute")
                    else -> throw MessagesException(
                        RemoteForumService.detailOf(responseBody) ?: "Messages are unavailable (${response.code})"
                    )
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
        internal const val PATH = "/api/marketplace/conversations"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY = 429
        private val JSON = "application/json".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()

        internal fun parseInbox(json: JSONObject): Inbox {
            val rows = json.optJSONArray("results") ?: JSONArray()
            val conversations = (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.let(::parseConversation) }
            return Inbox(conversations, json.optInt("unread", conversations.sumOf { it.unread }))
        }

        internal fun parseConversation(json: JSONObject): Conversation {
            val listing = json.optJSONObject("listing") ?: JSONObject()
            val messages = json.optJSONArray("messages") ?: JSONArray()
            return Conversation(
                id = json.optString("id"),
                buying = json.optString("role") != "selling",
                otherName = json.textOrNull("other_name") ?: "HoDoWo user",
                listing = ConversationListing(
                    id = listing.optString("id"),
                    title = listing.optString("title"),
                    imageUrl = listing.textOrNull("image_url"),
                    priceCents = listing.intOrNull("price_cents"),
                    status = listing.textOrNull("status") ?: "active"
                ),
                lastMessage = json.optJSONObject("last_message")?.let {
                    LastMessage(it.optString("text"), it.optString("kind"), it.optBoolean("mine"), it.longOrNull("created_at"))
                },
                unread = json.optInt("unread", 0),
                offer = json.optJSONObject("offer")?.let(::parseOffer),
                canOffer = json.optBoolean("can_offer", false),
                updatedAt = json.longOrNull("updated_at"),
                messages = (0 until messages.length()).mapNotNull { index ->
                    messages.optJSONObject(index)?.let {
                        ConversationMessage(
                            id = it.optString("id"),
                            kind = it.textOrNull("kind") ?: "text",
                            text = it.optString("text"),
                            amountCents = it.intOrNull("amount_cents"),
                            mine = it.optBoolean("mine"),
                            createdAt = it.longOrNull("created_at")
                        )
                    }
                }
            )
        }

        private fun parseOffer(json: JSONObject) = Offer(
            offerId = json.optString("offer_id"),
            amountCents = json.optInt("amount_cents"),
            fromMe = json.optString("from") == "me",
            status = json.optString("status"),
            expiresAt = json.longOrNull("expires_at"),
            validUntil = json.longOrNull("valid_until"),
            canAnswer = json.optBoolean("can_answer"),
            canPay = json.optBoolean("can_pay")
        )

        private fun JSONObject.textOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)

        private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
    }
}

/** "85", "85,5", "85.50" or "€85" in cents; null when it is not an amount. */
fun parseEuroAmount(text: String): Int? {
    val clean = text.trim().removePrefix("€").trim().replace(',', '.')
    if (!Regex("""\d{1,6}(\.\d{1,2})?""").matches(clean)) return null
    return BigDecimal(clean).movePointRight(2).intValueExact()
}
