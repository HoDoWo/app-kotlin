package com.hodowo.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hodowo.BuildConfig
import com.hodowo.viewmodel.CartViewModel
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.CheckoutViewModel
import com.hodowo.viewmodel.DemoHoDoWoAiService
import com.hodowo.viewmodel.HoDoWoAiService
import com.hodowo.viewmodel.HomeViewModel
import com.hodowo.viewmodel.MarketplaceViewModel
import com.hodowo.viewmodel.PostProductViewModel
import com.hodowo.viewmodel.MyListingsViewModel
import com.hodowo.viewmodel.ProductDetailViewModel
import com.hodowo.viewmodel.greetingName
import java.io.File

/**
 * Chooses which AI service the app uses and provides singletons for Auth and Firebase.
 */
object AiServiceProvider {

    val authManager by lazy { AuthManager() }
    val firebaseRepository by lazy { FirebaseRepository() }
    /** Cart of the Product Detail screen, which still shows demo products (Backend#38). */
    val cartRepository by lazy { InMemoryCartRepository() }

    /**
     * The real cart (Backend#42), shared by the badge, the marketplace screens and the
     * cart screen. Without a backend URL the cart stays empty and refuses changes.
     */
    val cartStore by lazy {
        val backendUrl = BuildConfig.BACKEND_URL
        CartStore(
            if (backendUrl.isBlank()) UnavailableCartService
            else RemoteCartService(backendUrl, FirebaseTokenProvider()::token)
        )
    }

    /**
     * The listings service, kept as a singleton so edits/mark-as-sold/delete done through
     * [DemoListingsService]'s in-memory data survive navigating away from the screen.
     *
     * Like [marketplaceService], this doesn't switch on [BuildConfig.BACKEND_URL] yet: the
     * marketplace routes (Backend#38, Backend#40) aren't deployed. Switch to
     * [RemoteListingsService] once they are.
     */
    val listingsService: ListingsService by lazy { DemoListingsService() }

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

    /**
     * The marketplace service.
     *
     * Unlike [create], this does not switch on [BuildConfig.BACKEND_URL]: that URL only
     * means the AI chat backend is deployed, not that its marketplace routes exist yet
     * (Backend#38, Backend#40 are not shipped). [RemoteMarketplaceService] is ready to use
     * once they are: switch the line below when that happens.
     */
    fun marketplaceService(): MarketplaceService = DemoMarketplaceService()

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

    fun marketplaceViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                MarketplaceViewModel(
                    MarketplaceService(BuildConfig.BACKEND_URL, FirebaseTokenProvider()::token),
                    cartStore
                )
            }
        }

    fun postProductViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                PostProductViewModel(
                    MarketplaceService(BuildConfig.BACKEND_URL, FirebaseTokenProvider()::token),
                    firebaseRepository
                )
            }
        }

    /** Factory that builds [ProductDetailViewModel] for the given product id. */
    fun productDetailViewModelFactory(productId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ProductDetailViewModel(
                    productId = productId,
                    marketplaceService = marketplaceService(),
                    cartRepository = cartRepository
                )
            }
        }

    /** Factory that builds [CartViewModel] on the shared [cartStore]. */
    fun cartViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CartViewModel(cartStore)
            }
        }

    /**
     * Factory that builds [CheckoutViewModel] (#143): orders from the backend checkout,
     * payment in the Stripe PaymentSheet with the publishable test key of local.properties.
     */
    fun checkoutViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                CheckoutViewModel(
                    checkout = RemoteCheckoutService(BuildConfig.BACKEND_URL, FirebaseTokenProvider()::token),
                    cart = cartStore,
                    publishableKey = BuildConfig.STRIPE_PUBLISHABLE_KEY
                )
            }
        }

    /** Factory that builds [MyListingsViewModel] with the shared listings service. */
    fun myListingsViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                MyListingsViewModel(service = listingsService)
            }
        }
}