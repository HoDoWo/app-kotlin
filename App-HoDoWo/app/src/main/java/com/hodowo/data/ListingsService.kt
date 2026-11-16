package com.hodowo.data

import com.hodowo.viewmodel.Listing
import com.hodowo.viewmodel.ListingStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Backend for "My Listings" (issue #146): edit, mark as sold and delete. */
interface ListingsService {
    suspend fun myListings(): List<Listing>
    suspend fun updateListing(id: String, title: String, description: String, condition: String, priceLabel: String): Listing
    suspend fun markAsSold(id: String): Listing
    suspend fun deleteListing(id: String)
}

/**
 * Used when the app has no backend URL. It fails loudly instead of showing
 * sample listings: the screen then offers a retry instead of pretending the
 * user has posted appliances.
 */
class UnavailableListingsService(
    private val reason: String = "The marketplace backend is not configured."
) : ListingsService {

    override suspend fun myListings(): List<Listing> = throw IOException(reason)

    override suspend fun updateListing(
        id: String,
        title: String,
        description: String,
        condition: String,
        priceLabel: String
    ): Listing = throw IOException(reason)

    override suspend fun markAsSold(id: String): Listing = throw IOException(reason)

    override suspend fun deleteListing(id: String) = throw IOException(reason)
}

/**
 * [ListingsService] backed by the HoDoWo backend, following the marketplace API
 * contract (Sprint 2 plan, `/api/marketplace/listings`).
 */
class RemoteListingsService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String? = { null },
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ListingsService {

    override suspend fun myListings(): List<Listing> {
        val payload = jsonOf(get("$LISTINGS_PATH?mine=true"))
        val array = payload.optJSONArray("results") ?: return emptyList()
        val result = ArrayList<Listing>(array.length())
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { result.add(parseListing(it)) }
        }
        return result
    }

    override suspend fun updateListing(
        id: String,
        title: String,
        description: String,
        condition: String,
        priceLabel: String
    ): Listing {
        val body = JSONObject()
            .put("title", title)
            .put("description", description)
            .put("condition", condition)
            .put("price", priceLabel.removePrefix("$").toDoubleOrNull() ?: 0.0)
            .toString()
            .toRequestBody(JSON)

        return parseListing(jsonOf(patch("$LISTINGS_PATH/$id", body)))
    }

    override suspend fun markAsSold(id: String): Listing =
        parseListing(jsonOf(post("$LISTINGS_PATH/$id/sold")))

    override suspend fun deleteListing(id: String) {
        delete("$LISTINGS_PATH/$id")
    }

    private suspend fun get(path: String): String = withContext(ioDispatcher) {
        execute(requestBuilder(path).get().build())
    }

    private suspend fun post(path: String): String = withContext(ioDispatcher) {
        execute(requestBuilder(path).post("".toRequestBody(JSON)).build())
    }

    private suspend fun patch(path: String, body: RequestBody): String = withContext(ioDispatcher) {
        execute(requestBuilder(path).patch(body).build())
    }

    private suspend fun delete(path: String): String = withContext(ioDispatcher) {
        execute(requestBuilder(path).delete().build())
    }

    private suspend fun requestBuilder(path: String): Request.Builder {
        val token = tokenProvider()
        val builder = Request.Builder().url("${backendUrl.trimEnd('/')}$path")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
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
    } catch (e: org.json.JSONException) {
        throw IOException("Invalid server response", e)
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L

        internal const val LISTINGS_PATH = "/api/marketplace/listings"

        private val JSON = "application/json".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        internal fun parseListing(json: JSONObject): Listing {
            val images = json.optJSONArray("image_urls")
            val firstImage = images?.optString(0)?.takeIf { it.isNotBlank() }
                ?: json.optString("image_url").takeIf { it.isNotBlank() }
            // The backend stores keys such as "used-good" but also sends the
            // human label; fall back to the key for older responses.
            val conditionLabel = json.optString("condition_label").takeIf { it.isNotBlank() }

            return Listing(
                id = json.optString("id"),
                title = json.optString("title"),
                description = json.optString("description"),
                condition = conditionLabel ?: json.optString("condition"),
                priceLabel = "$" + String.format(java.util.Locale.US, "%.2f", json.optDouble("price", 0.0)),
                status = if (json.optString("status") == "sold") ListingStatus.SOLD else ListingStatus.ACTIVE,
                inquiryCount = json.optInt("inquiry_count", 0),
                postedAgo = json.optString("posted_ago"),
                imageUrl = firstImage
            )
        }
    }
}
