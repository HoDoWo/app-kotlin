package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.CartException
import com.hodowo.data.CartStore
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.MarketplaceService
import com.hodowo.data.UnavailableCartService
import com.hodowo.data.cartProductType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Search, filters and "add to cart" of the marketplace screens.
 *
 * The cart is the shared [CartStore] (Backend#42 cart API), so the badge shows the
 * same number on every screen.
 */
class MarketplaceViewModel(
    private val service: MarketplaceService,
    private val cart: CartStore = CartStore(UnavailableCartService)
) : ViewModel() {
    private val _products = MutableStateFlow<List<MarketplaceProduct>>(emptyList())
    val products: StateFlow<List<MarketplaceProduct>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // "all", "part", "appliance"
    private val _classification = MutableStateFlow("all")
    val classification: StateFlow<String> = _classification.asStateFlow()

    // "all", "new", "used"
    private val _condition = MutableStateFlow("all")
    val condition: StateFlow<String> = _condition.asStateFlow()

    private val _fitsBoschSerie4 = MutableStateFlow(false)
    val fitsBoschSerie4: StateFlow<Boolean> = _fitsBoschSerie4.asStateFlow()

    private val _under50 = MutableStateFlow(false)
    val under50: StateFlow<Boolean> = _under50.asStateFlow()

    /** Available items in the shared cart, for the badge. */
    val cartCount: StateFlow<Int> = cart.cart
        .map { it.itemCount }
        .stateIn(viewModelScope, SharingStarted.Eagerly, cart.cart.value.itemCount)

    /** One-off feedback of "add to cart" ("Added to cart: …", "already sold"…). */
    private val _cartMessage = MutableStateFlow<String?>(null)
    val cartMessage: StateFlow<String?> = _cartMessage.asStateFlow()

    init {
        performSearch()
        refreshCart()
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
        val productType = product.cartProductType()
        if (productType == null) {
            _cartMessage.value = "This product cannot be bought yet"
            return
        }
        viewModelScope.launch {
            _cartMessage.value = try {
                cart.add(productType, product.id)
                "Added to cart: ${product.title}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: CartException) {
                // The backend explains why: "already sold", "not enough stock"...
                "Could not add to cart: ${e.message}"
            } catch (e: IOException) {
                "Could not add to cart, check your connection"
            }
        }
    }

    fun consumeCartMessage() {
        _cartMessage.value = null
    }

    /** Loads the cart, so the badge is right when the marketplace opens. */
    fun refreshCart() {
        viewModelScope.launch {
            try {
                cart.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // The badge keeps the last known value; the cart screen shows the error.
            }
        }
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