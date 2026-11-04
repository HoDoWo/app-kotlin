package com.hodowo.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Orders created by `POST /api/marketplace/checkout` (Backend#43): one order per seller,
 * all paid with one Stripe PaymentIntent.
 */
data class CheckoutSession(
    val checkoutId: String,
    val orderIds: List<String>,
    val totalCents: Int,
    val currency: String,
    /** True when the server has no Stripe keys: the purchase is recorded without payment. */
    val simulated: Boolean,
    /** Secret of the PaymentIntent for the PaymentSheet; null for a simulated purchase. */
    val clientSecret: String?
)

/** The backend refused the checkout; the message can be shown as it is. */
class CheckoutException(message: String) : IOException(message)

interface CheckoutService {
    /** Turns the user's cart into orders and returns what the PaymentSheet needs. */
    suspend fun startCheckout(): CheckoutSession
}

/** [CheckoutService] backed by `POST /api/marketplace/checkout`. */
class RemoteCheckoutService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CheckoutService {

    override suspend fun startCheckout(): CheckoutSession {
        val builder = Request.Builder()
            .url("${backendUrl.trimEnd('/')}$CHECKOUT_PATH")
            // The amount is never sent: the backend computes it from the cart.
            .post("{}".toRequestBody(JSON))
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        val text = withContext(ioDispatcher) {
            httpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> body
                    response.code == HTTP_UNAUTHORIZED -> throw IOException("Not authorized: please sign in again")
                    // 400 empty cart, 409 items not buyable, 422 total too low, 502/503 payments down.
                    else -> throw CheckoutException(detailOf(body) ?: "The payment could not be started (${response.code})")
                }
            }
        }
        return parseSession(text)
    }

    companion object {
        internal const val CHECKOUT_PATH = "/api/marketplace/checkout"
        private const val HTTP_UNAUTHORIZED = 401
        private val JSON = "application/json".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()

        /**
         * FastAPI "detail": a text, or for unbuyable items
         * `{"message": "...", "items": [{"product_id": "...", "reason": "already sold"}]}`.
         */
        internal fun detailOf(body: String): String? {
            val detail = try {
                JSONObject(body).opt("detail")
            } catch (e: JSONException) {
                return null
            }
            return when (detail) {
                is String -> detail.takeIf { it.isNotBlank() }
                is JSONObject -> {
                    val message = detail.optString("message").ifBlank { "Some items cannot be bought" }
                    val items = detail.optJSONArray("items")
                    val reasons = (0 until (items?.length() ?: 0))
                        .mapNotNull { items?.optJSONObject(it)?.optString("reason")?.takeIf { reason -> reason.isNotBlank() } }
                        .distinct()
                    if (reasons.isEmpty()) message else "$message: ${reasons.joinToString(", ")}"
                }
                else -> null
            }
        }

        internal fun parseSession(text: String): CheckoutSession {
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw IOException("Invalid server response", e)
            }
            val ids = json.optJSONArray("order_ids")
            return CheckoutSession(
                checkoutId = json.optString("checkout_id"),
                orderIds = (0 until (ids?.length() ?: 0)).mapNotNull { ids?.optString(it)?.takeIf { id -> id.isNotBlank() } },
                totalCents = json.optInt("total_cents", 0),
                currency = json.optString("currency", "eur").ifBlank { "eur" },
                simulated = json.optBoolean("simulated", false),
                clientSecret = if (json.isNull("client_secret")) null else json.optString("client_secret").takeIf { it.isNotBlank() }
            )
        }
    }
}