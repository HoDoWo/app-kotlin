package com.hodowo.data

import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.RecentManual
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [ManualJson] and the offline copy of the Home lists ([FileManualsCache]). */
class ManualJsonTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val manual = RecentManual(
        guide = ManualGuide(
            applianceName = "Indesit Oven",
            issueTitle = "Set 180 degrees with the fan",
            overview = "Use the right knob.",
            steps = listOf(ManualStep(1, "Knob", "Turn it to 180")),
            imageUrl = "https://img.test/oven.png",
            warnings = listOf("Hot surfaces"),
            manualId = "m1"
        ),
        createdAtMillis = 1_800_000_000_000L,
        isBookmarked = true,
        searchCount = 12,
        saveCount = 3
    )

    @Test
    fun aListSurvivesTheRoundTrip() {
        val json = ManualJson.toJson(listOf(manual))

        assertEquals(listOf(manual), ManualJson.parse(JSONObject(json.toString())))
    }

    @Test
    fun missingValuesAreReadAsNullOrZero() {
        val minimal = manual.copy(
            guide = manual.guide.copy(imageUrl = null, manualId = null, steps = emptyList(), warnings = emptyList()),
            createdAtMillis = null,
            isBookmarked = false,
            searchCount = 0,
            saveCount = 0
        )

        assertEquals(listOf(minimal), ManualJson.parse(JSONObject(ManualJson.toJson(listOf(minimal)).toString())))
    }

    @Test
    fun popularCountsAreRead() {
        val payload = JSONObject(
            """{"manuals": [{"id": "p1", "issue_title": "E18", "search_count": 9, "save_count": 4}]}"""
        )

        val popular = ManualJson.parse(payload).single()

        assertEquals(9, popular.searchCount)
        assertEquals(4, popular.saveCount)
        assertEquals("Appliance", popular.guide.applianceName)
    }

    // ---------- FileManualsCache ----------

    @Test
    fun theCacheGivesBackWhatWasSaved() = runTest {
        val cache = FileManualsCache(folder.root)

        cache.save("recent_manuals_user-1", listOf(manual))

        assertEquals(listOf(manual), cache.load("recent_manuals_user-1"))
    }

    @Test
    fun nothingSavedMeansNoCopy() = runTest {
        assertNull(FileManualsCache(folder.root).load("popular_manuals"))
    }

    @Test
    fun aBrokenFileMeansNoCopy() = runTest {
        folder.newFile("popular_manuals.json").writeText("{ not json")

        assertNull(FileManualsCache(folder.root).load("popular_manuals"))
    }

    @Test
    fun namesCannotLeaveTheFolder() = runTest {
        val cache = FileManualsCache(folder.root)

        cache.save("../../evil", listOf(manual))

        assertTrue(folder.root.listFiles()!!.any { it.name == "______evil.json" })
    }
}
