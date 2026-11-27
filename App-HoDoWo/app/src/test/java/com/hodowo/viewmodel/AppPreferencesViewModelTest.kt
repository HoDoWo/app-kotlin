package com.hodowo.viewmodel

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.hodowo.data.ColourVisionMode
import com.hodowo.data.ThemeMode
import com.hodowo.data.UserPreferences
import com.hodowo.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val storeScope = CoroutineScope(dispatcher + Job())

    @Before
    fun setUp() {
        // viewModelScope uses Dispatchers.Main, which does not exist in unit tests.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(): AppPreferencesViewModel {
        val store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(folder.root, "prefs_${System.nanoTime()}.preferences_pb")
        }
        return AppPreferencesViewModel(UserPreferencesRepository(store))
    }

    @Test
    fun theStoredChoicesArePublishedOnceRead() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertEquals(UserPreferences(), viewModel.preferences.filterNotNull().first())
    }

    @Test
    fun aChangeIsPublishedAtOnce() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setFontScale(1.15f)
        viewModel.setColourVisionMode(ColourVisionMode.GRAYSCALE)

        // The store writes its file off the test thread: wait for the last change.
        val published = viewModel.preferences.filterNotNull()
            .first { it.colourVisionMode == ColourVisionMode.GRAYSCALE }
        assertEquals(ThemeMode.DARK, published.themeMode)
        assertEquals(1.15f, published.fontScale)
        assertEquals(ColourVisionMode.GRAYSCALE, published.colourVisionMode)
    }

    @Test
    fun resetPublishesTheDefaults() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setHighContrast(true)
        viewModel.setLanguageTag("es")
        viewModel.preferences.filterNotNull().first { it.languageTag == "es" }

        viewModel.reset()

        assertEquals(UserPreferences(), viewModel.preferences.filterNotNull().first { !it.highContrast })
    }
}
