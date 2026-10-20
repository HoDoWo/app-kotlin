package com.hodowo.viewmodel

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Unit tests for [HomeViewModel] and the Home helper functions. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = 1_800_000_000_000L
    private lateinit var service: FakeHomeService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = FakeHomeService()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(name: String = "Alex") =
        HomeViewModel(aiService = service, userName = { name }, clock = { now })

    // ---------- refresh ----------

    @Test
    fun refreshLoadsNameTipAndManuals() = runTest(dispatcher) {
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Alex", state.userName)
        assertEquals("Wash at 30 degrees.", state.tip)
        assertEquals(listOf("E18"), state.recentManuals.map { it.guide.issueTitle })
        assertFalse(state.isLoadingManuals)
        assertFalse(state.manualsFailed)
        assertEquals(HomeViewModel.RECENT_MANUALS_LIMIT, service.lastLimit)
    }

    @Test
    fun theTipIsLoadedOnlyOnce() = runTest(dispatcher) {
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, service.tipCalls)
        assertEquals(2, service.manualCalls)
    }

    @Test
    fun aFailedTipKeepsTheLocalTip() = runTest(dispatcher) {
        service.tipError = IOException("offline")
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(fallbackTip(now), vm.uiState.value.tip)
    }

    @Test
    fun aBlankTipKeepsTheLocalTip() = runTest(dispatcher) {
        service.tip = "   "
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(fallbackTip(now), vm.uiState.value.tip)
    }

    @Test
    fun failedManualsAreReportedAndTheOldListIsKept() = runTest(dispatcher) {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        service.manualsError = IOException("Server error 503")
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.manualsFailed)
        assertFalse(state.isLoadingManuals)
        assertEquals(1, state.recentManuals.size)
    }

    @Test
    fun aSuccessfulRetryClearsTheError() = runTest(dispatcher) {
        service.manualsError = IOException("offline")
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        service.manualsError = null
        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.manualsFailed)
        assertEquals(1, vm.uiState.value.recentManuals.size)
    }

    @Test
    fun theDemoServiceShowsTheMockupManuals() = runTest(dispatcher) {
        val vm = HomeViewModel(aiService = DemoHoDoWoAiService(), clock = { now })

        vm.refresh()
        advanceUntilIdle()

        val titles = vm.uiState.value.recentManuals.map { it.guide.issueTitle }
        assertEquals(listOf("Error E18: Drain Pump Clog", "Descaling Routine & Flush"), titles)
        assertEquals("Run washer after 10:00 PM tonight to save on power.", vm.uiState.value.tip)
        assertEquals(DEFAULT_USER_NAME, vm.uiState.value.userName)
    }

    // ---------- greetingName ----------

    @Test
    fun greetingUsesTheFirstNameOfTheDisplayName() {
        assertEquals("Davide", greetingName("Davide Pignataro", "x@y.com"))
    }

    @Test
    fun greetingFallsBackToTheEmail() {
        assertEquals("Antote", greetingName(null, "antote.dev@gmail.com"))
        assertEquals("Anna", greetingName("  ", "anna_marin@udl.cat"))
    }

    @Test
    fun guestsAreGreetedGenerically() {
        assertEquals(DEFAULT_USER_NAME, greetingName(null, null))
        assertEquals(DEFAULT_USER_NAME, greetingName("", "@nothing.com"))
    }

    // ---------- relativeTimeLabel ----------

    @Test
    fun relativeTimeLabels() {
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour

        assertNull(relativeTimeLabel(null, now))
        assertEquals("Just now", relativeTimeLabel(now - 10_000, now))
        assertEquals("5 min ago", relativeTimeLabel(now - 5 * minute, now))
        assertEquals("2h ago", relativeTimeLabel(now - 2 * hour, now))
        assertEquals("Yesterday", relativeTimeLabel(now - 26 * hour, now))
        assertEquals("3 days ago", relativeTimeLabel(now - 3 * day, now))
        assertEquals("Just now", relativeTimeLabel(now + hour, now)) // clock skew
    }

    @Test
    fun olderManualsShowTheDate() {
        val label = relativeTimeLabel(now - 30L * 24 * 60 * 60 * 1000, now)

        assertTrue(label!!.isNotBlank())
        assertFalse(label.endsWith("ago"))
    }

    // ---------- fallbackTip ----------

    @Test
    fun theLocalTipChangesOncePerDay() {
        val day = 24L * 60 * 60 * 1000
        val start = 20_000L * day

        assertEquals(fallbackTip(start), fallbackTip(start + day - 1))
        assertTrue(fallbackTip(start) != fallbackTip(start + day))
        assertTrue(FALLBACK_TIPS.contains(fallbackTip(0)))
    }
}

/** Fake backend for the Home screen: only the Home calls are implemented. */
private class FakeHomeService : HoDoWoAiService {
    var tip: String? = "Wash at 30 degrees."
    var tipError: Exception? = null
    var manualsError: Exception? = null
    var tipCalls = 0
    var manualCalls = 0
    var lastLimit = 0

    override suspend fun dailyTip(): String? {
        tipCalls++
        tipError?.let { throw it }
        return tip
    }

    override suspend fun recentManuals(limit: Int): List<RecentManual> {
        manualCalls++
        lastLimit = limit
        manualsError?.let { throw it }
        return listOf(
            RecentManual(
                guide = ManualGuide(applianceName = "Bosch Washer", issueTitle = "E18", overview = "Clean the filter"),
                createdAtMillis = 1L
            )
        )
    }

    override suspend fun identifyAppliance(uri: Uri): Appliance = error("not used")
    override suspend fun askQuestion(question: String, appliance: Appliance?): String = error("not used")
    override suspend fun locatePanel(uri: Uri): PanelBox? = null
    override suspend fun analyzePanel(uri: Uri, appliance: Appliance?): List<PanelControl> = emptyList()
    override suspend fun sendFeedback(report: FeedbackReport) = Unit
}
