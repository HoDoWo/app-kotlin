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

    private fun getMockCatalog(): List<MarketplaceProduct> = listOf(
        MarketplaceProduct(
            id = "hero-bosch-oem-pump",
            title = "Bosch Genuine OEM Drain Pump Motor WAN/WAE",
            description = "Technical macro photography of a genuine Bosch Serie 4 washing machine drain pump motor with magnetic copper coils and white plastic impeller housing on an industrial technical blueprint grid background, sharp directional diagnostic lighting, crisp focus on serial imprint.",
            type = "part",
            condition = "new",
            price = 38.50,
            sellerName = "Bosch Official Store",
            partnerName = "Bosch Official Store",
            sponsored = true,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAVSdamDabayAgAr75y9_5WrJXXpAEbljWm-D4UcksWO2SkfuBCrwDnseBFWB4-E5fOslVSy64plsUDNxOlhZYCkZy7OOtclyqj70xM_KcAfH17kDLern1fikDBqBEsBbzCUNvVXuvVJFwW41Y1nzVsSyWWi7oymhdqv39mgJFqewgl7Mrx35hsseGuk2VhqeiLNJmes-0VeSqQS6h501TyqQRfKjpgarwRlpyctXo",
            category = "washing machine",
            brand = "Bosch",
            model = "Serie 4 WAN/WAE",
            partNumber = "WAN/WAE OEM",
            rating = 4.9,
            reviewCount = 1240,
            warrantyInfo = "Factory Guarantee",
            isPart = true
        ),
        MarketplaceProduct(
            id = "official-bosch-flap-seal",
            title = "Bosch Universal Drain Flap & Seal Assembly",
            description = "Studio catalog product photo of a high-durability black rubber drain flap and silicone seal ring gasket for high-efficiency washing machines, pristine white clean laboratory setting with soft diffuse lighting and sharp technical reflections.",
            type = "part",
            condition = "new",
            price = 14.99,
            sellerName = "Official Partner",
            partnerName = "Official Partner",
            sponsored = false,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB69D8uZdiih_iVSm1D9541xxSGfukgSozY4RPyPijF3jtZ4buBfYDwm5j08eCt6wsESCSrkMKy2HlxMV-4umkcAiyTD4WqVbI3IkiQdpN3nScITY1ymPSLshdTVfdwPfAuDg4SnxvJHLb55cdI64HErAQnjRKY_1gxOm75kF6uN8wrJTwafsUu7jWvbgQSUd2DxzNL-7rf5W8Mhlyg9r475Imv_kPfAtGZePnHhfg",
            category = "washing machine",
            brand = "Bosch",
            rating = 4.8,
            reviewCount = 430,
            isPart = true
        ),
        MarketplaceProduct(
            id = "community-marco-pump",
            title = "OEM Drain Pump Motor (Pulled from Bosch Serie 4)",
            description = "Real-world natural photograph of a clean, tested second-hand washing machine drain pump motor resting on an orderly wooden workbench next to a digital multimeter showing active test continuity, warm ambient morning workshop lighting.",
            type = "part",
            condition = "used-good",
            price = 18.00,
            originalPrice = 38.50,
            discountPercent = 53,
            sellerName = "Marco V.",
            partnerName = null,
            sponsored = false,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBc8GkpxgUMFD5W6pEp3PRVcKWDuFR6KmVb2Nn6Au9R1lMcq6zmZwl2iYZ760zWKZ4FcqxYdIWQ3wNZYIGmbV2-rNkmreM68AYmJ07TYvjmKER2uAcNwV0l6zbVCEHFa7WlLDuibYbMSB2jugBxPYMCE_kgYwJDOhAGO2umkSascynvHDf9Fh4UmGp64LtG1qctLJZRsUIpQZWUU0--9mCORuShoRCPtdLpronpY7k",
            category = "washing machine",
            brand = "Bosch",
            model = "Serie 4",
            rating = 5.0,
            reviewCount = 34,
            distanceKm = 1.8,
            isPart = true
        ),
        MarketplaceProduct(
            id = "community-elena-filter",
            title = "Cleaned Drain Filter Chamber & Plug Cap",
            description = "Refurbished translucent plastic washing machine drain pump filter chamber and twist-off sealing cap thoroughly sanitized and dry, photographed on a neutral textured slate stone tile with soft diffuse natural daylight.",
            type = "part",
            condition = "refurbished",
            price = 12.00,
            sellerName = "Elena R.",
            partnerName = null,
            sponsored = false,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB7YHgf622GxfpKgVcATGSIzbcauLdCNaM7AdsASeg0lAfeFIQt2KAdsZtUI4_g9XOX0DqCG6XsgiA4k4I8llJnRFLJF2HU3qzeDUC0qY7IkCN7FM5qqEcTiH6Tikek62olHwe5O-ZXMKJhXikSHkihVymxCZro7d4GDje3K3uIc42Tpvk9zzvulO917YqlVfCuyELnW-2OfM2EyfLZ-4TXxQxNwxZIsX1BfyZ2SoY",
            category = "washing machine",
            brand = "Bosch",
            rating = 4.8,
            distanceKm = 4.1,
            isPart = true,
            isRefurbished = true
        ),
        MarketplaceProduct(
            id = "featured-banner-bosch-wan",
            title = "Bosch WAN Serie 4 Drain Motor & Eco Accessories",
            description = "Close-up diagnostic photo of a genuine Bosch washing machine high-efficiency electric drain motor pump on a clean white testing surface with subtle blue engineering blueprint reflections",
            type = "part",
            condition = "new",
            price = 64.50,
            originalPrice = 89.00,
            discountPercent = 27,
            sellerName = "Bosch Official Store",
            partnerName = "Bosch Official Store",
            sponsored = true,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAAHMYQKJYNSiL8AxhO2_mFYZSeqhDG7lDk7OGCipDHhG55lIV10-bn9x8vABIGofZ6rYZQhheAko7ccMawBqnH-_m0YMqYjzTX74xbRgN8PZ86PS7J8m-I3us3jcCc26VNJQi1xw_TzLhvE8AByiQiP5BNu0Q2UXXOt51lIqguWDAP_dBfmFUIbbbEEgokrRgd0dXDB1pwoq4buRNmbihWNWaaTj1C5tCoRDL-LUM",
            category = "washing machine",
            brand = "Bosch",
            model = "WAN Serie 4",
            warrantyInfo = "Factory Guarantee",
            isPart = true
        ),
        MarketplaceProduct(
            id = "partner-deal-bosch-serie6",
            title = "Bosch Serie 6 Front-Load Washer",
            description = "Modern high-end stainless steel Bosch Serie 6 front load washing machine positioned inside a clean contemporary minimalist laundry room setup",
            type = "appliance",
            condition = "new",
            price = 749.0,
            originalPrice = 899.0,
            sellerName = "Bosch Home OEM",
            partnerName = "Bosch Home OEM",
            sponsored = false,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDUZRm2gGb1ZwdPDlqL2M1y-Ys7cjU1spwsV7gjd3WcqAe_irraOU30IYTkqZxbxgHKQDt4zzVjT4u5M8BUe5xXXUz3om9UtTkMspHk8XpCK4HuRvJ6cr0bFYj0fos9Sekph4L9JMab3stgWykWY9Sb4p79fo4qYqZaCHbbe29oS-iLIfoUM56GNb2cIsN6wZwO-qTDhiiSCtA6kl1jq-sU3cZD-GC9_HoqnJgZxsc",
            category = "washing machine",
            brand = "Bosch",
            model = "Serie 6",
            warrantyInfo = "2-Yr Bosch Warranty",
            isPart = false
        ),
        MarketplaceProduct(
            id = "partner-deal-delonghi-magnifica",
            title = "De'Longhi Magnifica S Espresso",
            description = "DeLonghi Magnifica S compact bean to cup espresso maker with glossy black accents and polished chrome steam wand on a modern kitchen counter",
            type = "appliance",
            condition = "new",
            price = 529.0,
            originalPrice = 619.0,
            sellerName = "De'Longhi Official",
            partnerName = "De'Longhi Official",
            sponsored = false,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCKOaLrK_l8N5xdh0m6wfP6blKbpTLhgYv-ejQlN4cWAzYwYbMI5RnwL4X61EKZBfYh-NeXXqQTBEL-_CoeYc6m6LjPuyLd9AykA74JtbNjuicz7dXwed8WypfpPxwfXORUbmoyvR_BYM6RUBAx8p9VNabjHaxoyZA2wTeUvi55Dc7WSBC01FcNO5guBPelIUgZRgXFaHCCXkfP8sxkWm6bv4wUBvJ1kr8WZdw2GrE",
            category = "espresso",
            brand = "De'Longhi",
            model = "Magnifica S",
            warrantyInfo = "3-Yr Partner Care",
            isPart = false
        ),
        MarketplaceProduct(
            id = "partner-deal-samsung-pump",
            title = "Inverter Drain Pump OEM #DC31",
            description = "Technical product shot of an authentic Samsung Digital Inverter Drain Pump assembly with clear part numbers, copper winding visible, and diagnostic serial sticker",
            type = "part",
            condition = "new",
            price = 42.0,
            originalPrice = 58.0,
            sellerName = "Samsung Parts Direct",
            partnerName = "Samsung Parts Direct",
            sponsored = false,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAC3D5ZahffUXOhqcNDJYYSHk7VxySNS3ui8Q_Q6-AzcpPyRW_MMTqb-M-p_LbPOL84Nml9WbLCC7xWLqrSFqE6BPn5dlTR6_OYeKQgbDww66ygHv14fje5sY6qMuHJVrEVYCag_o5cg5PIcOJBVxAxlP9-GGdqGaUZMxtaBMFedaE5f9npASAmXAIXwjVOG_8MVhVHS0fHP1kJeSC8v_-PgmyV7YojN7jm_UtUmA8",
            category = "washing machine",
            brand = "Samsung",
            model = "DC31",
            partNumber = "DC31-00181A",
            warrantyInfo = "OEM Direct Fit",
            isPart = true
        ),
        MarketplaceProduct(
            id = "community-kitchenaid-mixer",
            title = "KitchenAid Stand Mixer 5-Qt",
            description = "Fully working, dough hook & whisk included. Tested with Howdowo motor test.",
            type = "appliance",
            condition = "like-new",
            price = 160.0,
            sellerName = "David M.",
            rating = 4.9,
            distanceKm = 2.4,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCBipPCBoLJQN4p-ls0aIaHOylOoSszQdVLVpHXydVVEvRGaSIiKec8NE27BvAh1w4OvQomxe5Fm-QAyCLn3yzx9YyBrZWLxjBZWnpyVn9bbvug-3Cqf6iLwwRC0aQwSBeEm24trs9SSFyPmAtGx977x56Q0f1LuwYQA0onHR4MMpeGgwAg9_-BG7rGPxKM9StkJl78g1Io3oFRLimQfLU8lkyoNtLpwpbK2TYLCf4",
            category = "mixer",
            brand = "KitchenAid",
            model = "Artisan 5-Qt",
            isPart = false
        ),
        MarketplaceProduct(
            id = "community-dyson-v10",
            title = "Dyson V10 Cyclone Motor Base",
            description = "New battery cells installed, diagnostic log attached. Suction 100% verified.",
            type = "part",
            condition = "refurbished",
            price = 85.0,
            sellerName = "FixLab Pro",
            rating = 5.0,
            distanceKm = 4.1,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuARGCRGYtAuai85wLnfWIyWStJtn6KY7eWzVaLmqH-y61zBy73orRGvGszKJuhPrmSaF2z2MbBu08_69Plqr2rNKWnk3y6FgmoaCqh9vUAERb1louhROfs8oLYU3od8jdiT6JdXFrw-Ni9hHLuea3Snz2iogA8zsMbh7n4kVIFIvBzP7fGNGcX6R5YG97cWTd4SKBLuaub21gCaQFedOJkAXMBZP6XvUhHJX6PGfXQ",
            category = "vacuum",
            brand = "Dyson",
            model = "V10",
            isPart = true,
            isRefurbished = true
        ),
        MarketplaceProduct(
            id = "community-whirlpool-valve",
            title = "Whirlpool Double Solenoid Valve",
            description = "Unused spare from leftover DIY washer maintenance. Resistance checked at 1.2kΩ.",
            type = "part",
            condition = "used-good",
            price = 18.0,
            sellerName = "Elena R.",
            rating = 4.8,
            distanceKm = 1.2,
            imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAVNeOmh-zvSybShXYUs1GmLCr1kCvf45Chhy3NLP0PwK3wtDGVdHq6-Mk0wjPPxWzQS8u69z9g9vHth4xWHLn_q4OzARMemwLZf2NZpOefzz8cLh3FuDdtj0zEDet_rEWb-OixQrN9bWktiassX8AC6DRI6NvybNoerX53wTncXgj-HcJDU-goCDaml73J_4OyHMvFZdvADqztJYJzr17hZXVMXL93XWPNXWwRAxI",
            category = "washing machine",
            brand = "Whirlpool",
            isPart = true
        )
    )

    private fun String.urlEncode() = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
