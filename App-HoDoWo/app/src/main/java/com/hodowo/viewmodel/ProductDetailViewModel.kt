package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.CartRepository
import com.hodowo.data.DemoMarketplaceService
import com.hodowo.data.InMemoryCartRepository
import com.hodowo.data.MarketplaceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the Product Detail screen shows. */
data class ProductDetailUiState(
    val isLoading: Boolean = true,
    val product: ProductDetail? = null,
    val error: String? = null,
    /** True while a part add-to-cart/buy needs the user to confirm the compatibility mismatch. */
    val pendingIncompatibleConfirmation: Boolean = false,
    /** One-off "Added to cart"/"Purchase started" feedback; cleared by [ProductDetailViewModel.consumeCartMessage]. */
    val cartMessage: String? = null
)

/**
 * State of the Product Detail screen (issue #140): loads one [ProductDetail]
 * (new product, second-hand listing or spare part) and wires the add-to-cart
 * and buy actions to [cartRepository].
 *
 * Adding an incompatible part ([PartCompatibility.DOES_NOT_FIT]) asks for
 * confirmation before it reaches the cart, per the issue's acceptance criteria.
 */
class ProductDetailViewModel(
    private val productId: String,
    private val marketplaceService: MarketplaceService = DemoMarketplaceService(),
    private val cartRepository: CartRepository = InMemoryCartRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    /** Pending action waiting for the user to confirm the compatibility mismatch. */
    private var pendingAction: (suspend () -> Unit)? = null

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            _uiState.value = try {
                val product = marketplaceService.getProductDetail(productId)
                _uiState.value.copy(isLoading = false, product = product)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load this product")
            }
        }
    }

    fun onAddToCartClicked() {
        runOrAskConfirmation {
            if (cartRepository.addToCart(productId)) {
                _uiState.value = _uiState.value.copy(cartMessage = "Added to cart")
            }
        }
    }

    fun onBuyNowClicked() {
        runOrAskConfirmation {
            if (cartRepository.buyNow(productId)) {
                _uiState.value = _uiState.value.copy(cartMessage = "Purchase started")
            }
        }
    }

    /** User confirmed they still want to add/buy a part that does not fit. */
    fun confirmIncompatibleAction() {
        val action = pendingAction ?: return
        pendingAction = null
        _uiState.value = _uiState.value.copy(pendingIncompatibleConfirmation = false)
        viewModelScope.launch { action() }
    }

    fun dismissIncompatibleConfirmation() {
        pendingAction = null
        _uiState.value = _uiState.value.copy(pendingIncompatibleConfirmation = false)
    }

    fun consumeCartMessage() {
        _uiState.value = _uiState.value.copy(cartMessage = null)
    }

    /** Runs [action] right away, unless the product is a part that does not fit. */
    private fun runOrAskConfirmation(action: suspend () -> Unit) {
        val product = _uiState.value.product
        val isIncompatiblePart = product is ProductDetail.Part && product.compatibility == PartCompatibility.DOES_NOT_FIT

        if (isIncompatiblePart) {
            pendingAction = action
            _uiState.value = _uiState.value.copy(pendingIncompatibleConfirmation = true)
        } else {
            viewModelScope.launch { action() }
        }
    }
}
