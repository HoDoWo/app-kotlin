package com.hodowo.viewmodel

import com.hodowo.data.ProductReview
import com.hodowo.data.ReviewsException
import com.hodowo.data.ReviewsPage
import com.hodowo.data.ReviewsService
import com.hodowo.data.ReviewsSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProductReviewsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val service = mockk<ReviewsService>()

    private val mine = ProductReview("r1", "Anna", 4, "Good", 10L, verifiedPurchase = true, mine = true)
    private val page = ReviewsPage(ReviewsSummary(4.0, 1, mapOf(4 to 1)), listOf(mine), mine, canReview = true)

    private fun viewModel() = ProductReviewsViewModel(service, "part", "P1")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theReviewsAreLoaded() = runTest(dispatcher) {
        coEvery { service.reviews("part", "P1") } returns page
        val vm = viewModel()

        vm.load()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(page, vm.uiState.value.page)
    }

    @Test
    fun aLoadingErrorIsShown() = runTest(dispatcher) {
        coEvery { service.reviews(any(), any()) } throws IOException("Reviews are unavailable (503)")
        val vm = viewModel()

        vm.load()
        advanceUntilIdle()

        assertEquals("Reviews are unavailable (503)", vm.uiState.value.error)
    }

    @Test
    fun editingStartsFromTheUsersReview() = runTest(dispatcher) {
        coEvery { service.reviews(any(), any()) } returns page
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        vm.startWriting()

        assertEquals(ReviewEditorState(rating = 4, text = "Good", editing = true), vm.uiState.value.editor)
        vm.cancel()
        assertNull(vm.uiState.value.editor)
    }

    @Test
    fun aNewReviewIsPublishedAndTheListReloaded() = runTest(dispatcher) {
        coEvery { service.reviews(any(), any()) } returns ReviewsPage(canReview = true)
        coEvery { service.saveReview("part", "P1", 5, "Perfect") } returns mine
        val vm = viewModel()
        vm.startWriting()
        assertEquals(ReviewEditorState(), vm.uiState.value.editor)

        vm.setRating(9)
        vm.setText("Perfect")
        assertEquals(5, vm.uiState.value.editor?.rating)
        vm.submit()
        advanceUntilIdle()

        assertNull(vm.uiState.value.editor)
        assertEquals("Thanks for your review", vm.uiState.value.message)
        coVerify { service.reviews("part", "P1") }
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun withoutStarsNothingIsSent() {
        val vm = viewModel()
        vm.startWriting()

        vm.submit()

        assertEquals("Choose from 1 to 5 stars", vm.uiState.value.editor?.error)
    }

    @Test
    fun aRefusalStaysInTheDialog() = runTest(dispatcher) {
        coEvery { service.saveReview(any(), any(), any(), any()) } throws
            ReviewsException("only buyers of this product can review it")
        val vm = viewModel()
        vm.startWriting()
        vm.setRating(3)

        vm.submit()
        advanceUntilIdle()

        val editor = vm.uiState.value.editor
        assertEquals("only buyers of this product can review it", editor?.error)
        assertFalse(editor?.isSending ?: true)
    }

    @Test
    fun theUsersReviewCanBeDeleted() = runTest(dispatcher) {
        coEvery { service.reviews(any(), any()) } returns page
        coEvery { service.deleteReview("part", "P1") } returns Unit
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.startWriting()

        vm.delete()
        advanceUntilIdle()

        assertEquals("Review deleted", vm.uiState.value.message)
        assertTrue(vm.uiState.value.editor == null)
    }

    @Test
    fun withoutTheDialogNothingHappens() {
        val vm = viewModel()

        vm.submit()
        vm.delete()
        vm.setRating(3)
        vm.setText("x")

        assertNull(vm.uiState.value.editor)
    }
}
