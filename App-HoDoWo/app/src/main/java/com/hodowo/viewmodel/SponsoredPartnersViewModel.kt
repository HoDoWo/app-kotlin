package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.MarketplaceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sponsored products of one partner (#141). */
data class SponsoredGroup(val partnerName: String, val products: List<MarketplaceProduct>)

data class SponsoredPartnersState(
    val groups: List<SponsoredGroup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True once a load finished, so "nothing sponsored" is not shown while loading. */
    val hasLoaded: Boolean = false
)

/**
 * Page of sponsored partner products (issue #141), apart from the organic search results.
 * Only products the backend flags as `sponsored` are listed, grouped by partner in the order
 * the backend ranked them (Backend#41).
 */
class SponsoredPartnersViewModel(private val service: MarketplaceService) : ViewModel() {
    private val _uiState = MutableStateFlow(SponsoredPartnersState())
    val uiState: StateFlow<SponsoredPartnersState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Loads the sponsored products. It is also the "Retry" button. */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val groups = service.search()
                    .filter { it.sponsored }
                    .groupBy { it.partnerName?.takeIf(String::isNotBlank) ?: it.sellerName }
                    .map { (partner, products) -> SponsoredGroup(partner, products) }
                _uiState.update { it.copy(groups = groups, isLoading = false, hasLoaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, hasLoaded = true, error = e.message ?: "Could not load the sponsored products")
                }
            }
        }
    }
}
