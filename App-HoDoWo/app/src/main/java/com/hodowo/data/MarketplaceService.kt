package com.hodowo.data

import com.hodowo.BuildConfig
import com.hodowo.viewmodel.CommunityQuestion
import com.hodowo.viewmodel.CustomerReview
import com.hodowo.viewmodel.PartCompatibility
import com.hodowo.viewmodel.ProductDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class MarketplaceProduct(
    val id: String,
    val title: String,
    val description: String,
    val type: String = "used",
    val condition: String = "used",
    val price: Double = 0.0,
    val originalPrice: Double? = null,
    val discountPercent: Int? = null,
    val sellerName: String = "Community seller",
    val partnerName: String? = null,
    val sponsored: Boolean = false,
    val imageUrl: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val partNumber: String? = null,
    /** Only real reviews: 0 and 0 when the product has none, and the screens show no stars. */
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val distanceKm: Double? = null,
    val warrantyInfo: String? = null,
    val isPart: Boolean = true,
    val isRefurbished: Boolean = false,
    val handoverMethods: List<String> = emptyList()
)

/** Backend for the marketplace Product Detail screen (issue #140) and Marketplace items. */
interface MarketplaceService {
    /** The product/listing/part shown at the given id, in its matching variant. */
    suspend fun getProductDetail(id: String): ProductDetail

    /** Ids of the items shown by the temporary Market tab picker (#138/#139 are not built yet). */
    suspend fun recommendedDemoIds(): List<String>

    suspend fun search(
        query: String = "",
        type: String = "all",
        condition: String? = null,
        category: String? = null,
        brand: String? = null,
        maxPrice: Double? = null
    ): List<MarketplaceProduct> = emptyList()

    suspend fun createListing(
        title: String,
        description: String,
        category: String,
        brand: String,
        model: String,
        partNumber: String,
        listingKind: String,
        condition: String,
        price: Double,
        imageUrls: List<String>,
        diagnosticMediaUrls: List<String>,
        handoverMethods: List<String>,
        pickupZip: String,
        shippingPriceCents: Int?
    ): MarketplaceProduct = throw UnsupportedOperationException("createListing not supported")

    companion object {
        operator fun invoke(
            backendUrl: String = BuildConfig.BACKEND_URL,
            tokenProvider: suspend () -> String? = { null },
            client: OkHttpClient = defaultClient(),
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ): RemoteMarketplaceService = RemoteMarketplaceService(
            backendUrl = backendUrl,
            tokenProvider = tokenProvider,
            httpClient = client,
            ioDispatcher = ioDispatcher
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Local sample data, used until the backend has the marketplace endpoints
 * (Backend#38, Backend#40) and the real search screen (#139) exists.
 */
class DemoMarketplaceService : MarketplaceService {

    override suspend fun recommendedDemoIds(): List<String> {
        delay(DEMO_DELAY_MS)
        return listOf(NEW_PRODUCT.id, SECOND_HAND_LISTING.id, PART_FITS.id)
    }

    override suspend fun getProductDetail(id: String): ProductDetail {
        delay(DEMO_DELAY_MS)
        return SAMPLES[id] ?: throw IOException("Unknown product id: $id")
    }

    companion object {
        private const val DEMO_DELAY_MS = 300L

        val NEW_PRODUCT = ProductDetail.New(
            id = "demo-new-1",
            title = "Bosch Genuine OEM Drain Pump Motor (WAN/WAE Series)",
            priceLabel = "$38.50",
            images = listOf(
                "https://lh3.googleusercontent.com/aida-public/AB6AXuCBov7-VbevbBbyDYfzgmP6hxcZRbqDN2OT1wGVusRIOqEEYvffqYKjo9uL87tZ-Pf7Q9mOm1aGfLtFCZnj-eVlAQsqDdc0VORxuo7y2H33R-iwJZiWvYqEerjmsfD6eyI6zVljTuqAYdLv8NvhGx8ezdWVRx9PVUoDiBvkaEY_fn1F2DXltps-We9K5Wb9H2bQPpkfY398q6Z3EbkuIoI95IHYA96OUgMakBLrSNw",
                "https://lh3.googleusercontent.com/aida-public/AB6AXuA2PRiUn1zjW5KahamBuKidyFxYNSJunvClw_7mq8byBMOctbXd0uQPd8vtI428xZAIzaviNaSmINuVsEfVT_MKs_kZQkBuyhNqoV0FLGiHXsAAR1L1drpdoXKpb7ceXHMr4Fm2vzAVhYNcrVs6viHI0re4EuzXYQLgR8O5_4iepXK52RIOop-N4DeFMNLz3JMns87DR-bO-JCycTr9-aQXcxSLL3WuF0pnGaxZ84I",
                "https://lh3.googleusercontent.com/aida-public/AB6AXuAGACLVViInypDYgMpAqvflFg5zPvYwZMesBOlRVDjCGiliijhZiFEZoZL-ww9h5SXQHBhu0NF82FReT34R8mbUrDDoyVxjDy7UEvcmqEFl65bNYrSx3ip3aq50Dh-6xdo1UF8ytUhT0sj9p0CCtUk3XGntP1kkIFWD167BYnJP4sgWMl2FlmbznIzos-Fz2XENzZVQ7jh09d1xIIHyAz6HAh4sopXeVikcds4nEzk"
            ),
            description = null,
            partnerName = "Partner Certified",
            rating = 4.9,
            reviewCount = 1248,
            specs = listOf(
                "OEM PART NUMBER" to "00145787 (B/S/H)",
                "MOTOR CASING" to "PA66 Reinforced GF",
                "WARRANTY" to "24-Month Partner Direct",
                "CERTIFICATION" to "VDE / CE Certified"
            ),
            matchedAppliance = "Bosch Serie 4 Washer (WAN28209GB)",
            questions = listOf(
                CommunityQuestion(
                    question = "Does this include the rubber O-ring seal?",
                    answererName = "Dave M.",
                    answererBadge = "Bosch Tech & Community Expert",
                    answer = "Yes, comes with pre-lubricated silicone O-ring seal included in the internal " +
                            "sleeve packaging. No extra gasket purchase necessary!",
                    upvotes = 18,
                    postedAgo = "2d ago"
                ),
                CommunityQuestion(
                    question = "Is this difficult to swap yourself?",
                    answererName = "Klaus H.",
                    answererBadge = "Verified DIY Fixer",
                    answer = "Takes only 15 mins via the bottom access flap! Tip: put a small towel underneath " +
                            "before rotating the quarter-turn housing.",
                    upvotes = 9,
                    postedAgo = "1w ago"
                )
            ),
            reviews = listOf(
                CustomerReview(
                    authorName = "Sarah Jenkins",
                    rating = 5,
                    verifiedPurchase = true,
                    body = "Saved me a $180 service call fee. Unclipped the old noisy humming pump, locked " +
                            "this one in with a solid twist, reattached the hoses, and our washer is completely " +
                            "silent again. Extremely fast delivery too!",
                    postedAgo = "3d ago"
                )
            )
        )

        val SECOND_HAND_LISTING = ProductDetail.SecondHand(
            id = "demo-used-1",
            title = "Philips Airfryer XL (Like New)",
            priceLabel = "$45.00",
            images = listOf(
                "https://lh3.googleusercontent.com/aida-public/AB6AXuA2PRiUn1zjW5KahamBuKidyFxYNSJunvClw_7mq8byBMOctbXd0uQPd8vtI428xZAIzaviNaSmINuVsEfVT_MKs_kZQkBuyhNqoV0FLGiHXsAAR1L1drpdoXKpb7ceXHMr4Fm2vzAVhYNcrVs6viHI0re4EuzXYQLgR8O5_4iepXK52RIOop-N4DeFMNLz3JMns87DR-bO-JCycTr9-aQXcxSLL3WuF0pnGaxZ84I"
            ),
            description = "Original basket and manual included. Light scuffs on the base, works perfectly.",
            condition = "Like new",
            location = "Lleida, Spain",
            sellerName = "Marc O.",
            sellerRating = 4.7,
            postedAgo = "4d ago"
        )

        val PART_FITS = ProductDetail.Part(
            id = "demo-part-fits",
            title = "Universal Washing Machine Door Seal Gasket",
            priceLabel = "$22.90",
            images = listOf(
                "https://lh3.googleusercontent.com/aida-public/AB6AXuAGACLVViInypDYgMpAqvflFg5zPvYwZMesBOlRVDjCGiliijhZiFEZoZL-ww9h5SXQHBhu0NF82FReT34R8mbUrDDoyVxjDy7UEvcmqEFl65bNYrSx3ip3aq50Dh-6xdo1UF8ytUhT0sj9p0CCtUk3XGntP1kkIFWD167BYnJP4sgWMl2FlmbznIzos-Fz2XENzZVQ7jh09d1xIIHyAz6HAh4sopXeVikcds4nEzk"
            ),
            description = null,
            sku = "SKU-DS-4471",
            compatibility = PartCompatibility.FITS,
            compatibilityEvidence = "Matches model WAN28209GB",
            specs = listOf(
                "MATERIAL" to "EPDM Rubber",
                "WARRANTY" to "12-Month Partner Direct"
            )
        )

        val PART_DOES_NOT_FIT = PART_FITS.copy(
            id = "demo-part-mismatch",
            title = "OEM Door Seal Gasket (Serie 6 only)",
            compatibility = PartCompatibility.DOES_NOT_FIT,
            compatibilityEvidence = "Registered appliance is a Serie 4 (WAN28209GB); this part fits Serie 6 only"
        )

        val PART_UNKNOWN = PART_FITS.copy(
            id = "demo-part-unknown",
            title = "Generic Door Seal Gasket (brand unconfirmed)",
            compatibility = PartCompatibility.UNKNOWN,
            compatibilityEvidence = null
        )

        private val SAMPLES: Map<String, ProductDetail> = listOf(
            NEW_PRODUCT, SECOND_HAND_LISTING, PART_FITS, PART_DOES_NOT_FIT, PART_UNKNOWN
        ).associateBy { it.id }
    }
}

/**
 * [MarketplaceService] backed by the HoDoWo backend.
 *
 * There is no unified product-detail endpoint yet (Backend#38 covers parts only,
 * Backend#40 covers search): a listing is fetched by trying the parts endpoint
 * first, then the listings endpoint, following the paths from the marketplace
 * API contract (Sprint 2 plan, `/api/marketplace/...`).
 */
class RemoteMarketplaceService(
    private val backendUrl: String = BuildConfig.BACKEND_URL,
    private val tokenProvider: suspend () -> String? = { null },
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MarketplaceService {

    override suspend fun recommendedDemoIds(): List<String> =
        parseIdList(jsonOf(get(SEARCH_PATH)))

    override suspend fun getProductDetail(id: String): ProductDetail {
        val partResponse = runCatching { get("$PARTS_PATH/$id") }
        partResponse.getOrNull()?.let { return parseProduct(jsonOf(it)) }

        return parseProduct(jsonOf(get("$LISTINGS_PATH/$id")))
    }

    override suspend fun search(
        query: String,
        type: String,
        condition: String?,
        category: String?,
        brand: String?,
        maxPrice: Double?
    ): List<MarketplaceProduct> {
        val params = mutableListOf<String>()
        if (query.isNotBlank()) params.add("q=${query.urlEncode()}")
        if (type.isNotBlank()) params.add("type=${type.urlEncode()}")
        if (!condition.isNullOrBlank()) params.add("condition=${condition.urlEncode()}")
        if (!category.isNullOrBlank()) params.add("category=${category.urlEncode()}")
        if (!brand.isNullOrBlank()) params.add("brand=${brand.urlEncode()}")
        if (maxPrice != null && maxPrice > 0) params.add("max_price=$maxPrice")
        val queryStr = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        val path = "/api/marketplace/search$queryStr"

        // Only real catalogue items: the cart and the checkout can buy them (Backend#42, #43).
        // Errors are not hidden behind sample data: the screen shows them.
        val response = jsonOf(get(path))
        val items = response.optJSONArray("items") ?: JSONArray()
        val products = (0 until items.length()).map { parseMarketplaceProduct(items.getJSONObject(it)) }

        return filterProducts(products.distinctBy { it.id }, query, type, condition, category, brand, maxPrice)
    }

    override suspend fun createListing(
        title: String,
        description: String,
        category: String,
        brand: String,
        model: String,
        partNumber: String,
        listingKind: String,
        condition: String,
        price: Double,
        imageUrls: List<String>,
        diagnosticMediaUrls: List<String>,
        handoverMethods: List<String>,
        pickupZip: String,
        shippingPriceCents: Int?
    ): MarketplaceProduct {
        val body = JSONObject()
            .put("title", title)
            .put("description", description)
            .put("category", category)
            .put("brand", brand.ifBlank { JSONObject.NULL })
            .put("model", model.ifBlank { JSONObject.NULL })
            .put("part_number", partNumber.ifBlank { JSONObject.NULL })
            .put("listing_kind", listingKind)
            .put("condition", condition)
            .put("price", price)
            .put("image_urls", JSONArray(imageUrls))
            .put("diagnostic_media_urls", JSONArray(diagnosticMediaUrls))
            .put("handover_methods", JSONArray(handoverMethods))
            .put("pickup_zip", pickupZip.ifBlank { JSONObject.NULL })
            .put("location", pickupZip.ifBlank { JSONObject.NULL })
            .put("shipping_price_cents", shippingPriceCents ?: JSONObject.NULL)
            .put("quantity", 1)
        return parseMarketplaceProduct(jsonOf(post("/api/marketplace/listings", body.toString())))
    }

    private suspend fun get(path: String): String {
        val token = tokenProvider()

        val builder = Request.Builder()
            .url("${backendUrl.trimEnd('/')}$path")
            .get()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return withContext(ioDispatcher) {
            execute(builder.build())
        }
    }

    private suspend fun post(path: String, json: String): String {
        val token = tokenProvider()

        val builder = Request.Builder()
            .url("${backendUrl.trimEnd('/')}$path")
            .post(json.toRequestBody(JSON))
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return withContext(ioDispatcher) {
            execute(builder.build())
        }
    }

    private fun execute(request: Request): String =
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (response.code == HTTP_UNAUTHORIZED) {
                throw IOException("Not authorized: please sign in again")
            }
            if (!response.isSuccessful) {
                throw IOException("Server error ${response.code}")
            }

            text
        }

    private fun jsonOf(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw IOException("Invalid server response", e)
    }

    private fun parseMarketplaceProduct(item: JSONObject): MarketplaceProduct {
        val rawPrice = if (item.has("price") && !item.isNull("price")) {
            item.optDouble("price", 0.0)
        } else if (item.has("price_cents")) {
            item.optLong("price_cents", 0) / 100.0
        } else 0.0

        val origPrice = if (item.has("original_price") && !item.isNull("original_price")) {
            item.optDouble("original_price")
        } else null

        val conditionVal = item.optString("condition", "used-good")
        val isPartVal = item.optString("listing_kind", item.optString("item_type", "part")) == "part"
        val isRefurbishedVal = conditionVal.contains("refurbished", ignoreCase = true)

        val imageUrl = item.optJSONArray("image_urls")?.optString(0)?.takeIf { it.isNotBlank() }
            ?: item.optString("image_url").takeIf { it.isNotBlank() }

        return MarketplaceProduct(
            id = item.optString("id"),
            title = item.optString("title").ifBlank { item.optString("name", "Marketplace item") },
            description = item.optString("description"),
            type = item.optString("type", if (isPartVal) "part" else "appliance"),
            condition = conditionVal,
            price = rawPrice,
            originalPrice = origPrice,
            discountPercent = if (item.has("discount_percent") && !item.isNull("discount_percent")) item.optInt("discount_percent") else null,
            sellerName = item.optString("seller_name", "Community seller"),
            partnerName = item.optString("partner_name").takeIf { it.isNotBlank() },
            sponsored = item.optBoolean("sponsored", false),
            imageUrl = imageUrl,
            category = item.optString("category").takeIf { it.isNotBlank() },
            brand = item.optString("brand").takeIf { it.isNotBlank() },
            model = item.optString("model").takeIf { it.isNotBlank() },
            partNumber = item.optString("part_number").takeIf { it.isNotBlank() },
            rating = item.optDouble("rating", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            reviewCount = item.optInt("review_count", 0),
            distanceKm = if (item.has("distance_km") && !item.isNull("distance_km")) item.optDouble("distance_km") else null,
            warrantyInfo = item.optString("warranty_info").takeIf { it.isNotBlank() },
            isPart = isPartVal,
            isRefurbished = isRefurbishedVal
        )
    }

    private fun filterProducts(
        list: List<MarketplaceProduct>,
        query: String,
        type: String,
        condition: String?,
        category: String?,
        brand: String?,
        maxPrice: Double?
    ): List<MarketplaceProduct> {
        val q = query.trim().lowercase()
        return list.filter { item ->
            val matchQuery = if (q.isBlank()) true else {
                item.title.lowercase().contains(q) ||
                        item.description.lowercase().contains(q) ||
                        (item.brand?.lowercase()?.contains(q) == true) ||
                        (item.model?.lowercase()?.contains(q) == true) ||
                        (item.partNumber?.lowercase()?.contains(q) == true)
            }
            val matchType = when (type.lowercase()) {
                "all" -> true
                "part", "replacement parts" -> item.isPart || item.type == "part"
                "appliance", "full appliances", "whole appliances" -> !item.isPart || item.type in listOf("appliance", "used")
                "new" -> item.condition in listOf("new", "unopened", "brand-new")
                "used" -> item.condition !in listOf("new", "unopened", "brand-new")
                else -> true
            }
            val matchCondition = when (condition?.lowercase()?.trim()) {
                null, "", "all" -> true
                "new", "brand new", "brand-new", "unopened" -> item.condition in listOf("new", "unopened", "brand-new")
                "used", "second-hand", "second hand", "refurbished", "pre-owned" -> item.condition !in listOf("new", "unopened", "brand-new")
                else -> true
            }
            val matchBrand = if (brand.isNullOrBlank()) true else {
                item.brand?.equals(brand, ignoreCase = true) == true ||
                        item.title.contains(brand, ignoreCase = true)
            }
            val matchPrice = if (maxPrice == null || maxPrice <= 0) true else {
                item.price <= maxPrice
            }
            matchQuery && matchType && matchCondition && matchBrand && matchPrice
        }
    }

    private fun String.urlEncode() = URLEncoder.encode(this, Charsets.UTF_8.name())

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L

        private val JSON = "application/json".toMediaType()

        internal const val PARTS_PATH = "/api/marketplace/parts"
        internal const val LISTINGS_PATH = "/api/marketplace/listings"
        internal const val SEARCH_PATH = "/api/marketplace/search"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Parses a part or listing payload into the matching [ProductDetail] variant. */
        internal fun parseProduct(json: JSONObject): ProductDetail {
            val id = json.optString("id")
            val title = json.optString("title").ifBlank { json.optString("name") }
            val priceLabel = formatPrice(json.optDouble("price", 0.0))
            val images = json.optJSONArray("images")?.toStringList() ?: emptyList()
            val description = json.textOrNull("description")

            val sku = json.textOrNull("sku")
            if (sku != null) {
                val compatibilityJson = json.optJSONObject("compatibility")
                return ProductDetail.Part(
                    id = id,
                    title = title,
                    priceLabel = priceLabel,
                    images = images,
                    description = description,
                    sku = sku,
                    compatibility = parseCompatibility(compatibilityJson?.optString("status")),
                    compatibilityEvidence = compatibilityJson?.textOrNull("evidence"),
                    specs = json.optJSONObject("specs")?.toPairList() ?: emptyList()
                )
            }

            val condition = json.textOrNull("condition")
            if (condition != null) {
                return ProductDetail.SecondHand(
                    id = id,
                    title = title,
                    priceLabel = priceLabel,
                    images = images,
                    description = description,
                    condition = condition,
                    location = json.optString("location"),
                    sellerName = json.optString("seller_name"),
                    sellerRating = json.optDouble("seller_rating").takeUnless { it.isNaN() },
                    postedAgo = json.optString("posted_ago")
                )
            }

            return ProductDetail.New(
                id = id,
                title = title,
                priceLabel = priceLabel,
                images = images,
                description = description,
                partnerName = json.optString("partner_name"),
                rating = json.optDouble("rating", 0.0),
                reviewCount = json.optInt("review_count", 0),
                specs = json.optJSONObject("specs")?.toPairList() ?: emptyList(),
                matchedAppliance = json.textOrNull("matched_appliance"),
                questions = emptyList(),
                reviews = emptyList()
            )
        }

        private fun parseCompatibility(status: String?): PartCompatibility = when (status) {
            "true", "compatible" -> PartCompatibility.FITS
            "false", "incompatible" -> PartCompatibility.DOES_NOT_FIT
            else -> PartCompatibility.UNKNOWN
        }

        private fun formatPrice(value: Double) = "$" + String.format(java.util.Locale.US, "%.2f", value)

        private fun parseIdList(json: JSONObject): List<String> {
            val array = json.optJSONArray("results") ?: return emptyList()
            val ids = ArrayList<String>(array.length())
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }?.let(ids::add)
            }
            return ids
        }

        private fun JSONArray.toStringList(): List<String> {
            val list = ArrayList<String>(length())
            for (index in 0 until length()) {
                optString(index)?.takeIf { it.isNotBlank() }?.let(list::add)
            }
            return list
        }

        private fun JSONObject.toPairList(): List<Pair<String, String>> {
            val list = ArrayList<Pair<String, String>>(length())
            val keys = keys()
            while (keys.hasNext()) {
                val key = keys.next()
                list.add(key to optString(key))
            }
            return list
        }
    }
}