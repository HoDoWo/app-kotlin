package com.hodowo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

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
    val rating: Double = 4.8,
    val reviewCount: Int = 0,
    val distanceKm: Double? = null,
    val warrantyInfo: String? = null,
    val isPart: Boolean = true,
    val isRefurbished: Boolean = false,
    val handoverMethods: List<String> = emptyList()
)

class MarketplaceService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun search(
        query: String = "",
        type: String = "all",
        condition: String? = null,
        category: String? = null,
        brand: String? = null,
        maxPrice: Double? = null
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

        val remoteItems = runCatching {
            val response = request(path, null)
            val items = response.optJSONArray("items") ?: JSONArray()
            (0 until items.length()).map { parseProduct(items.getJSONObject(it)) }
        }.getOrElse { emptyList() }

        val catalog = getMockCatalog()
        val merged = (remoteItems + catalog).distinctBy { it.id }

        return filterProducts(merged, query, type, condition, category, brand, maxPrice)
    }

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
            .put("shipping_price_cents", shippingPriceCents ?: JSONObject.NULL)
            .put("quantity", 1)
        return parseProduct(request("/api/marketplace/listings", body.toString()))
    }

    private suspend fun request(path: String, json: String?): JSONObject = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        val builder = Request.Builder().url("${backendUrl.trimEnd('/')}$path")
        if (json == null) builder.get() else builder.post(json.toRequestBody(JSON))
        token?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Marketplace request failed (${response.code})")
            JSONObject(text)
        }
    }

    private fun parseProduct(item: JSONObject): MarketplaceProduct {
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
            rating = item.optDouble("rating", 4.8),
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

    private fun mockItem(
        id: String,
        title: String,
        desc: String,
        type: String = "part",
        condition: String = "new",
        price: Double,
        origPrice: Double? = null,
        discount: Int? = null,
        seller: String = "Community seller",
        partner: String? = null,
        sponsored: Boolean = false,
        image: String? = null,
        category: String? = "washing machine",
        brand: String? = null,
        model: String? = null,
        partNumber: String? = null,
        rating: Double = 4.8,
        reviews: Int = 0,
        distanceKm: Double? = null,
        warranty: String? = null,
        isPart: Boolean = true,
        isRefurbished: Boolean = false
    ) = MarketplaceProduct(
        id = id,
        title = title,
        description = desc,
        type = type,
        condition = condition,
        price = price,
        originalPrice = origPrice,
        discountPercent = discount,
        sellerName = seller,
        partnerName = partner,
        sponsored = sponsored,
        imageUrl = image,
        category = category,
        brand = brand,
        model = model,
        partNumber = partNumber,
        rating = rating,
        reviewCount = reviews,
        distanceKm = distanceKm,
        warrantyInfo = warranty,
        isPart = isPart,
        isRefurbished = isRefurbished
    )

    private fun getMockCatalog(): List<MarketplaceProduct> = listOf(
        mockItem(
            id = "hero-bosch-oem-pump",
            title = "Bosch Genuine OEM Drain Pump Motor WAN/WAE",
            desc = "Technical macro photography of a genuine Bosch Serie 4 washing machine drain pump motor",
            price = 38.50,
            seller = "Bosch Official Store",
            partner = "Bosch Official Store",
            sponsored = true,
            image = "https://lh3.googleusercontent.com/aida-public/AB6AXuAVSdamDabayAgAr75y9_5WrJXXpAEbljWm-D4UcksWO2SkfuBCrwDnseBFWB4-E5fOslVSy64plsUDNxOlhZYCkZy7OOtclyqj70xM_KcAfH17kDLern1fikDBqBEsBbzCUNvVXuvVJFwW41Y1nzVsSyWWi7oymhdqv39mgJFqewgl7Mrx35hsseGuk2VhqeiLNJmes-0VeSqQS6h501TyqQRfKjpgarwRlpyctXo",
            brand = "Bosch",
            model = "Serie 4 WAN/WAE",
            partNumber = "WAN/WAE OEM",
            rating = 4.9,
            reviews = 1240,
            warranty = "Factory Guarantee"
        ),
        mockItem(
            id = "official-bosch-flap-seal",
            title = "Bosch Universal Drain Flap & Seal Assembly",
            desc = "Studio catalog product photo of a high-durability black rubber drain flap",
            price = 14.99,
            seller = "Official Partner",
            partner = "Official Partner",
            image = "https://lh3.googleusercontent.com/aida-public/AB6AXuB69D8uZdiih_iVSm1D9541xxSGfukgSozY4RPyPijF3jtZ4buBfYDwm5j08eCt6wsESCSrkMKy2HlxMV-4umkcAiyTD4WqVbI3IkiQdpN3nScITY1ymPSLshdTVfdwPfAuDg4SnxvJHLb55cdI64HErAQnjRKY_1gxOm75kF6uN8wrJTwafsUu7jWvbgQSUd2DxzNL-7rf5W8Mhlyg9r475Imv_kPfAtGZePnHhfg",
            brand = "Bosch",
            reviews = 430
        ),
        mockItem(
            id = "community-marco-pump",
            title = "OEM Drain Pump Motor (Pulled from Bosch Serie 4)",
            desc = "Real-world natural photograph of a clean, tested second-hand washing machine drain pump motor",
            condition = "used-good",
            price = 18.00,
            origPrice = 38.50,
            discount = 53,
            seller = "Marco V.",
            image = "https://lh3.googleusercontent.com/aida-public/AB6AXuBc8GkpxgUMFD5W6pEp3PRVcKWDuFR6KmVb2Nn6Au9R1lMcq6zmZwl2iYZ760zWKZ4FcqxYdIWQ3wNZYIGmbV2-rNkmreM68AYmJ07TYvjmKER2uAcNwV0l6zbVCEHFa7WlLDuibYbMSB2jugBxPYMCE_kgYwJDOhAGO2umkSascynvHDf9Fh4UmGp64LtG1qctLJZRsUIpQZWUU0--9mCORuShoRCPtdLpronpY7k",
            brand = "Bosch",
            model = "Serie 4",
            rating = 5.0,
            reviews = 34,
            distanceKm = 1.8
        ),
        mockItem(
            id = "community-elena-filter",
            title = "Cleaned Drain Filter Chamber & Plug Cap",
            desc = "Refurbished translucent plastic washing machine drain pump filter chamber",
            condition = "refurbished",
            price = 12.00,
            seller = "Elena R.",
            image = "https://lh3.googleusercontent.com/aida-public/AB6AXuB7YHgf622GxfpKgVcATGSIzbcauLdCNaM7AdsASeg0lAfeFIQt2KAdsZtUI4_g9XOX0DqCG6XsgiA4k4I8llJnRFLJF2HU3qzeDUC0qY7IkCN7FM5qqEcTiH6Tikek62olHwe5O-ZXMKJhXikSHkihVymxCZro7d4GDje3K3uIc42Tpvk9zzvulO917YqlVfCuyELnW-2OfM2EyfLZ-4TXxQxNwxZIsX1BfyZ2SoY",
            brand = "Bosch",
            distanceKm = 4.1,
            isRefurbished = true
        ),
        mockItem(
            id = "partner-deal-bosch-serie6",
            title = "Bosch Serie 6 Front-Load Washer",
            desc = "Modern high-end stainless steel Bosch Serie 6 front load washing machine",
            type = "appliance",
            price = 749.0,
            origPrice = 899.0,
            seller = "Bosch Home OEM",
            partner = "Bosch Home OEM",
            image = "https://lh3.googleusercontent.com/aida-public/AB6AXuDUZRm2gGb1ZwdPDlqL2M1y-Ys7cjU1spwsV7gjd3WcqAe_irraOU30IYTkqZxbxgHKQDt4zzVjT4u5M8BUe5xXXUz3om9UtTkMspHk8XpCK4HuRvJ6cr0bFYj0fos9Sekph4L9JMab3stgWykWY9Sb4p79fo4qYqZaCHbbe29oS-iLIfoUM56GNb2cIsN6wZwO-qTDhiiSCtA6kl1jq-sU3cZD-GC9_HoqnJgZxsc",
            brand = "Bosch",
            model = "Serie 6",
            warranty = "2-Yr Bosch Warranty",
            isPart = false
        )
    )

    private fun String.urlEncode() = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
