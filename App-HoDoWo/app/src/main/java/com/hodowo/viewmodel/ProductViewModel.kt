package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.ProductRepository
import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductPage
import com.hodowo.data.model.ProductType
import com.hodowo.data.model.ProductUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class ProductViewModel(
    private val repository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _filters = MutableStateFlow(ProductFilters())
    private val _uiState = MutableStateFlow<ProductUiState>(ProductUiState.Loading)
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    val filters: ProductFilters
        get() = _filters.value

    private var currentCursor: String? = null
    private var isLoadingMore = false
    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        load(ProductFilters(), null, true)
    }

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            withContext(Dispatchers.Main) {
                _filters.value = _filters.value.copy(query = query)
                load(_filters.value, null, true)
            }
        }
    }

    fun onTypeSelected(type: ProductType?) {
        _filters.value = _filters.value.copy(type = type)
        load(_filters.value, null, true)
    }

    fun onConditionSelected(condition: ProductCondition?) {
        _filters.value = _filters.value.copy(condition = condition)
        load(_filters.value, null, true)
    }

    fun onCategorySelected(category: String?) {
        _filters.value = _filters.value.copy(category = category)
        load(_filters.value, null, true)
    }

    fun onBrandSelected(brand: String?) {
        _filters.value = _filters.value.copy(brand = brand)
        load(_filters.value, null, true)
    }

    fun onPriceRangeSelected(min: Double?, max: Double?) {
        _filters.value = _filters.value.copy(minPrice = min, maxPrice = max)
        load(_filters.value, null, true)
    }

    fun onClearFilters() {
        _filters.value = ProductFilters()
        load(ProductFilters(), null, true)
    }

    fun onRefresh() {
        load(_filters.value, null, true)
    }

    fun loadMore() {
        if (isLoadingMore) return
        val state = _uiState.value
        val currentPage = when (state) {
            is ProductUiState.Success -> state.page
            else -> return
        }
        if (!currentPage.hasMore || currentPage.nextCursor == null) return

        isLoadingMore = true
        viewModelScope.launch {
            load(_filters.value, currentPage.nextCursor, false)
            withContext(Dispatchers.Main) { isLoadingMore = false }
        }
    }

    private fun load(
        filters: ProductFilters,
        cursor: String?,
        refresh: Boolean
    ) {
        viewModelScope.launch {
            try {
                if (refresh) {
                    currentCursor = null
                    _uiState.value = ProductUiState.Loading
                }
                val page = repository.search(filters = filters, cursor = cursor).first()
                currentCursor = page.nextCursor
                _uiState.value = when {
                    page.products.isEmpty() && refresh -> ProductUiState.Empty
                    else -> {
                        val products = if (refresh) page.products else {
                            val current = _uiState.value
                            val prev = if (current is ProductUiState.Success) current.products else emptyList()
                            prev + page.products
                        }
                        ProductUiState.Success(
                            products = products,
                            page = page,
                            filters = filters,
                            isRefreshing = false,
                            hasMore = page.hasMore
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = ProductUiState.Error(
                    message = "Couldn't load products. Please try again.",
                    canRetry = true
                )
            }
        }
    }

    val categories: List<String> = repository.categories()
    val brands: List<String> = repository.brands()
    val types: List<ProductType> = listOf(ProductType.WHOLE_APPLIANCE, ProductType.REPLACEMENT_PART)
    val conditions: List<ProductCondition> = listOf(ProductCondition.NEW, ProductCondition.SECOND_HAND)
}
