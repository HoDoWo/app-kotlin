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
    val type: String,
    val condition: String,
    val price: Double,
    val sellerName: String,
    val partnerName: String?,
    val sponsored: Boolean,
    val imageUrl: String?
)

class MarketplaceService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun search(query: String, type: String): List<MarketplaceProduct> {
        val path = "/api/marketplace/search?q=${query.urlEncode()}&type=$type"
        val response = request(path, null)
        val items = response.optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { parseProduct(items.getJSONObject(it)) }
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

    private fun parseProduct(item: JSONObject) = MarketplaceProduct(
        id = item.optString("id"),
        title = item.optString("title").ifBlank { item.optString("name") },
        description = item.optString("description"),
        type = item.optString("type", "used"),
        condition = item.optString("condition", "used"),
        price = item.optDouble("price", 0.0),
        sellerName = item.optString("seller_name", "Community seller"),
        partnerName = item.optString("partner_name").takeIf { it.isNotBlank() },
        sponsored = item.optBoolean("sponsored"),
        imageUrl = item.optJSONArray("image_urls")?.optString(0)?.takeIf { it.isNotBlank() }
    )

    private fun String.urlEncode() = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
