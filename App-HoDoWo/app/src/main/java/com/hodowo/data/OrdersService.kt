package com.hodowo.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Where an order is: the Stripe webhook turns "pending" into "paid", "failed" or "cancelled". */
enum class OrderStatus(val label: String) {
    PENDING("Payment processing"),
    PAID("Paid"),
    FAILED("Payment failed"),
    CANCELLED("Cancelled"),
    UNKNOWN("Unknown");

    companion object {
        fun of(value: String?): OrderStatus = when (value?.lowercase()) {
            "pending" -> PENDING
            "paid" -> PAID
            "failed" -> FAILED
            "cancelled", "canceled" -> CANCELLED
            else -> UNKNOWN
        }
    }
}

/** One bought line of an order, as it was when it was paid. */
data class OrderItem(
    val productType: String,
    val productId: String,
    val title: String,
    val imageUrl: String?,
    val quantity: Int,
    val unitPriceCents: Int,
    val priceCents: Int
)

/**
 * One order of `GET /api/marketplace/orders` (Backend#43): one per seller of a checkout.
 * [simulated] is true when the server had no Stripe keys: nothing was charged.
 */
data class Order(
    val orderId: String,
    val checkoutId: String,
    val sellerName: String,
    val items: List<OrderItem>,
    val subtotalCents: Int,
    /** "standard", "express" or "pickup". */
    val shippingMethod: String?,
    val shippingCents: Int,
    /** Estimated delivery window (ISO dates); null for a pickup. */
    val deliveryFrom: String?,
    val deliveryTo: String?,
    val pickupZip: String?,
    /** "card", "google_pay" or "klarna". */
    val paymentMethod: String?,
    val totalCents: Int,
    val currency: String,
    val status: OrderStatus,
    val simulated: Boolean,
    val createdAt: Long,
    val paidAt: Long?
) {
    val isPickup: Boolean get() = shippingMethod == "pickup"
    val itemCount: Int get() = items.sumOf { it.quantity }
}

interface OrdersService {
    /** The user's orders, newest first. */
    suspend fun listOrders(): List<Order>

    /** One of the user's orders. */
    suspend fun getOrder(orderId: String): Order
}

/** [OrdersService] backed by `GET /api/marketplace/orders` and `/orders/{id}`. */
class RemoteOrdersService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : OrdersService {

    override suspend fun listOrders(): List<Order> {
        val json = parse(get(ORDERS_PATH))
        val orders = json.optJSONArray("orders") ?: JSONArray()
        return (0 until orders.length()).mapNotNull { orders.optJSONObject(it)?.let(::parseOrder) }
    }

    override suspend fun getOrder(orderId: String): Order =
        parseOrder(parse(get("$ORDERS_PATH/${URLEncoder.encode(orderId, Charsets.UTF_8.name())}")))

    private suspend fun get(path: String): String {
        val builder = Request.Builder().url("${backendUrl.trimEnd('/')}$path").get()
        val token = tokenProvider()
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return withContext(ioDispatcher) {
            httpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> body
                    response.code == HTTP_UNAUTHORIZED -> throw IOException("Please sign in to see your orders")
                    response.code == HTTP_NOT_FOUND -> throw IOException("Order not found")
                    else -> throw IOException("Could not load your orders (${response.code})")
                }
            }
        }
    }

    companion object {
        internal const val ORDERS_PATH = "/api/marketplace/orders"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()

        private fun parse(text: String): JSONObject = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IOException("Invalid server response", e)
        }

        internal fun parseOrder(json: JSONObject): Order {
            val items = json.optJSONArray("items") ?: JSONArray()
            val delivery = json.optJSONObject("delivery")
            return Order(
                orderId = json.optString("order_id"),
                checkoutId = json.optString("checkout_id"),
                sellerName = json.text("seller_name") ?: "Seller",
                items = (0 until items.length()).mapNotNull { items.optJSONObject(it)?.let(::parseItem) },
                subtotalCents = json.optInt("subtotal_cents", 0),
                shippingMethod = json.text("shipping_method"),
                shippingCents = json.optInt("shipping_cents", 0),
                deliveryFrom = delivery?.text("from"),
                deliveryTo = delivery?.text("to"),
                pickupZip = json.text("pickup_zip"),
                paymentMethod = json.text("payment_method"),
                totalCents = json.optInt("total_cents", 0),
                currency = json.text("currency") ?: "eur",
                status = OrderStatus.of(json.text("status")),
                simulated = json.optBoolean("simulated", false),
                createdAt = json.optLong("created_at", 0L),
                paidAt = if (json.isNull("paid_at")) null else json.optLong("paid_at").takeIf { it > 0 }
            )
        }

        private fun parseItem(json: JSONObject): OrderItem {
            val quantity = json.optInt("quantity", 1).coerceAtLeast(1)
            val price = json.optInt("price_cents", 0)
            return OrderItem(
                productType = json.text("product_type") ?: "new",
                productId = json.optString("product_id"),
                title = json.text("title") ?: "Item",
                imageUrl = json.text("image_url"),
                quantity = quantity,
                unitPriceCents = json.optInt("unit_price_cents", price / quantity),
                priceCents = price
            )
        }

        /** The text of [key], or null when missing, blank or JSON null. */
        private fun JSONObject.text(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
