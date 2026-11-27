package com.hodowo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory DataStore implementation for fast, reliable unit tests without file locks. */
class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val _data = MutableStateFlow(initial)
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val mutable = _data.value.toMutablePreferences()
        val updated = transform(mutable)
        _data.value = updated
        return updated
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    @Test
    fun aNewStoreGivesTheDefaults() = runTest(UnconfinedTestDispatcher()) {
        val repository = UserPreferencesRepository(FakePreferencesDataStore())
        assertEquals(UserPreferences(), repository.preferences.first())
    }

    @Test
    fun choicesAreStoredAndReadBack() = runTest(UnconfinedTestDispatcher()) {
        val repository = UserPreferencesRepository(FakePreferencesDataStore())

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
        val fakeStore = FakePreferencesDataStore()
        UserPreferencesRepository(fakeStore).setHighContrast(true)

        val reopened = UserPreferencesRepository(fakeStore)
        assertEquals(true, reopened.preferences.first().highContrast)
    }

    @Test
    fun invalidStoredValuesAreReadAsDefaults() = runTest(UnconfinedTestDispatcher()) {
        val store = FakePreferencesDataStore()
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
        val repository = UserPreferencesRepository(FakePreferencesDataStore())
        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setReduceMotion(true)

        repository.reset()

        assertEquals(UserPreferences(), repository.preferences.first())
    }
}
