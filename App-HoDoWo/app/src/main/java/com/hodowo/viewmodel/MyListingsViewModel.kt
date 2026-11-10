package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.ListingsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the "My Listings" screen shows (issue #146). */
data class MyListingsUiState(
    val isLoading: Boolean = true,
    val listings: List<Listing> = emptyList(),
    val error: String? = null,
    /** Listing being edited in the (lightweight, stand-in for #145) edit dialog. */
    val editingListing: Listing? = null,
    /** Listing waiting for the user to confirm marking it as sold. */
    val pendingMarkAsSold: Listing? = null,
    /** Listing waiting for the user to confirm deletion. */
    val pendingDelete: Listing? = null,
    /** One-off feedback message; cleared by [MyListingsViewModel.consumeMessage]. */
    val message: String? = null
)

/**
 * State of "My Listings" (issue #146): lists the seller's listings and lets them
 * edit, mark as sold or delete one, with a confirmation step for the latter two.
 */
class MyListingsViewModel(
    private val service: ListingsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyListingsUiState())
    val uiState: StateFlow<MyListingsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            _uiState.value = try {
                val listings = service.myListings()
                _uiState.value.copy(isLoading = false, listings = listings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load your listings")
            }
        }
    }

    fun startEditing(listing: Listing) {
        _uiState.value = _uiState.value.copy(editingListing = listing)
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(editingListing = null)
    }

    fun saveEdit(title: String, description: String, condition: String, priceLabel: String) {
        val id = _uiState.value.editingListing?.id ?: return

        viewModelScope.launch {
            try {
                val updated = service.updateListing(id, title, description, condition, priceLabel)
                _uiState.value = _uiState.value.copy(
                    editingListing = null,
                    listings = _uiState.value.listings.map { if (it.id == id) updated else it },
                    message = "Listing updated"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = e.message ?: "Could not update the listing"
                )
            }
        }
    }

    fun requestMarkAsSold(listing: Listing) {
        _uiState.value = _uiState.value.copy(pendingMarkAsSold = listing)
    }

    fun dismissMarkAsSold() {
        _uiState.value = _uiState.value.copy(pendingMarkAsSold = null)
    }

    fun confirmMarkAsSold() {
        val id = _uiState.value.pendingMarkAsSold?.id ?: return

        viewModelScope.launch {
            try {
                val updated = service.markAsSold(id)
                _uiState.value = _uiState.value.copy(
                    pendingMarkAsSold = null,
                    listings = _uiState.value.listings.map { if (it.id == id) updated else it },
                    message = "Marked as sold"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = e.message ?: "Could not mark the listing as sold"
                )
            }
        }
    }

    fun requestDelete(listing: Listing) {
        _uiState.value = _uiState.value.copy(pendingDelete = listing)
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val id = _uiState.value.pendingDelete?.id ?: return

        viewModelScope.launch {
            try {
                service.deleteListing(id)
                _uiState.value = _uiState.value.copy(
                    pendingDelete = null,
                    listings = _uiState.value.listings.filterNot { it.id == id },
                    message = "Listing deleted"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = e.message ?: "Could not delete the listing"
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
