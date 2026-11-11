package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.MarketplaceService
import com.hodowo.data.PartsCache
import com.hodowo.data.PartsSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PartsCatalogueState(
    /** What the user typed: a part name, brand or model number. */
    val query: String = "",
    val category: String? = null,
    val brand: String? = null,
    /** Every part of the last search, before the category and brand filters. */
    val parts: List<MarketplaceProduct> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** The list is the last saved search, because the new one failed. */
    val isFromCache: Boolean = false,
    /** True once a search finished (or the cache answered), so "no results" is not shown while loading. */
    val hasSearched: Boolean = false
) {
    val categories: List<String> get() = parts.mapNotNull { it.category?.takeIf(String::isNotBlank) }.distinct().sorted()
    val brands: List<String> get() = parts.mapNotNull { it.brand?.takeIf(String::isNotBlank) }.distinct().sorted()

    /** [parts] narrowed by the selected category and brand. */
    val visibleParts: List<MarketplaceProduct>
        get() = parts.filter { part ->
            (category == null || part.category.equals(category, ignoreCase = true)) &&
                (brand == null || part.brand.equals(brand, ignoreCase = true))
        }
}

/**
 * Parts catalogue (issue #148): search by name, brand or model number, filters by category and
 * brand, and the last search kept on the phone for when the network fails.
 */
class PartsCatalogueViewModel(
    private val service: MarketplaceService,
    private val cache: PartsCache
) : ViewModel() {
    private val _uiState = MutableStateFlow(PartsCatalogueState())
    val uiState: StateFlow<PartsCatalogueState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // The saved search is shown at once, and the network refreshes it.
        cache.load()?.let { saved ->
            _uiState.update { it.copy(query = saved.query, parts = saved.parts, isFromCache = true, hasSearched = true) }
        }
        search()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(category = category) }
    }

    fun selectBrand(brand: String?) {
        _uiState.update { it.copy(brand = brand) }
    }

    fun clearFilters() {
        _uiState.update { it.copy(category = null, brand = null) }
    }

    /** Searches the parts for the current query. It is also the "Retry" button. */
    fun search() {
        searchJob?.cancel()
        val query = _uiState.value.query.trim()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val parts = service.search(query = query, type = "part")
                cache.save(PartsSearch(query, parts))
                _uiState.update {
                    it.copy(
                        parts = parts,
                        isLoading = false,
                        isFromCache = false,
                        hasSearched = true,
                        // A filter that the new results do not have any more would hide everything.
                        category = it.category?.takeIf { c -> parts.any { p -> p.category.equals(c, true) } },
                        brand = it.brand?.takeIf { b -> parts.any { p -> p.brand.equals(b, true) } }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Could not load the parts",
                        hasSearched = true
                    )
                }
            }
        }
    }
}
