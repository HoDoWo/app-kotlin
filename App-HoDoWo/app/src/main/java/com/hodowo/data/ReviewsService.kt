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

/** The reviews of one product, newest first, with their average ([rating] is null without reviews). */
data class ProductReviews(val reviews: List<CustomerReview>, val rating: Double?, val reviewCount: Int)

/** The backend refused the review; the message can be shown as it is. */
class ReviewException(message: String) : IOException(message)

/** Customer reviews of official products (app #200, Backend `/api/marketplace/products/{id}/reviews`). */
interface ReviewsService {
    suspend fun loadReviews(productId: String): ProductReviews

    /** Creates the signed-in user's review, or edits it when they already wrote one. */
    suspend fun submitReview(productId: String, rating: Int, body: String)
}

/** [ReviewsService] backed by the marketplace products API. */
class RemoteReviewsService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReviewsService {

    override suspend fun loadReviews(productId: String): ProductReviews =
        parseReviews(call("GET", productId))

    override suspend fun submitReview(productId: String, rating: Int, body: String) {
        call("POST", productId, JSONObject().put("rating", rating).put("body", body))
    }

    private suspend fun call(method: String, productId: String, body: JSONObject? = null): JSONObject {
        val url = "${backendUrl.trimEnd('/')}$PRODUCTS_PATH".toHttpUrl().newBuilder()
            .addPathSegment(productId)
            .addPathSegment("reviews")
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
        private val JSON = "application/json".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()

        internal fun parseReviews(json: JSONObject): ProductReviews {
            val array = json.optJSONArray("reviews")
            val reviews = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it)?.let(::parseReview) }
            val rating = if (json.isNull("rating") || !json.has("rating")) null else json.optDouble("rating")
            return ProductReviews(reviews, rating, json.optInt("review_count", reviews.size))
        }

        internal fun parseReview(json: JSONObject, nowMs: Long = System.currentTimeMillis()) = CustomerReview(
            authorName = json.optString("author_name").ifBlank { "HoDoWo customer" },
            rating = json.optInt("rating", 0).coerceIn(1, 5),
            verifiedPurchase = json.optBoolean("verified_purchase", false),
            body = json.optString("body"),
            postedAgo = formatTimeAgo(json.optLong("created_at").takeIf { it > 0 }, nowMs)
        )
    }
}
