package com.hodowo.data

import com.hodowo.viewmodel.CustomerReview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the signed-in user wrote, to prefill the form when they edit it. */
data class MyReview(val rating: Int, val text: String)

/**
 * The reviews of one product, newest first, with their average ([rating] is null without reviews).
 * Only buyers write reviews: [canReview] comes from the backend.
 */
data class ProductReviews(
    val reviews: List<CustomerReview>,
    val rating: Double?,
    val reviewCount: Int,
    val canReview: Boolean = false,
    val myReview: MyReview? = null
)

/** The backend refused the review; the message can be shown as it is. */
class ReviewException(message: String) : IOException(message)

/** Customer reviews (app #200, Backend `/api/marketplace/products/{type}/{id}/reviews`). */
interface ReviewsService {
    suspend fun loadReviews(productType: String, productId: String): ProductReviews

    /** Creates the signed-in user's review, or edits it when they already wrote one (buyers only). */
    suspend fun submitReview(productType: String, productId: String, rating: Int, text: String)
}

/** [ReviewsService] backed by the marketplace products API. */
class RemoteReviewsService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReviewsService {

    override suspend fun loadReviews(productType: String, productId: String): ProductReviews =
        parseReviews(call("GET", productType, productId))

    override suspend fun submitReview(productType: String, productId: String, rating: Int, text: String) {
        call("PUT", productType, productId, JSONObject().put("rating", rating).put("text", text), mine = true)
    }

    private suspend fun call(
        method: String,
        productType: String,
        productId: String,
        body: JSONObject? = null,
        mine: Boolean = false
    ): JSONObject {
        val url = "${backendUrl.trimEnd('/')}$PRODUCTS_PATH".toHttpUrl().newBuilder()
            .addPathSegment(productType)
            .addPathSegment(productId)
            .addPathSegment("reviews")
            .apply { if (mine) addPathSegment("mine") }
            .build()
        val builder = Request.Builder().url(url).method(method, body?.toString()?.toRequestBody(JSON))
        val token = tokenProvider()
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")

        val text = withContext(ioDispatcher) {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> responseBody
                    response.code == HTTP_UNAUTHORIZED -> throw IOException("Not authorized: please sign in again")
                    response.code == HTTP_TOO_MANY -> throw ReviewException("You are posting too fast, wait a minute")
                    else -> throw ReviewException(
                        RemoteForumService.detailOf(responseBody) ?: "Reviews are unavailable (${response.code})"
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
        internal const val PRODUCTS_PATH = "/api/marketplace/products"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY = 429
        private val JSON = "application/json".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()

        internal fun parseReviews(json: JSONObject, nowMs: Long = System.currentTimeMillis()): ProductReviews {
            val array = json.optJSONArray("items")
            val reviews = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it)?.let { r -> parseReview(r, nowMs) } }
            val summary = json.optJSONObject("summary")
            val count = summary?.optInt("count", reviews.size) ?: reviews.size
            val mine = json.optJSONObject("my_review")
            return ProductReviews(
                reviews = reviews,
                rating = if (count > 0) summary?.optDouble("average") else null,
                reviewCount = count,
                canReview = json.optBoolean("can_review", false),
                myReview = mine?.let { MyReview(it.optInt("rating", 0), it.optString("text")) }
            )
        }

        internal fun parseReview(json: JSONObject, nowMs: Long) = CustomerReview(
            authorName = json.optString("author_name").ifBlank { "HoDoWo user" },
            rating = json.optInt("rating", 0).coerceIn(1, 5),
            verifiedPurchase = json.optBoolean("verified_purchase", false),
            body = json.optString("text"),
            postedAgo = formatTimeAgo(
                (json.optLong("updated_at").takeIf { it > 0 } ?: json.optLong("created_at")).takeIf { it > 0 },
                nowMs
            )
        )
    }
}
