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

    /** The languages the view model asked the app to show, in order. */
    private val applied = mutableListOf<String>()

    private fun viewModel(): AppPreferencesViewModel {
        val store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(folder.root, "prefs_${System.nanoTime()}.preferences_pb")
        }
        return AppPreferencesViewModel(UserPreferencesRepository(store)) { synchronized(applied) { applied += it } }
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

    @Test
    fun aChosenLanguageIsStoredAndApplied() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setLanguageTag("it")

        assertEquals("it", viewModel.preferences.filterNotNull().first { it.languageTag == "it" }.languageTag)
        waitUntil { applied.isNotEmpty() }
        assertEquals(listOf("it"), applied.toList())
    }

    @Test
    fun resetGoesBackToTheSystemLanguage() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setLanguageTag("es")
        waitUntil { applied.size == 1 }

        viewModel.reset()

        waitUntil { applied.size == 2 }
        assertEquals(listOf("es", ""), applied.toList())
    }

    @Test
    fun theLanguageChosenInTheSystemIsStoredButNotAppliedAgain() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.syncLanguage("ca")

        assertEquals("ca", viewModel.preferences.filterNotNull().first { it.languageTag == "ca" }.languageTag)
        assertEquals(emptyList<String>(), applied.toList())
    }

    /** The store writes off the test thread: wait (real time) for [done]. */
    private fun waitUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!synchronized(applied) { done() } && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
