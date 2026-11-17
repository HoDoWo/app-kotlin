package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.CartException
import com.hodowo.data.CartRepository
import com.hodowo.data.DemoMarketplaceService
import com.hodowo.data.InMemoryCartRepository
import com.hodowo.data.MarketplaceService
import com.hodowo.data.ReviewsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Everything the Product Detail screen shows. */
data class ProductDetailUiState(
    val isLoading: Boolean = true,
    val product: ProductDetail? = null,
    val error: String? = null,
    /** True while a part add-to-cart/buy needs the user to confirm the compatibility mismatch. */
    val pendingIncompatibleConfirmation: Boolean = false,
    /** One-off "Added to cart"/"Purchase started" feedback; cleared by [ProductDetailViewModel.consumeCartMessage]. */
    val cartMessage: String? = null,
    /** "Buy now" put the product in the cart: the screen opens the cart; cleared by [ProductDetailViewModel.onCartOpened]. */
    val openCart: Boolean = false,
    /** Official products can be reviewed (app #200): shows the "Write a review" button. */
    val canReview: Boolean = false,
    val isSubmittingReview: Boolean = false,
    /** A review was sent: the screen closes its form; cleared by [ProductDetailViewModel.consumeReviewSent]. */
    val reviewSent: Boolean = false,
    /** Why the last review could not be sent; cleared by [ProductDetailViewModel.consumeReviewError]. */
    val reviewError: String? = null
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
    private val cartRepository: CartRepository = InMemoryCartRepository(),
    /** Real customer reviews of official products (#200); null keeps the demo / search data. */
    private val reviewsService: ReviewsService? = null
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
                _uiState.value.copy(
                    isLoading = false,
                    product = withReviews(product),
                    canReview = reviewsService != null && product is ProductDetail.New
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value.copy(isLoading = false, error = e.message ?: "Could not load this product")
            }
        }
    }

    /** The product with its real reviews; without them (or when they fail to load) it stays as it was. */
    private suspend fun withReviews(product: ProductDetail): ProductDetail {
        val service = reviewsService
        if (service == null || product !is ProductDetail.New) return product
        return try {
            val loaded = service.loadReviews(product.id)
            product.copy(
                reviews = loaded.reviews,
                rating = loaded.rating ?: product.rating,
                reviewCount = loaded.reviewCount
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            product
        }
    }

    /** Sends the signed-in user's review (1-5 stars, optional text) and shows it. */
    fun submitReview(rating: Int, body: String) {
        val service = reviewsService ?: return
        if (!_uiState.value.canReview || _uiState.value.isSubmittingReview) return
        if (rating !in MIN_STARS..MAX_STARS) {
            _uiState.value = _uiState.value.copy(reviewError = "Choose from 1 to 5 stars")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingReview = true, reviewError = null)
            _uiState.value = try {
                service.submitReview(productId, rating, body.trim())
                val product = _uiState.value.product?.let { withReviews(it) }
                _uiState.value.copy(isSubmittingReview = false, product = product, reviewSent = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.value.copy(
                    isSubmittingReview = false,
                    reviewError = e.message ?: "Could not send your review, check your connection"
                )
            }
        }
    }

    fun consumeReviewSent() {
        _uiState.value = _uiState.value.copy(reviewSent = false)
    }

    fun consumeReviewError() {
        _uiState.value = _uiState.value.copy(reviewError = null)
    }

    fun onAddToCartClicked() {
        runOrAskConfirmation {
            reportCartErrors {
                if (cartRepository.addToCart(productId)) {
                    _uiState.value = _uiState.value.copy(cartMessage = "Added to cart")
                }
            }
        }
    }

    fun onBuyNowClicked() {
        runOrAskConfirmation {
            reportCartErrors {
                if (cartRepository.buyNow(productId)) {
                    _uiState.value = _uiState.value.copy(openCart = true)
                }
            }
        }
    }

    fun onCartOpened() {
        _uiState.value = _uiState.value.copy(openCart = false)
    }

    /** The real cart (#142) refuses sold or out-of-stock items: show its reason instead of crashing. */
    private suspend fun reportCartErrors(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CartException) {
            _uiState.value = _uiState.value.copy(cartMessage = "Could not add to cart: ${e.message}")
        } catch (e: IOException) {
            _uiState.value = _uiState.value.copy(cartMessage = "Could not add to cart, check your connection")
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

    private companion object {
        const val MIN_STARS = 1
        const val MAX_STARS = 5
    }
}
