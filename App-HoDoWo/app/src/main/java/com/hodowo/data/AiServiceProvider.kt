package com.hodowo.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hodowo.BuildConfig
import com.hodowo.data.ProductRepository
import com.hodowo.viewmodel.CartViewModel
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.CheckoutViewModel
import com.hodowo.viewmodel.DemoHoDoWoAiService
import com.hodowo.viewmodel.HoDoWoAiService
import com.hodowo.viewmodel.HomeViewModel
import com.hodowo.viewmodel.MarketplaceViewModel
import com.hodowo.viewmodel.ProductDetailViewModel
import com.hodowo.viewmodel.ProductViewModel
import com.hodowo.viewmodel.greetingName
import java.io.File

/**
 * Chooses which AI service the app uses and provides singletons for Auth and Firebase.
 */
object AiServiceProvider {

    val authManager by lazy { AuthManager() }
    val firebaseRepository by lazy { FirebaseRepository() }

    private val tokens by lazy { FirebaseTokenProvider() }

    /** Shared cart state (Backend#42 cart API), so every screen shows the same badge. */
    val cartStore: CartStore by lazy {
        val service = if (BuildConfig.BACKEND_URL.isBlank()) {
            UnavailableCartService
        } else {
            RemoteCartService(BuildConfig.BACKEND_URL, { tokens.token() })
        }
        CartStore(service)
    }

    private fun marketplaceService(): MarketplaceService =
        if (BuildConfig.BACKEND_URL.isBlank()) {
            DemoMarketplaceService()
        } else {
            RemoteMarketplaceService(BuildConfig.BACKEND_URL, { tokens.token() })
        }

    private fun productSearchService(): ProductSearchService =
        if (BuildConfig.BACKEND_URL.isBlank()) {
            ProductRepository()
        } else {
            RemoteProductSearchService(BuildConfig.BACKEND_URL, { tokens.token() })
        }

    private fun checkoutService(): CheckoutService =
        if (BuildConfig.BACKEND_URL.isBlank()) {
            object : CheckoutService {
                override suspend fun startCheckout(selection: CartSelection): CheckoutSession =
                    throw CheckoutException("Checkout is not available right now")
            }
        } else {
            RemoteCheckoutService(BuildConfig.BACKEND_URL, { tokens.token() })
        }

    fun create(context: Context): HoDoWoAiService {
        val backendUrl = BuildConfig.BACKEND_URL

        if (backendUrl.isBlank()) {
            return DemoHoDoWoAiService()
        }

        val compressor = ImageCompressor(context.applicationContext)
        val tokens = FirebaseTokenProvider()

        return RemoteHoDoWoAiService(
            backendUrl = backendUrl,
            loadImage = { uri -> compressor.compress(uri) },
            tokenProvider = { tokens.token() }
        )
    }

    /** Factory that builds [ChatViewModel] with the service and Firebase repos. */
    fun chatViewModelFactory(context: Context): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ChatViewModel(
                    aiService = create(context),
                    firebaseRepository = firebaseRepository,
                    authManager = authManager,
                    chatHistory = FileChatHistoryStore(
                        directory = File(context.filesDir, "chat_history"),
                        openImage = { uri -> context.applicationContext.contentResolver.openInputStream(uri) }
                    ),
                    profilePhotoStore = ProfilePhotoStore(context.applicationContext)
                )
            }
        }

    /** Factory that builds [HomeViewModel]: same AI service, name of the signed-in user. */
    fun homeViewModelFactory(context: Context): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                HomeViewModel(
                    aiService = create(context),
                    userName = {
                        val user = authManager.currentUser.value
                        greetingName(user?.displayName, user?.email)
                    },
                    cache = FileManualsCache(File(context.filesDir, "home_cache")),
                    userId = { authManager.currentUserId }
                )
            }
        }

    /** Factory for the Marketplace Home screen (partner deals + community items). */
    fun marketplaceViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                MarketplaceViewModel(
                    service = marketplaceService(),
                    cart = cartStore
                )
            }
        }

    /** Factory that builds [ProductViewModel] backed by the marketplace products endpoint. */
    fun productViewModelFactory(context: Context): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ProductViewModel(
                    repository = productSearchService(),
                    cart = cartStore
                )
            }
        }

    /** Factory for the Product Detail screen of one product (loads `/api/marketplace/products/{id}`). */
    fun productDetailViewModelFactory(
        productId: String,
        productType: String? = null
    ): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ProductDetailViewModel(
                    productId = productId,
                    marketplaceService = marketplaceService(),
                    cartRepository = StoreCartRepository(cartStore, productType)
                )
            }
        }

    /** Factory for the full cart screen (Backend#42 cart API). */
    fun cartViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { CartViewModel(cartStore) }
        }

    /** Factory for the checkout (Stripe PaymentSheet, Backend#43). */
    fun checkoutViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CheckoutViewModel(
                    checkout = checkoutService(),
                    cart = cartStore,
                    publishableKey = BuildConfig.STRIPE_PUBLISHABLE_KEY
                )
            }
        }
}
