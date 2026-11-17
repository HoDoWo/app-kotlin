package com.hodowo.data

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

/** One review of a product; every review comes from a buyer ([verifiedPurchase]). */
data class ProductReview(
    val id: String,
    val authorName: String,
    val rating: Int,
    val text: String,
    val createdAt: Long?,
    val verifiedPurchase: Boolean,
    val mine: Boolean
)

/** The average, how many reviews and how many for each number of stars (5 to 1). */
data class ReviewsSummary(
    val average: Double = 0.0,
    val count: Int = 0,
    val stars: Map<Int, Int> = emptyMap()
)

/** The reviews of a product, newest first, and whether the user can write (or change) one. */
data class ReviewsPage(
    val summary: ReviewsSummary = ReviewsSummary(),
    val items: List<ProductReview> = emptyList(),
    val myReview: ProductReview? = null,
    val canReview: Boolean = false
)

/** A refusal of the server worth showing as it is ("only buyers of this product can review it"). */
class ReviewsException(message: String) : IOException(message)

interface ReviewsService {
    suspend fun reviews(productType: String, productId: String): ReviewsPage
    suspend fun saveReview(productType: String, productId: String, rating: Int, text: String): ProductReview
    suspend fun deleteReview(productType: String, productId: String)
}

/** [ReviewsService] backed by `/api/marketplace/products/{type}/{id}/reviews`. */
class RemoteReviewsService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReviewsService {

    override suspend fun reviews(productType: String, productId: String): ReviewsPage =
        parsePage(call("GET", productType, productId))

    override suspend fun saveReview(productType: String, productId: String, rating: Int, text: String): ProductReview {
        val body = JSONObject().put("rating", rating).put("text", text.trim())
        return parseReview(call("PUT", productType, productId, mine = true, body = body))
    }

    override suspend fun deleteReview(productType: String, productId: String) {
        call("DELETE", productType, productId, mine = true)
    }

    private suspend fun call(
        method: String,
        productType: String,
        productId: String,
        mine: Boolean = false,
        body: JSONObject? = null
    ): JSONObject {
        val url = "${backendUrl.trimEnd('/')}$PRODUCTS_PATH".toHttpUrl().newBuilder()
            .addPathSegment(productType)
            .addPathSegment(productId)
            .addPathSegment("reviews")
            .apply { if (mine) addPathSegment("mine") }
            .build()
        val builder = Request.Builder().url(url).method(method, body?.toString()?.toRequestBody(JSON))
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        val text = withContext(ioDispatcher) {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> responseBody
                    response.code == HTTP_UNAUTHORIZED -> throw IOException("Not authorized: please sign in again")
                    response.code == HTTP_TOO_MANY -> throw ReviewsException("Too many changes, wait a minute")
                    else -> throw ReviewsException(
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

        internal fun parseReview(json: JSONObject): ProductReview = ProductReview(
            id = json.optString("id"),
            authorName = json.optString("author_name").ifBlank { "HoDoWo user" },
            rating = json.optInt("rating", 0).coerceIn(0, MAX_STARS),
            text = if (json.isNull("text")) "" else json.optString("text"),
            createdAt = if (json.isNull("created_at") || !json.has("created_at")) null else json.optLong("created_at"),
            verifiedPurchase = json.optBoolean("verified_purchase", false),
            mine = json.optBoolean("mine", false)
        )

        internal fun parsePage(json: JSONObject): ReviewsPage {
            val summary = json.optJSONObject("summary")
            val stars = summary?.optJSONObject("stars")
            val items = json.optJSONArray("items")
            return ReviewsPage(
                summary = ReviewsSummary(
                    average = summary?.optDouble("average", 0.0)?.takeUnless { it.isNaN() } ?: 0.0,
                    count = summary?.optInt("count", 0) ?: 0,
                    stars = (MAX_STARS downTo 1).associateWith { stars?.optInt(it.toString(), 0) ?: 0 }
                ),
                items = (0 until (items?.length() ?: 0)).mapNotNull { index ->
                    items?.optJSONObject(index)?.let(::parseReview)
                },
                myReview = json.optJSONObject("my_review")?.let(::parseReview),
                canReview = json.optBoolean("can_review", false)
            )
        }

        const val MAX_STARS = 5
    }
}
