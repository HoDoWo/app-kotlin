package com.hodowo.data

import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.RecentManual
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON form of the manuals, the same one the backend uses for `/api/manuals`:
 * `{"manuals": [{"id", "appliance_name", "issue_title", "overview", "steps",
 * "warnings", "image_url", "is_bookmarked", "created_at_ms", "search_count", "save_count"}]}`.
 *
 * It is used to read the backend answers and to keep the lists on the phone,
 * so the Home screen works offline.
 */
object ManualJson {

    /** Reads a `{"manuals": [...]}` payload. Manuals without a title are skipped. */
    fun parse(payload: JSONObject): List<RecentManual> {
        val array = payload.optJSONArray("manuals") ?: return emptyList()
        val manuals = ArrayList<RecentManual>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val guide = guideFromJson(item) ?: continue

            manuals.add(
                RecentManual(
                    guide = guide,
                    createdAtMillis = item.positiveLongOrNull("created_at_ms"),
                    isBookmarked = item.optBoolean("is_bookmarked", false),
                    searchCount = item.optInt("search_count", 0),
                    saveCount = item.optInt("save_count", 0)
                )
            )
        }

        return manuals
    }

    /** The opposite of [parse]. */
    fun toJson(manuals: List<RecentManual>): JSONObject {
        val array = JSONArray()
        manuals.forEach { manual ->
            array.put(
                guideToJson(manual.guide)
                    .put("created_at_ms", manual.createdAtMillis ?: JSONObject.NULL)
                    .put("is_bookmarked", manual.isBookmarked)
                    .put("search_count", manual.searchCount)
                    .put("save_count", manual.saveCount)
            )
        }
        return JSONObject().put("manuals", array)
    }

    /** One guide in the backend format. */
    fun guideToJson(guide: ManualGuide): JSONObject {
        val steps = JSONArray()
        guide.steps.forEach { step ->
            steps.put(
                JSONObject()
                    .put("number", step.number)
                    .put("title", step.title)
                    .put("description", step.description)
            )
        }

        return JSONObject()
            .put("id", guide.manualId ?: JSONObject.NULL)
            .put("appliance_name", guide.applianceName)
            .put("issue_title", guide.issueTitle)
            .put("overview", guide.overview)
            .put("steps", steps)
            .put("warnings", JSONArray(guide.warnings))
            .put("image_url", guide.imageUrl ?: JSONObject.NULL)
    }

    /** Reads one guide; null when it has no title. */
    fun guideFromJson(item: JSONObject): ManualGuide? {
        val title = item.optString("issue_title").trim()
        if (title.isEmpty()) return null

        return ManualGuide(
            applianceName = item.optString("appliance_name").trim().ifEmpty { "Appliance" },
            issueTitle = title,
            overview = item.optString("overview").trim(),
            steps = parseSteps(item),
            imageUrl = item.textOrNull("image_url"),
            warnings = parseWarnings(item),
            manualId = item.textOrNull("id")
        )
    }

    private fun parseSteps(item: JSONObject): List<ManualStep> {
        val array = item.optJSONArray("steps") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val step = array.optJSONObject(index) ?: return@mapNotNull null
            ManualStep(
                number = step.optInt("number", index + 1),
                title = step.optString("title").trim().ifEmpty { "Step ${index + 1}" },
                description = step.optString("description").trim()
            )
        }
    }

    private fun parseWarnings(item: JSONObject): List<String> {
        val array = item.optJSONArray("warnings") ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotEmpty() }
    }
}

/** optString() turns a JSON null into the text "null": this returns null instead. */
internal fun JSONObject.textOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.positiveLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key).takeIf { it > 0 }
