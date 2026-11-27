package com.hodowo.viewmodel

import com.hodowo.data.ColourVisionMode
import com.hodowo.data.FakePreferencesDataStore
import com.hodowo.data.ThemeMode
import com.hodowo.data.UserPreferences
import com.hodowo.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The languages the view model asked the app to show, in order. */
    private val applied = mutableListOf<String>()

    private fun createViewModel(): AppPreferencesViewModel {
        val store = FakePreferencesDataStore()
        return AppPreferencesViewModel(UserPreferencesRepository(store)) {
            synchronized(applied) { applied += it }
        }
    }

    @Test
    fun theStoredChoicesArePublishedOnceRead() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertEquals(UserPreferences(), viewModel.preferences.filterNotNull().first())
    }

    @Test
    fun aChangeIsPublishedAtOnce() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setFontScale(1.15f)
        viewModel.setColourVisionMode(ColourVisionMode.GRAYSCALE)

        val published = viewModel.preferences.filterNotNull()
            .first { it.colourVisionMode == ColourVisionMode.GRAYSCALE }
        assertEquals(ThemeMode.DARK, published.themeMode)
        assertEquals(1.15f, published.fontScale)
        assertEquals(ColourVisionMode.GRAYSCALE, published.colourVisionMode)
    }

    @Test
    fun resetPublishesTheDefaults() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.setHighContrast(true)
        viewModel.setLanguageTag("es")

        viewModel.reset()

        val reset = viewModel.preferences.filterNotNull().first { !it.highContrast }
        assertEquals(UserPreferences(), reset)
    }

    @Test
    fun aChosenLanguageIsStoredAndApplied() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.setLanguageTag("it")

        assertEquals("it", viewModel.preferences.filterNotNull().first { it.languageTag == "it" }.languageTag)
        assertEquals(listOf("it"), applied.toList())
    }

    @Test
    fun resetGoesBackToTheSystemLanguage() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.setLanguageTag("es")

        viewModel.reset()

        assertEquals("", viewModel.preferences.filterNotNull().first { it.languageTag.isEmpty() }.languageTag)
        assertEquals(listOf("es", ""), applied.toList())
    }

    @Test
    fun theLanguageChosenInTheSystemIsStoredButNotAppliedAgain() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.syncLanguage("ca")

        assertEquals("ca", viewModel.preferences.filterNotNull().first { it.languageTag == "ca" }.languageTag)
        assertEquals(emptyList<String>(), applied.toList())
    }
}
