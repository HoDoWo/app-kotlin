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

    fun search(query: String, type: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { service.search(query, type) }
                .onSuccess { _products.value = it }
                .onFailure { _error.value = it.message ?: "Could not load products" }
            _isLoading.value = false
        }
    }
}
