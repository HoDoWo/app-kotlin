package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.MarketplaceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MarketplaceViewModel(private val service: MarketplaceService) : ViewModel() {
    private val _products = MutableStateFlow<List<MarketplaceProduct>>(emptyList())
    val products: StateFlow<List<MarketplaceProduct>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _query = MutableStateFlow("Drain pump washer")
    val query: StateFlow<String> = _query.asStateFlow()

    // "all", "part", "appliance"
    private val _classification = MutableStateFlow("part")
    val classification: StateFlow<String> = _classification.asStateFlow()

    // "all", "new", "used"
    private val _condition = MutableStateFlow("all")
    val condition: StateFlow<String> = _condition.asStateFlow()

    private val _fitsBoschSerie4 = MutableStateFlow(true)
    val fitsBoschSerie4: StateFlow<Boolean> = _fitsBoschSerie4.asStateFlow()

    private val _under50 = MutableStateFlow(true)
    val under50: StateFlow<Boolean> = _under50.asStateFlow()

    private val _cartCount = MutableStateFlow(2)
    val cartCount: StateFlow<Int> = _cartCount.asStateFlow()

    init {
        performSearch()
    }

    fun setQuery(newQuery: String) {
        _query.value = newQuery
        performSearch()
    }

    fun clearQuery() {
        _query.value = ""
        performSearch()
    }

    fun setClassification(type: String) {
        _classification.value = type
        performSearch()
    }

    fun setCondition(cond: String) {
        _condition.value = cond
        performSearch()
    }

    fun toggleFitsBosch() {
        _fitsBoschSerie4.value = !_fitsBoschSerie4.value
        performSearch()
    }

    fun toggleUnder50() {
        _under50.value = !_under50.value
        performSearch()
    }

    fun addToCart(product: MarketplaceProduct) {
        _cartCount.value += 1
    }

    fun search(
        query: String = _query.value,
        type: String = _classification.value,
        condition: String = _condition.value,
        fitsBosch: Boolean = _fitsBoschSerie4.value,
        under50: Boolean = _under50.value
    ) {
        _query.value = query
        _classification.value = type
        _condition.value = condition
        _fitsBoschSerie4.value = fitsBosch
        _under50.value = under50
        performSearch()
    }

    private fun performSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                service.search(
                    query = _query.value,
                    type = _classification.value,
                    condition = if (_condition.value == "all") null else _condition.value,
                    brand = if (_fitsBoschSerie4.value) "Bosch" else null,
                    maxPrice = if (_under50.value) 50.0 else null
                )
            }.onSuccess {
                _products.value = it
            }.onFailure {
                _error.value = it.message ?: "Could not load products"
            }
            _isLoading.value = false
        }
    }
}
