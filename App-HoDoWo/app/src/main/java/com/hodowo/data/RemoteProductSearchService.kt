package com.hodowo.data

import com.hodowo.BuildConfig
import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductPage
import com.hodowo.data.model.ProductType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * [ProductSearchService] backed by the official partner catalogue:
 * `GET /api/marketplace/products?q=&brand=&category=&partner=&cursor=&limit=`.
 *
 * The endpoint returns the partner catalogue (with `partner_tier` and `sponsored`).
 * Type/condition/price filters are not part of this endpoint, so they are applied
 * locally to the returned page.
 */
class RemoteProductSearchService(
    private val backendUrl: String = BuildConfig.BACKEND_URL,
    private val tokenProvider: suspend () -> String? = { null },
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProductSearchService {

    override fun search(
        filters: ProductFilters,
        cursor: String?,
        pageSize: Int
    ): Flow<ProductPage> = flow {
        val params = mutableListOf<String>()
        filters.query.takeIf { it.isNotBlank() }?.let { params.add("q=${it.urlEncode()}") }
        filters.brand?.takeIf { it.isNotBlank() }?.let { params.add("brand=${it.urlEncode()}") }
        filters.category?.takeIf { it.isNotBlank() }?.let { params.add("category=${it.urlEncode()}") }
        params.add("limit=$pageSize")
        cursor?.let { params.add("cursor=${it.urlEncode()}") }

        val path = "$PRODUCTS_PATH?${params.joinToString("&")}"
        val json = jsonOf(get(path))

        val array = json.optJSONArray("items")
            ?: json.optJSONArray("results")
            ?: json.optJSONArray("products")
            ?: JSONArray()

        val mapped = (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index)?.let(::parseProduct) }
            .distinctBy { it.id }

        val filtered = mapped.filter { product ->
            val matchesType = when (filters.type) {
                null -> true
                ProductType.REPLACEMENT_PART -> product.type == ProductType.REPLACEMENT_PART
                ProductType.WHOLE_APPLIANCE -> product.type == ProductType.WHOLE_APPLIANCE
            }
            val matchesCondition = filters.condition == null || product.condition == filters.condition
            val matchesMin = filters.minPrice == null || product.price >= filters.minPrice
            val matchesMax = filters.maxPrice == null || product.price <= filters.maxPrice
            matchesType && matchesCondition && matchesMin && matchesMax
        }.sortedWith(compareByDescending<Product> { it.sponsored }.thenByDescending { it.rating })

        val offset = cursor?.toIntOrNull() ?: 0
        val nextOffset = offset + pageSize
        val serverHasMore = json.optBoolean("has_more", false) ||
            json.optString("next_cursor").isNotBlank() ||
            array.length() >= pageSize

        emit(
            ProductPage(
                products = filtered,
                nextCursor = if (serverHasMore) nextOffset.toString() else null,
                hasMore = serverHasMore
            )
        )
    }

    override fun categories(): List<String> = CATEGORIES

    override fun brands(): List<String> = BRANDS

    private suspend fun get(path: String): String {
        val builder = Request.Builder().url("${backendUrl.trimEnd('/')}$path").get()
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }

        return withContext(ioDispatcher) {
            httpClient.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> text
                    response.code == HTTP_UNAUTHORIZED -> throw IOException("Not authorized: please sign in again")
                    else -> throw IOException("Server error ${response.code}")
                }
            }
        }
    }

    private fun jsonOf(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw IOException("Invalid server response", e)
    }

    private fun parseProduct(item: JSONObject): Product? {
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = item.optString("title").ifBlank { item.optString("name", "Marketplace item") }

        val rawPrice = when {
            item.has("price") && !item.isNull("price") -> item.optDouble("price", 0.0)
            item.has("price_cents") -> item.optLong("price_cents", 0) / 100.0
            else -> 0.0
        }

        val conditionRaw = item.optString("condition", "new")
        val condition = if (conditionRaw.contains("new", true) || conditionRaw.contains("unopened", true)) {
            ProductCondition.NEW
        } else {
            ProductCondition.SECOND_HAND
        }

        val kind = item.optString("listing_kind", item.optString("item_type", item.optString("type", "part")))
        val isPart = kind.equals("part", true) || kind.equals("replacement", true)

        val partnerName = item.optString("partner_name").takeIf { it.isNotBlank() }
        val sellerName = item.optString("seller_name").takeIf { it.isNotBlank() }
        val partnerTier = item.optString("partner_tier").takeIf { it.isNotBlank() }

        val imageUrl = item.optJSONArray("image_urls")?.optString(0)?.takeIf { it.isNotBlank() }
            ?: item.optString("image_url").takeIf { it.isNotBlank() }

        val currencyCode = item.optString("currency", "usd")
        val currency = if (currencyCode.equals("eur", true)) "€" else "$"

        return Product(
            id = id,
            title = title,
            partner = partnerName ?: sellerName ?: "Official Partner",
            partnerVerified = partnerName != null || partnerTier != null,
            price = rawPrice,
            currency = currency,
            type = if (isPart) ProductType.REPLACEMENT_PART else ProductType.WHOLE_APPLIANCE,
            condition = condition,
            imageUrl = imageUrl,
            rating = item.optDouble("rating", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            reviews = item.optInt("review_count", item.optInt("reviews", 0)),
            stock = item.optString("stock_status").takeIf { it.isNotBlank() }
                ?: item.optString("stock").takeIf { it.isNotBlank() },
            distanceKm = if (item.has("distance_km") && !item.isNull("distance_km")) item.optDouble("distance_km") else null,
            sponsored = item.optBoolean("sponsored", false),
            refurbished = conditionRaw.contains("refurbished", true),
            discountPercent = if (item.has("discount_percent") && !item.isNull("discount_percent")) item.optInt("discount_percent") else null
        )
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    companion object {
        internal const val PRODUCTS_PATH = "/api/marketplace/products"
        private const val HTTP_UNAUTHORIZED = 401
        private val CATEGORIES = listOf("Pumps", "Filters", "Hoses", "Seals", "Motors", "Electronics")
        private val BRANDS = listOf("Bosch", "Siemens", "Universal", "OEM", "Community")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()
    }
}
