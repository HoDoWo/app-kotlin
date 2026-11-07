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
    val unavailableReason: String?,
    /** Second line of the row: brand, model and part number, or the condition of a listing. */
    val subtitle: String = "",
    val sellerId: String? = null,
    val sellerName: String? = null,
    /** "partner" (official partner or HoDoWo part) or "community" (second-hand listing). */
    val sellerKind: String = if (productType == "used") SELLER_COMMUNITY else SELLER_PARTNER,
    /** Part of the purchase being prepared; unselected lines stay in the cart. */
    val selected: Boolean = available
)

/** Estimated delivery, ISO dates ("2026-11-11"). */
data class DeliveryWindow(val from: String, val to: String)

/** One way to receive a seller's items, priced by the backend. */
data class DeliveryOption(
    /** "standard", "express" or "pickup". */
    val method: String,
    val label: String,
    val priceCents: Int,
    val delivery: DeliveryWindow?
)

/** The items of one seller: they become one order, delivered with [method]. */
data class CartGroup(
    val sellerId: String,
    val sellerName: String,
    val sellerKind: String,
    val cartItemIds: List<String>,
    val selectedCount: Int,
    val options: List<DeliveryOption>,
    val method: String?,
    val delivery: DeliveryWindow?,
    /** Approximate pickup area ("080xx"); the exact place is in the order after paying. */
    val pickupArea: String?,
    val subtotalCents: Int,
    val shippingCents: Int,
    val totalCents: Int
)

/** Totals of the selection, as the checkout will charge them (VAT included). */
data class CartSummary(
    val selectedItemCount: Int = 0,
    val subtotalCents: Int = 0,
    val shippingCents: Int = 0,
    val totalCents: Int = 0,
    val vatIncludedCents: Int = 0,
    val canCheckout: Boolean = false
)

/** The whole cart. Totals always come from the backend, never from the app. */
data class Cart(
    val items: List<CartLine> = emptyList(),
    /** Number of available items, for the badge. */
    val itemCount: Int = 0,
    val totalCents: Int = 0,
    val currency: String = "eur",
    val hasUnavailableItems: Boolean = false,
    /** The cart split by seller, official partners first. */
    val groups: List<CartGroup> = emptyList(),
    val summary: CartSummary = CartSummary(
        selectedItemCount = itemCount, subtotalCents = totalCents, totalCents = totalCents,
        canCheckout = itemCount > 0
    ),
    val paymentMethods: List<String> = listOf(PAYMENT_CARD)
) {
    /** Payment is possible when the backend says the selection can be bought. */
    val canCheckout: Boolean get() = summary.canCheckout
}

/** What to buy now and how: the cart lines, the delivery of each seller and the payment method. */
data class CartSelection(
    /** Lines to buy now; null means all of them. */
    val cartItemIds: List<String>? = null,
    /** sellerId -> "standard" / "express" / "pickup". */
    val delivery: Map<String, String> = emptyMap(),
    val paymentMethod: String? = null
)

const val SELLER_PARTNER = "partner"
const val SELLER_COMMUNITY = "community"
const val PAYMENT_CARD = "card"
const val PAYMENT_GOOGLE_PAY = "google_pay"
const val PAYMENT_KLARNA = "klarna"

/** The backend refused a cart change; the message can be shown as it is ("already sold"). */
class CartException(message: String) : IOException(message)

/** Cart API of the backend. Every call returns the whole updated cart. */
interface CartService {
    suspend fun getCart(): Cart
    suspend fun addItem(productType: String, productId: String, quantity: Int = 1): Cart
    suspend fun setQuantity(cartItemId: String, quantity: Int): Cart
    suspend fun removeItem(cartItemId: String): Cart

    /** The cart for a selection and delivery choices, with the totals the checkout will charge. */
    suspend fun quote(selection: CartSelection): Cart
}

/** Used where no backend is configured: the cart stays empty and changes are refused. */
object UnavailableCartService : CartService {
    private const val MESSAGE = "The cart is not available right now"

    override suspend fun getCart(): Cart = Cart()
    override suspend fun addItem(productType: String, productId: String, quantity: Int): Cart = throw CartException(MESSAGE)
    override suspend fun setQuantity(cartItemId: String, quantity: Int): Cart = throw CartException(MESSAGE)
    override suspend fun removeItem(cartItemId: String): Cart = throw CartException(MESSAGE)
    override suspend fun quote(selection: CartSelection): Cart = Cart()
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

    override suspend fun quote(selection: CartSelection): Cart =
        send(request(QUOTE_PATH).post(selection.toJson().toString().toRequestBody(JSON)))

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
        internal const val QUOTE_PATH = "/api/marketplace/checkout/quote"
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
                    unavailableReason = item.textOrNull("unavailable_reason"),
                    subtitle = item.optString("subtitle"),
                    sellerId = item.textOrNull("seller_id"),
                    sellerName = item.textOrNull("seller_name"),
                    sellerKind = item.textOrNull("seller_kind")
                        ?: if (item.optString("product_type") == "used") SELLER_COMMUNITY else SELLER_PARTNER,
                    selected = if (item.has("selected")) item.optBoolean("selected") else item.optBoolean("available", true)
                )
            }
            val itemCount = json.optInt("item_count", 0)
            val totalCents = json.optInt("total_cents", 0)
            val summary = json.optJSONObject("summary")?.let(::parseSummary)
                ?: CartSummary(selectedItemCount = itemCount, subtotalCents = totalCents, totalCents = totalCents,
                    canCheckout = itemCount > 0 && !json.optBoolean("has_unavailable_items", false))
            return Cart(
                items = lines,
                itemCount = itemCount,
                totalCents = totalCents,
                currency = json.optString("currency", "eur").ifBlank { "eur" },
                hasUnavailableItems = json.optBoolean("has_unavailable_items", false),
                groups = json.optJSONArray("groups").objects().map(::parseGroup),
                summary = summary,
                paymentMethods = json.optJSONArray("payment_methods").strings().ifEmpty { listOf(PAYMENT_CARD) }
            )
        }

        private fun org.json.JSONArray?.objects(): List<JSONObject> =
            (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

        private fun org.json.JSONArray?.strings(): List<String> =
            (0 until (this?.length() ?: 0)).mapNotNull { this?.optString(it)?.takeIf { text -> text.isNotBlank() } }

        private fun parseWindow(json: JSONObject?): DeliveryWindow? {
            json ?: return null
            val from = json.textOrNull("from") ?: return null
            return DeliveryWindow(from, json.textOrNull("to") ?: from)
        }

        private fun parseGroup(json: JSONObject) = CartGroup(
            sellerId = json.optString("seller_id"),
            sellerName = json.optString("seller_name"),
            sellerKind = json.optString("seller_kind", SELLER_PARTNER),
            cartItemIds = json.optJSONArray("cart_item_ids").strings(),
            selectedCount = json.optInt("selected_count", 0),
            options = json.optJSONArray("options").objects().map { option ->
                DeliveryOption(
                    method = option.optString("method"),
                    label = option.optString("label"),
                    priceCents = option.optInt("price_cents", 0),
                    delivery = parseWindow(option.optJSONObject("delivery"))
                )
            },
            method = json.textOrNull("method"),
            delivery = parseWindow(json.optJSONObject("delivery")),
            pickupArea = json.textOrNull("pickup_area"),
            subtotalCents = json.optInt("subtotal_cents", 0),
            shippingCents = json.optInt("shipping_cents", 0),
            totalCents = json.optInt("total_cents", 0)
        )

        private fun parseSummary(json: JSONObject) = CartSummary(
            selectedItemCount = json.optInt("selected_item_count", 0),
            subtotalCents = json.optInt("subtotal_cents", 0),
            shippingCents = json.optInt("shipping_cents", 0),
            totalCents = json.optInt("total_cents", 0),
            vatIncludedCents = json.optInt("vat_included_cents", 0),
            canCheckout = json.optBoolean("can_checkout", false)
        )
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

    suspend fun quote(selection: CartSelection): Cart =
        service.quote(selection).also { _cart.value = it }
}

/** Request body of the quote and of the checkout. */
fun CartSelection.toJson(): JSONObject {
    val json = JSONObject()
    cartItemIds?.let { json.put("cart_item_ids", org.json.JSONArray(it)) }
    json.put("delivery", JSONObject(delivery))
    paymentMethod?.let { json.put("payment_method", it) }
    return json
}

/** The cart product type of a search result, or null when the backend cannot sell it. */
fun MarketplaceProduct.cartProductType(): String? = type.takeIf { it in CART_PRODUCT_TYPES }

private val CART_PRODUCT_TYPES = setOf("new", "used", "part")

/** "€24.90" for eur, "24.90 USD" for other currencies. */
fun formatPriceCents(cents: Int, currency: String): String {
    val amount = String.format(Locale.US, "%.2f", cents / 100.0)
    return if (currency.equals("eur", ignoreCase = true)) "€$amount" else "$amount ${currency.uppercase(Locale.US)}"
}
/** "Wed 11 Nov – Fri 13 Nov", or one day when both dates are the same; "" if the dates are invalid. */
fun formatDeliveryWindow(window: DeliveryWindow): String {
    val parser = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val printer = java.text.SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
    return try {
        val from = printer.format(parser.parse(window.from)!!)
        val to = printer.format(parser.parse(window.to)!!)
        if (from == to) from else "$from – $to"
    } catch (e: java.text.ParseException) {
        ""
    }
}