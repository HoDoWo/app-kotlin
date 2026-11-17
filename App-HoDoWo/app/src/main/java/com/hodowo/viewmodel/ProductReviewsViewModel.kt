package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.ReviewsPage
import com.hodowo.data.ReviewsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** The "write a review" dialog: stars, text, and whether it changes an existing review. */
data class ReviewEditorState(
    val rating: Int = 0,
    val text: String = "",
    val editing: Boolean = false,
    val error: String? = null,
    val isSending: Boolean = false
)

data class ProductReviewsUiState(
    val isLoading: Boolean = true,
    val page: ReviewsPage = ReviewsPage(),
    val error: String? = null,
    /** Non-null while the dialog is open. */
    val editor: ReviewEditorState? = null,
    /** One-off feedback ("Thanks for your review"); cleared by [ProductReviewsViewModel.consumeMessage]. */
    val message: String? = null
)

/**
 * Reviews of one product: the average and stars, the reviews, and writing,
 * changing or deleting one's own (only buyers can; the backend checks it).
 */
class ProductReviewsViewModel(
    private val service: ReviewsService,
    private val productType: String,
    private val productId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductReviewsUiState())
    val uiState: StateFlow<ProductReviewsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val page = service.reviews(productType, productId)
                _uiState.update { it.copy(isLoading = false, page = page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load the reviews") }
            }
        }
    }

    /** Opens the dialog, filled in with the user's review when there is one. */
    fun startWriting() {
        val mine = _uiState.value.page.myReview
        val editor = ReviewEditorState(rating = mine?.rating ?: 0, text = mine?.text.orEmpty(), editing = mine != null)
        _uiState.update { it.copy(editor = editor) }
    }

    fun setRating(rating: Int) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(rating = rating.coerceIn(1, MAX_STARS), error = null))
        }
    }

    fun setText(text: String) {
        _uiState.update { state -> state.copy(editor = state.editor?.copy(text = text.take(MAX_TEXT), error = null)) }
    }

    fun cancel() {
        _uiState.update { it.copy(editor = null) }
    }

    fun submit() {
        val editor = _uiState.value.editor ?: return
        if (editor.rating !in 1..MAX_STARS) {
            _uiState.update { it.copy(editor = editor.copy(error = "Choose from 1 to 5 stars")) }
            return
        }
        send(editor, "Thanks for your review") {
            service.saveReview(productType, productId, editor.rating, editor.text)
        }
    }

    fun delete() {
        val editor = _uiState.value.editor ?: return
        send(editor, "Review deleted") { service.deleteReview(productType, productId) }
    }

    private fun send(editor: ReviewEditorState, done: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(editor = editor.copy(isSending = true, error = null)) }
            try {
                action()
                _uiState.update { it.copy(editor = null, message = done) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(editor = editor.copy(isSending = false, error = e.message ?: "Could not save the review"))
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        const val MAX_STARS = 5
        const val MAX_TEXT = 1000
    }
}
