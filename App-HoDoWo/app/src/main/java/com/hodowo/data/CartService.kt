package com.hodowo.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One line of the cart, as computed by the backend (Backend#42). */
data class CartLine(
    val cartItemId: String,
    val productType: String,
    val productId: String,
    val title: String,
    val imageUrl: String?,
    val quantity: Int,
    /** Current price of one item; null when the item cannot be bought any more. */
    val unitPriceCents: Int?,
    /** unitPriceCents × quantity; null when the item cannot be bought any more. */
    val priceCents: Int?,
    /** Price of one item when it was added to the cart. */
    val addedPriceCents: Int?,
    val priceChanged: Boolean,
    val available: Boolean,
    /** "already sold", "not enough stock"…; null when [available]. */
    val unavailableReason: String?
)

/** The whole cart. Totals always come from the backend, never from the app. */
data class Cart(
    val items: List<CartLine> = emptyList(),
    /** Number of available items, for the badge. */
    val itemCount: Int = 0,
    val totalCents: Int = 0,
    val currency: String = "eur",
    val hasUnavailableItems: Boolean = false
) {
    /** Payment is possible only with at least one item and nothing unavailable. */
    val canCheckout: Boolean get() = items.isNotEmpty() && !hasUnavailableItems
}

/** The backend refused a cart change; the message can be shown as it is ("already sold"). */
class CartException(message: String) : IOException(message)

/** Cart API of the backend. Every call returns the whole updated cart. */
interface CartService {
    suspend fun getCart(): Cart
    suspend fun addItem(productType: String, productId: String, quantity: Int = 1): Cart
    suspend fun setQuantity(cartItemId: String, quantity: Int): Cart
    suspend fun removeItem(cartItemId: String): Cart
}

/** Used where no backend is configured: the cart stays empty and changes are refused. */
object UnavailableCartService : CartService {
    private const val MESSAGE = "The cart is not available right now"

    override suspend fun getCart(): Cart = Cart()
    override suspend fun addItem(productType: String, productId: String, quantity: Int): Cart = throw CartException(MESSAGE)
    override suspend fun setQuantity(cartItemId: String, quantity: Int): Cart = throw CartException(MESSAGE)
    override suspend fun removeItem(cartItemId: String): Cart = throw CartException(MESSAGE)
}

/** [CartService] backed by `/api/marketplace/cart` (Backend#42). */
class RemoteCartService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CartService {

    override suspend fun getCart(): Cart = send(request(CART_PATH).get())

    override suspend fun addItem(productType: String, productId: String, quantity: Int): Cart {
        val body = JSONObject()
            .put("product_type", productType)
            .put("product_id", productId)
            .put("quantity", quantity)
        return send(request(ITEMS_PATH).post(body.toString().toRequestBody(JSON)))
    }

    override suspend fun setQuantity(cartItemId: String, quantity: Int): Cart {
        val body = JSONObject().put("quantity", quantity)
        return send(request("$ITEMS_PATH/${cartItemId.encodePath()}").patch(body.toString().toRequestBody(JSON)))
    }

    override suspend fun removeItem(cartItemId: String): Cart =
        send(request("$ITEMS_PATH/${cartItemId.encodePath()}").delete())

    private fun request(path: String) = Request.Builder().url("${backendUrl.trimEnd('/')}$path")

    private suspend fun send(builder: Request.Builder): Cart {
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
                    // 404 / 409 / 422: the backend explains why (sold, out of stock, own listing...).
                    response.code in 400..499 -> throw CartException(detailOf(body) ?: "This cart change is not possible")
                    else -> throw IOException("Server error ${response.code}")
                }
            }
        }
        return parseCart(text)
    }

    companion object {
        internal const val CART_PATH = "/api/marketplace/cart"
        internal const val ITEMS_PATH = "/api/marketplace/cart/items"
        private const val HTTP_UNAUTHORIZED = 401
        private val JSON = "application/json".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()

        private fun String.encodePath(): String = URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

        /** FastAPI puts the reason in "detail"; validation errors use a list, which is not shown. */
        private fun detailOf(body: String): String? = try {
            (JSONObject(body).opt("detail") as? String)?.takeIf { it.isNotBlank() }
        } catch (e: JSONException) {
            null
        }

        private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

        private fun JSONObject.textOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        /** Parses the cart returned by every cart endpoint. */
        internal fun parseCart(text: String): Cart {
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw IOException("Invalid server response", e)
            }
            val array = json.optJSONArray("items")
            val lines = (0 until (array?.length() ?: 0)).mapNotNull { index ->
                val item = array?.optJSONObject(index) ?: return@mapNotNull null
                CartLine(
                    cartItemId = item.optString("cart_item_id"),
                    productType = item.optString("product_type"),
                    productId = item.optString("product_id"),
                    title = item.optString("title"),
                    imageUrl = item.textOrNull("image_url"),
                    quantity = item.optInt("quantity", 1),
                    unitPriceCents = item.intOrNull("unit_price_cents"),
                    priceCents = item.intOrNull("price_cents"),
                    addedPriceCents = item.intOrNull("added_price_cents"),
                    priceChanged = item.optBoolean("price_changed", false),
                    available = item.optBoolean("available", true),
                    unavailableReason = item.textOrNull("unavailable_reason")
                )
            }
            return Cart(
                items = lines,
                itemCount = json.optInt("item_count", 0),
                totalCents = json.optInt("total_cents", 0),
                currency = json.optString("currency", "eur").ifBlank { "eur" },
                hasUnavailableItems = json.optBoolean("has_unavailable_items", false)
            )
        }
    }
}

/**
 * The one cart of the app, shared by the badge, the marketplace screens and the cart screen.
 * Each change goes through the backend and the returned cart replaces the local copy.
 */
class CartStore(private val service: CartService) {
    private val _cart = MutableStateFlow(Cart())
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    suspend fun refresh(): Cart = service.getCart().also { _cart.value = it }

    suspend fun add(productType: String, productId: String, quantity: Int = 1): Cart =
        service.addItem(productType, productId, quantity).also { _cart.value = it }

    suspend fun setQuantity(cartItemId: String, quantity: Int): Cart =
        service.setQuantity(cartItemId, quantity).also { _cart.value = it }

    suspend fun remove(cartItemId: String): Cart =
        service.removeItem(cartItemId).also { _cart.value = it }
}

/** The cart product type of a search result, or null when the backend cannot sell it. */
fun MarketplaceProduct.cartProductType(): String? = type.takeIf { it in CART_PRODUCT_TYPES }

private val CART_PRODUCT_TYPES = setOf("new", "used", "part")

/** "€24.90" for eur, "24.90 USD" for other currencies. */
fun formatPriceCents(cents: Int, currency: String): String {
    val amount = String.format(Locale.US, "%.2f", cents / 100.0)
    return if (currency.equals("eur", ignoreCase = true)) "€$amount" else "$amount ${currency.uppercase(Locale.US)}"
}