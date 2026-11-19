package com.hodowo.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What tapping a notification opens. */
sealed interface NotificationTarget {
    /** A conversation with a buyer or a seller (messages and offers). */
    data class Conversation(val id: String) : NotificationTarget

    /** The cart, where the new price is shown. */
    data object Cart : NotificationTarget

    /** The Product Detail page of a (sponsored) product. */
    data class Product(val product: MarketplaceProduct) : NotificationTarget
}

/**
 * One notification of the bell. [kind] is "message", "offer", "offer_accepted",
 * "offer_declined", "price_drop", "price_change" or "sponsored". [unread] is
 * false for the sponsored product; a new one still counts on the bell once.
 */
data class AppNotification(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val createdAt: Long?,
    val unread: Boolean,
    val target: NotificationTarget?
)

/** The notifications of the signed-in user, newest first. */
interface NotificationsService {
    suspend fun notifications(): List<AppNotification>
}

/** Without a backend there is nothing to notify. */
object UnavailableNotificationsService : NotificationsService {
    override suspend fun notifications(): List<AppNotification> = emptyList()
}

/** [NotificationsService] backed by `GET /api/notifications`. */
class RemoteNotificationsService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NotificationsService {

    override suspend fun notifications(): List<AppNotification> {
        // Signed out there is nobody to notify.
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val request = Request.Builder()
            .url("${backendUrl.trimEnd('/')}$PATH")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val text = withContext(ioDispatcher) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Notifications are unavailable (${response.code})")
                response.body?.string().orEmpty()
            }
        }
        return try {
            parse(JSONObject(text.ifBlank { "{}" }))
        } catch (e: JSONException) {
            throw IOException("Invalid server response", e)
        }
    }

    companion object {
        internal const val PATH = "/api/notifications"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()

        internal fun parse(json: JSONObject): List<AppNotification> {
            val items = json.optJSONArray("items") ?: JSONArray()
            return (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val id = item.textOrNull("id") ?: return@mapNotNull null
                AppNotification(
                    id = id,
                    kind = item.textOrNull("kind") ?: "message",
                    title = item.optString("title"),
                    body = item.optString("body"),
                    imageUrl = item.textOrNull("image_url"),
                    createdAt = item.longOrNull("created_at"),
                    unread = item.optBoolean("unread", false),
                    target = item.optJSONObject("target")?.let(::parseTarget)
                )
            }
        }

        private fun parseTarget(json: JSONObject): NotificationTarget? = when (json.optString("type")) {
            "conversation" -> json.textOrNull("id")?.let { NotificationTarget.Conversation(it) }
            "cart" -> NotificationTarget.Cart
            "product" -> json.optJSONObject("product")?.let { NotificationTarget.Product(parseProduct(it)) }
            else -> null
        }

        /** A product of a partner, with what the Product Detail page needs to open. */
        private fun parseProduct(json: JSONObject): MarketplaceProduct {
            val kind = json.textOrNull("listing_kind") ?: "appliance"
            return MarketplaceProduct(
                id = json.optString("id"),
                title = json.optString("title"),
                description = json.optString("description"),
                type = json.textOrNull("type") ?: "new",
                condition = "new",
                price = json.optLong("price_cents", 0) / CENTS,
                sellerName = json.textOrNull("partner_name") ?: "HoDoWo partner",
                partnerName = json.textOrNull("partner_name"),
                sponsored = json.optBoolean("sponsored", false),
                imageUrl = json.textOrNull("image_url"),
                category = json.textOrNull("category"),
                brand = json.textOrNull("brand"),
                model = json.textOrNull("model"),
                isPart = kind == "part"
            )
        }

        private const val CENTS = 100.0

        private fun JSONObject.longOrNull(key: String): Long? =
            if (isNull(key) || !has(key)) null else optLong(key)

        private fun JSONObject.textOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}

/** The ids of the notifications the user has already seen, so the badge only counts new ones. */
interface NotificationSeenStore {
    fun seen(): Set<String>
    fun save(ids: Set<String>)
}

/** Kept only while the app runs (tests, or no storage). */
class InMemoryNotificationSeenStore : NotificationSeenStore {
    private var ids: Set<String> = emptySet()
    override fun seen(): Set<String> = ids
    override fun save(ids: Set<String>) {
        this.ids = ids
    }
}

/** Kept on the phone, so a notification already seen does not come back after a restart. */
class PrefsNotificationSeenStore(private val prefs: SharedPreferences) : NotificationSeenStore {
    override fun seen(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    override fun save(ids: Set<String>) {
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    private companion object {
        const val KEY = "seen_notifications"
    }
}
