package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.CartException
import com.hodowo.data.CartStore
import com.hodowo.data.ProductRepository
import com.hodowo.data.ProductSearchService
import com.hodowo.data.UnavailableCartService
import com.hodowo.data.model.FilterOptions
import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductUiState
import com.hodowo.data.model.SearchKind
import com.hodowo.data.model.cartProductType
import com.hodowo.data.model.key
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/** A filter shown as a removable chip above the results. */
enum class ActiveFilter { CATEGORY, BRAND, PRICE, CONDITION }

/**
 * Product search (#139): one search box, the type chips (all / new / second-hand / spare parts)
 * and the filter sheet. Every change asks the backend again; the pages follow its cursor.
 *
 * The filters live here, so they survive a rotation; only the last search counts (an older
 * answer arriving late is dropped).
 */
class ProductViewModel(
    private val repository: ProductSearchService = ProductRepository(),
    private val cart: CartStore = CartStore(UnavailableCartService)
) : ViewModel() {

    private val _filters = MutableStateFlow(ProductFilters())
    val filtersState: StateFlow<ProductFilters> = _filters.asStateFlow()

    /** The filters of the search on screen. */
    val filters: ProductFilters
        get() = _filters.value

    private val _uiState = MutableStateFlow<ProductUiState>(ProductUiState.Loading)
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    /** Categories and brands on sale now, offered by the filter sheet. */
    private val _filterOptions = MutableStateFlow(FilterOptions())
    val filterOptions: StateFlow<FilterOptions> = _filterOptions.asStateFlow()

    /** Items in the shared cart, for the header badge. */
    val cartCount: StateFlow<Int> = cart.cart
        .map { it.itemCount }
        .stateIn(viewModelScope, SharingStarted.Eagerly, cart.cart.value.itemCount)

    /** One-off "Added to cart" / error feedback for the search screen. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var nextCursor: String? = null
    private var searchJob: Job? = null
    private var queryJob: Job? = null

    init {
        search()
        refreshCart()
        loadFilterOptions()
    }

    // ---------- filters ----------

    /** Waits for the user to stop typing, then searches. */
    fun onQueryChanged(query: String) {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(QUERY_DEBOUNCE_MS)
            update { copy(query = query.trim()) }
        }
    }

    /** The type chips: a different type is a new search, not a filter of the list on screen. */
    fun onKindSelected(kind: SearchKind) {
        update { copy(kind = kind, usedCondition = usedCondition.takeIf { kind == SearchKind.SECOND_HAND }) }
    }

    /** "Apply" of the filter sheet. A minimum above the maximum is swapped, not refused. */
    fun applyFilters(
        category: String?,
        brand: String?,
        minPrice: Double?,
        maxPrice: Double?,
        usedCondition: String?
    ) {
        val min = minPrice?.takeIf { it > 0 }
        val max = maxPrice?.takeIf { it > 0 }
        val (low, high) = if (min != null && max != null && min > max) max to min else min to max
        update {
            copy(
                category = category,
                brand = brand,
                minPrice = low,
                maxPrice = high,
                usedCondition = usedCondition.takeIf { kind == SearchKind.SECOND_HAND }
            )
        }
    }

    /** The "x" of a chip above the results. */
    fun removeFilter(filter: ActiveFilter) {
        update {
            when (filter) {
                ActiveFilter.CATEGORY -> copy(category = null)
                ActiveFilter.BRAND -> copy(brand = null)
                ActiveFilter.PRICE -> copy(minPrice = null, maxPrice = null)
                ActiveFilter.CONDITION -> copy(usedCondition = null)
            }
        }
    }

    /** "Clear all" of the sheet: the query and the type chip stay. */
    fun onClearFilters() {
        update { ProductFilters(query = query, kind = kind) }
    }

    // ---------- loading ----------

    /** Pull to refresh: the results stay on screen while they reload. */
    fun onRefresh() {
        loadFilterOptions()
        search(keepResults = true)
    }

    /** "Try again" of the error and empty states. */
    fun retry() = search()

    /** The next page, when the list reaches its end. */
    fun loadMore() {
        val state = _uiState.value as? ProductUiState.Success ?: return
        val cursor = nextCursor
        if (!state.hasMore || state.isLoadingMore || state.isRefreshing || cursor == null) return
        _uiState.value = state.copy(isLoadingMore = true, loadMoreFailed = false)
        searchJob = viewModelScope.launch {
            try {
                val page = repository.search(state.filters, cursor, PAGE_SIZE).first()
                nextCursor = page.nextCursor
                val current = _uiState.value as? ProductUiState.Success ?: return@launch
                _uiState.value = current.copy(
                    products = (current.products + page.products).distinctBy { it.key() },
                    hasMore = page.nextCursor != null,
                    isLoadingMore = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val current = _uiState.value as? ProductUiState.Success ?: return@launch
                _uiState.value = current.copy(isLoadingMore = false, loadMoreFailed = true)
            }
        }
    }

    private fun update(change: ProductFilters.() -> ProductFilters) {
        val next = _filters.value.change()
        if (next == _filters.value) return
        _filters.value = next
        search()
    }

    private fun search(keepResults: Boolean = false) {
        // Only the last search counts: the previous one (and its next page) is dropped.
        searchJob?.cancel()
        val filters = _filters.value
        val current = _uiState.value
        _uiState.value = if (keepResults && current is ProductUiState.Success) {
            current.copy(isRefreshing = true, isLoadingMore = false, loadMoreFailed = false)
        } else {
            ProductUiState.Loading
        }
        searchJob = viewModelScope.launch {
            try {
                val page = repository.search(filters, null, PAGE_SIZE).first()
                nextCursor = page.nextCursor
                _uiState.value = if (page.products.isEmpty()) {
                    ProductUiState.Empty
                } else {
                    ProductUiState.Success(
                        products = page.products.distinctBy { it.key() },
                        filters = filters,
                        hasMore = page.nextCursor != null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val shown = _uiState.value
                if (keepResults && shown is ProductUiState.Success) {
                    _uiState.value = shown.copy(isRefreshing = false)
                    _message.value = "Couldn't refresh the results, check your connection"
                } else {
                    _uiState.value = ProductUiState.Error(
                        message = "Couldn't load products. Please try again.",
                        canRetry = true
                    )
                }
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            try {
                _filterOptions.value = repository.filterOptions()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The sheet keeps what it had: price and condition still work.
            }
        }
    }

    // ---------- cart ----------

    fun addToCart(product: Product) {
        val productType = product.cartProductType()
        if (productType == null) {
            _message.value = "This product cannot be bought yet"
            return
        }
        viewModelScope.launch {
            _message.value = try {
                cart.add(productType, product.id)
                "Added to cart: ${product.title}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: CartException) {
                "Could not add to cart: ${e.message}"
            } catch (e: IOException) {
                "Could not add to cart, check your connection"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Loads the cart so the badge is correct when the search screen opens. */
    fun refreshCart() {
        viewModelScope.launch {
            try {
                cart.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Keep the last known value; the cart screen shows the error.
            }
        }
    }

    val kinds: List<SearchKind> = SearchKind.entries

    companion object {
        const val PAGE_SIZE = 20
        const val QUERY_DEBOUNCE_MS = 300L
    }
}
