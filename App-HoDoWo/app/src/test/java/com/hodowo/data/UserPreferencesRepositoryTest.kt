package com.hodowo.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.storeIn(file: File, scope: CoroutineScope = backgroundScope) =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun newFile() = File(folder.root, "user_preferences_${System.nanoTime()}.preferences_pb")

    @Test
    fun aNewStoreGivesTheDefaults() = runTest(UnconfinedTestDispatcher()) {
        val repository = UserPreferencesRepository(storeIn(newFile()))
        assertEquals(UserPreferences(), repository.preferences.first())
    }

    @Test
    fun choicesAreStoredAndReadBack() = runTest(UnconfinedTestDispatcher()) {
        val repository = UserPreferencesRepository(storeIn(newFile()))

        repository.setThemeMode(ThemeMode.DARK)
        repository.setFontScale(1.5f)
        repository.setColourVisionMode(ColourVisionMode.PROTANOPIA)
        repository.setLanguageTag("ca")

        val stored = repository.preferences.first()
        assertEquals(ThemeMode.DARK, stored.themeMode)
        assertEquals(1.5f, stored.fontScale)
        assertEquals(ColourVisionMode.PROTANOPIA, stored.colourVisionMode)
        assertEquals("ca", stored.languageTag)
    }

    @Test
    fun choicesSurviveANewStoreOnTheSameFile() = runTest(UnconfinedTestDispatcher()) {
        val file = newFile()
        // Only one DataStore may be open per file: close the first one, like a process death.
        val firstRun = Job(backgroundScope.coroutineContext[Job])
        UserPreferencesRepository(storeIn(file, CoroutineScope(backgroundScope.coroutineContext + firstRun)))
            .setHighContrast(true)
        firstRun.cancelAndJoin()

        val reopened = UserPreferencesRepository(storeIn(file))
        assertEquals(true, reopened.preferences.first().highContrast)
    }

    @Test
    fun invalidStoredValuesAreReadAsDefaults() = runTest(UnconfinedTestDispatcher()) {
        val store = storeIn(newFile())
        store.edit {
            it[stringPreferencesKey(PreferenceKeys.THEME_MODE)] = "NEON"
            it[floatPreferencesKey(PreferenceKeys.FONT_SCALE)] = 40f
        }
        val preferences = UserPreferencesRepository(store).preferences.first()
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertEquals(UserPreferences.MAX_FONT_SCALE, preferences.fontScale)
    }

    @Test
    fun resetGoesBackToTheDefaults() = runTest(UnconfinedTestDispatcher()) {
        val repository = UserPreferencesRepository(storeIn(newFile()))
        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setReduceMotion(true)

        repository.reset()

        assertEquals(UserPreferences(), repository.preferences.first())
    }
}
