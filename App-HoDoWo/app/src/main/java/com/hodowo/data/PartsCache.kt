package com.hodowo.data

import android.content.Context
import com.hodowo.viewmodel.PartCompatibility
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The last parts search the user made (#148), kept to show something while offline. */
data class PartsSearch(val query: String, val parts: List<MarketplaceProduct>)

interface PartsCache {
    fun load(): PartsSearch?
    fun save(search: PartsSearch)
}

/** JSON form of [PartsSearch]; unreadable text gives null instead of crashing the screen. */
object PartsCacheJson {
    private const val MAX_PARTS = 50

    fun toJson(search: PartsSearch): String {
        val parts = JSONArray()
        search.parts.take(MAX_PARTS).forEach { part ->
            parts.put(
                JSONObject()
                    .put("id", part.id)
                    .put("title", part.title)
                    .put("description", part.description)
                    .put("price", part.price)
                    .put("image_url", part.imageUrl ?: JSONObject.NULL)
                    .put("category", part.category ?: JSONObject.NULL)
                    .put("brand", part.brand ?: JSONObject.NULL)
                    .put("model", part.model ?: JSONObject.NULL)
                    .put("part_number", part.partNumber ?: JSONObject.NULL)
                    .put("compatibility", part.compatibility.name)
            )
        }
        return JSONObject().put("query", search.query).put("parts", parts).toString()
    }

    fun parse(text: String?): PartsSearch? {
        if (text.isNullOrBlank()) return null
        return try {
            val json = JSONObject(text)
            val array = json.optJSONArray("parts") ?: return null
            val parts = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.textOrNull("id") ?: return@mapNotNull null
                MarketplaceProduct(
                    id = id,
                    title = item.optString("title"),
                    description = item.optString("description"),
                    type = "part",
                    condition = "new",
                    price = item.optDouble("price", 0.0),
                    imageUrl = item.textOrNull("image_url"),
                    category = item.textOrNull("category"),
                    brand = item.textOrNull("brand"),
                    model = item.textOrNull("model"),
                    partNumber = item.textOrNull("part_number"),
                    isPart = true,
                    compatibility = PartCompatibility.entries
                        .firstOrNull { it.name == item.optString("compatibility") } ?: PartCompatibility.UNKNOWN
                )
            }
            PartsSearch(query = json.optString("query"), parts = parts)
        } catch (e: JSONException) {
            null
        }
    }
}

class SharedPreferencesPartsCache(context: Context) : PartsCache {
    private val prefs = context.applicationContext.getSharedPreferences("parts_catalogue", Context.MODE_PRIVATE)

    override fun load(): PartsSearch? = PartsCacheJson.parse(prefs.getString(KEY, null))

    override fun save(search: PartsSearch) {
        prefs.edit().putString(KEY, PartsCacheJson.toJson(search)).apply()
    }

    private companion object {
        const val KEY = "last_search"
    }
}
