package com.hodowo.viewmodel

import com.hodowo.data.CartException
import com.hodowo.data.CartRepository
import com.hodowo.data.DemoMarketplaceService
import com.hodowo.data.MarketplaceService
import com.hodowo.data.ProductReviews
import com.hodowo.data.ReviewException
import com.hodowo.data.ReviewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private val NEW = ProductDetail.New(
    id = "new-1", title = "OEM Pump", priceLabel = "$38.50", images = emptyList(), description = null,
    partnerName = "Partner Certified", rating = 4.9, reviewCount = 100,
    specs = emptyList(), matchedAppliance = "WAN28209GB", questions = emptyList(), reviews = emptyList()
)
private val SECOND_HAND = ProductDetail.SecondHand(
    id = "used-1", title = "Airfryer", priceLabel = "$45.00", images = emptyList(), description = null,
    condition = "Like new", location = "Lleida", sellerName = "Marc", sellerRating = 4.7, postedAgo = "4d ago"
)
private val PART_FITS = ProductDetail.Part(
    id = "part-fits", title = "Door Seal", priceLabel = "$22.90", images = emptyList(), description = null,
    sku = "SKU-1", compatibility = PartCompatibility.FITS, compatibilityEvidence = "Matches model", specs = emptyList()
)
private val PART_UNKNOWN = PART_FITS.copy(id = "part-unknown", compatibility = PartCompatibility.UNKNOWN, compatibilityEvidence = null)
private val PART_MISMATCH = PART_FITS.copy(id = "part-mismatch", compatibility = PartCompatibility.DOES_NOT_FIT)

/** Unit tests for [ProductDetailViewModel] (issue #140): the three product variants and the cart actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: FakeMarketplaceService
    private lateinit var cart: FakeCartRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = FakeMarketplaceService()
        cart = FakeCartRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(productId: String, reviews: ReviewsService? = null) =
        ProductDetailViewModel(
            productId = productId, marketplaceService = service, cartRepository = cart, reviewsService = reviews
        )

    // ---------- reviews (#200) ----------

    private val anna = CustomerReview("Anna", 5, true, "Great", "1d ago")

    @Test
    fun theRealReviewsReplaceTheOnesOfTheSearchResult() = runTest(dispatcher) {
        val reviews = FakeReviewsService(ProductReviews(listOf(anna), 4.8, 12))
        val vm = viewModel(NEW.id, reviews)

        vm.load()
        advanceUntilIdle()

        val product = vm.uiState.value.product as ProductDetail.New
        assertEquals(listOf(anna), product.reviews)
        assertEquals(4.8, product.rating, 0.0)
        assertEquals(12, product.reviewCount)
        assertTrue(vm.uiState.value.canReview)
    }

    @Test
    fun onlyOfficialProductsCanBeReviewed() = runTest(dispatcher) {
        val vm = viewModel(SECOND_HAND.id, FakeReviewsService(ProductReviews(emptyList(), null, 0)))

        vm.load()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canReview)
        vm.submitReview(5, "x")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.reviewSent)
    }

    @Test
    fun withoutAReviewsServiceTheProductStaysAsItWas() = runTest(dispatcher) {
        val vm = viewModel(NEW.id)

        vm.load()
        advanceUntilIdle()

        assertEquals(NEW, vm.uiState.value.product)
        assertFalse(vm.uiState.value.canReview)
    }

    @Test
    fun reviewsThatFailToLoadDoNotBreakTheProduct() = runTest(dispatcher) {
        val reviews = FakeReviewsService(ProductReviews(emptyList(), null, 0)).apply { error = IOException("offline") }
        val vm = viewModel(NEW.id, reviews)

        vm.load()
        advanceUntilIdle()

        assertEquals(NEW, vm.uiState.value.product)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun sendingAReviewPostsItAndReloadsTheList() = runTest(dispatcher) {
        val reviews = FakeReviewsService(ProductReviews(emptyList(), null, 0))
        val vm = viewModel(NEW.id, reviews)
        vm.load()
        advanceUntilIdle()
        reviews.current = ProductReviews(listOf(anna), 5.0, 1)

        vm.submitReview(5, "  Great  ")
        advanceUntilIdle()

        assertEquals(listOf(Triple(NEW.id, 5, "Great")), reviews.sent)
        val state = vm.uiState.value
        assertTrue(state.reviewSent)
        assertFalse(state.isSubmittingReview)
        assertEquals(listOf(anna), (state.product as ProductDetail.New).reviews)
        vm.consumeReviewSent()
        assertFalse(vm.uiState.value.reviewSent)
    }

    @Test
    fun aRatingOutsideOneToFiveIsNotSent() = runTest(dispatcher) {
        val reviews = FakeReviewsService(ProductReviews(emptyList(), null, 0))
        val vm = viewModel(NEW.id, reviews)
        vm.load()
        advanceUntilIdle()

        vm.submitReview(0, "x")
        vm.submitReview(6, "x")
        advanceUntilIdle()

        assertTrue(reviews.sent.isEmpty())
        assertEquals("Choose from 1 to 5 stars", vm.uiState.value.reviewError)
    }

    @Test
    fun aRefusedReviewShowsTheReasonAndCanBeRetried() = runTest(dispatcher) {
        val reviews = FakeReviewsService(ProductReviews(emptyList(), null, 0))
        val vm = viewModel(NEW.id, reviews)
        vm.load()
        advanceUntilIdle()
        reviews.sendError = ReviewException("product not found")

        vm.submitReview(4, "")
        advanceUntilIdle()

        assertEquals("product not found", vm.uiState.value.reviewError)
        assertFalse(vm.uiState.value.reviewSent)
        assertFalse(vm.uiState.value.isSubmittingReview)

        reviews.sendError = null
        vm.submitReview(4, "")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.reviewSent)
        assertNull(vm.uiState.value.reviewError)
    }

    // ---------- load ----------

    @Test
    fun loadingShowsTheNewProductVariant() = runTest(dispatcher) {
        val vm = viewModel(NEW.id)

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(NEW, state.product)
        assertNull(state.error)
    }

    @Test
    fun loadingShowsTheSecondHandVariant() = runTest(dispatcher) {
        val vm = viewModel(SECOND_HAND.id)

        vm.load()
        advanceUntilIdle()

        assertEquals(SECOND_HAND, vm.uiState.value.product)
    }

    @Test
    fun loadingShowsThePartVariant() = runTest(dispatcher) {
        val vm = viewModel(PART_FITS.id)

        vm.load()
        advanceUntilIdle()

        assertEquals(PART_FITS, vm.uiState.value.product)
    }

    @Test
    fun aFailedLoadReportsTheError() = runTest(dispatcher) {
        service.error = IOException("offline")
        val vm = viewModel(NEW.id)

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.product)
        assertEquals("offline", state.error)
    }

    // ---------- add to cart / buy: compatible products ----------

    @Test
    fun addingANewProductToCartCallsTheRepositoryDirectly() = runTest(dispatcher) {
        val vm = viewModel(NEW.id)
        vm.load()
        advanceUntilIdle()

        vm.onAddToCartClicked()
        advanceUntilIdle()

        assertEquals(listOf(NEW.id), cart.added)
        assertFalse(vm.uiState.value.pendingIncompatibleConfirmation)
        assertEquals("Added to cart", vm.uiState.value.cartMessage)
    }

    @Test
    fun buyingASecondHandListingCallsBuyNowDirectly() = runTest(dispatcher) {
        val vm = viewModel(SECOND_HAND.id)
        vm.load()
        advanceUntilIdle()

        vm.onBuyNowClicked()
        advanceUntilIdle()

        assertEquals(listOf(SECOND_HAND.id), cart.bought)
        assertFalse(vm.uiState.value.pendingIncompatibleConfirmation)
        assertTrue(vm.uiState.value.openCart)

        vm.onCartOpened()
        assertFalse(vm.uiState.value.openCart)
    }

    @Test
    fun aCartRefusalIsShownInsteadOfCrashing() = runTest(dispatcher) {
        cart.error = CartException("already sold")
        val vm = viewModel(SECOND_HAND.id)
        vm.load()
        advanceUntilIdle()

        vm.onAddToCartClicked()
        advanceUntilIdle()
        assertEquals("Could not add to cart: already sold", vm.uiState.value.cartMessage)

        vm.onBuyNowClicked()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.openCart)
    }

    @Test
    fun aNetworkErrorWhileAddingAsksToCheckTheConnection() = runTest(dispatcher) {
        cart.error = IOException("timeout")
        val vm = viewModel(NEW.id)
        vm.load()
        advanceUntilIdle()

        vm.onAddToCartClicked()
        advanceUntilIdle()

        assertEquals("Could not add to cart, check your connection", vm.uiState.value.cartMessage)
    }

    @Test
    fun addingACompatiblePartCallsTheRepositoryDirectly() = runTest(dispatcher) {
        val vm = viewModel(PART_FITS.id)
        vm.load()
        advanceUntilIdle()

        vm.onAddToCartClicked()
        advanceUntilIdle()

        assertEquals(listOf(PART_FITS.id), cart.added)
        assertFalse(vm.uiState.value.pendingIncompatibleConfirmation)
    }

    @Test
    fun addingAnUnknownCompatibilityPartCallsTheRepositoryDirectly() = runTest(dispatcher) {
        val vm = viewModel(PART_UNKNOWN.id)
        vm.load()
        advanceUntilIdle()

        vm.onAddToCartClicked()
        advanceUntilIdle()

        assertEquals(listOf(PART_UNKNOWN.id), cart.added)
        assertFalse(vm.uiState.value.pendingIncompatibleConfirmation)
    }

    // ---------- add to cart / buy: incompatible part needs confirmation ----------

    @Test
    fun addingAnIncompatiblePartAsksForConfirmationFirst() = runTest(dispatcher) {
        val vm = viewModel(PART_MISMATCH.id)
        vm.load()
        advanceUntilIdle()

        vm.onAddToCartClicked()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pendingIncompatibleConfirmation)
        assertTrue(cart.added.isEmpty())
    }

    @Test
    fun confirmingAddsTheIncompatiblePartToTheCart() = runTest(dispatcher) {
        val vm = viewModel(PART_MISMATCH.id)
        vm.load()
        advanceUntilIdle()
        vm.onAddToCartClicked()
        advanceUntilIdle()

        vm.confirmIncompatibleAction()
        advanceUntilIdle()

        assertEquals(listOf(PART_MISMATCH.id), cart.added)
        assertFalse(vm.uiState.value.pendingIncompatibleConfirmation)
    }

    @Test
    fun dismissingTheConfirmationDoesNotTouchTheCart() = runTest(dispatcher) {
        val vm = viewModel(PART_MISMATCH.id)
        vm.load()
        advanceUntilIdle()
        vm.onAddToCartClicked()
        advanceUntilIdle()

        vm.dismissIncompatibleConfirmation()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.pendingIncompatibleConfirmation)
        assertTrue(cart.added.isEmpty())
    }

    @Test
    fun buyingAnIncompatiblePartAlsoAsksForConfirmation() = runTest(dispatcher) {
        val vm = viewModel(PART_MISMATCH.id)
        vm.load()
        advanceUntilIdle()

        vm.onBuyNowClicked()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pendingIncompatibleConfirmation)
        assertTrue(cart.bought.isEmpty())
    }

    // ---------- consumeCartMessage ----------

    // ---------- default dependencies ----------

    @Test
    fun withoutExplicitDependenciesItUsesTheDemoServiceAndInMemoryCart() = runTest(dispatcher) {
        val vm = ProductDetailViewModel(productId = DemoMarketplaceService.NEW_PRODUCT.id)

        vm.load()
        advanceUntilIdle()

        assertEquals(DemoMarketplaceService.NEW_PRODUCT, vm.uiState.value.product)

        vm.onAddToCartClicked()
        advanceUntilIdle()

        assertEquals("Added to cart", vm.uiState.value.cartMessage)
    }

    @Test
    fun consumingTheCartMessageClearsIt() = runTest(dispatcher) {
        val vm = viewModel(NEW.id)
        vm.load()
        advanceUntilIdle()
        vm.onAddToCartClicked()
        advanceUntilIdle()

        vm.consumeCartMessage()

        assertNull(vm.uiState.value.cartMessage)
    }
}

/** Fake marketplace backend: serves a fixed set of products by id. */
private class FakeMarketplaceService : MarketplaceService {
    var error: Exception? = null

    private val samples: Map<String, ProductDetail> =
        listOf(NEW, SECOND_HAND, PART_FITS, PART_UNKNOWN, PART_MISMATCH).associateBy { it.id }

    override suspend fun recommendedDemoIds(): List<String> = samples.keys.toList()

    override suspend fun getProductDetail(id: String): ProductDetail {
        error?.let { throw it }
        return samples[id] ?: throw IOException("Unknown product id: $id")
    }
}

/** In-memory [CartRepository] fake that records every call. */
private class FakeCartRepository : CartRepository {
    val added = mutableListOf<String>()
    val bought = mutableListOf<String>()
    var error: IOException? = null

    override suspend fun addToCart(productId: String, quantity: Int): Boolean {
        error?.let { throw it }
        added.add(productId)
        return true
    }

    override suspend fun buyNow(productId: String): Boolean {
        error?.let { throw it }
        bought.add(productId)
        return true
    }
}

/** In-memory [ReviewsService] that records what was sent. */
private class FakeReviewsService(var current: ProductReviews) : ReviewsService {
    var error: IOException? = null
    var sendError: IOException? = null
    val sent = mutableListOf<Triple<String, Int, String>>()

    override suspend fun loadReviews(productId: String): ProductReviews {
        error?.let { throw it }
        return current
    }

    override suspend fun submitReview(productId: String, rating: Int, body: String) {
        sendError?.let { throw it }
        sent.add(Triple(productId, rating, body))
    }
}
