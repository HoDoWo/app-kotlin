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
import com.hodowo.viewmodel.SponsoredPartnersViewModel
import com.hodowo.viewmodel.MyListingsViewModel
import com.hodowo.viewmodel.ProductDetailViewModel
import com.hodowo.viewmodel.ProductViewModel
import com.hodowo.viewmodel.MessagesViewModel
import com.hodowo.viewmodel.NotificationsViewModel
import com.hodowo.viewmodel.ProductForumViewModel
import com.hodowo.viewmodel.ProductReviewsViewModel
import com.hodowo.viewmodel.greetingName
import java.io.File

/**
 * Chooses which AI service the app uses and provides singletons for Auth and Firebase.
 */
object AiServiceProvider {

    val authManager by lazy { AuthManager() }
    val firebaseRepository by lazy { FirebaseRepository() }

    private val tokens by lazy { FirebaseTokenProvider() }

    /**
     * The real cart (Backend#42), shared by the badge, the marketplace screens and the
     * cart screen. Without a backend URL the cart stays empty and refuses changes.
     */
    val cartStore: CartStore by lazy {
        val backendUrl = BuildConfig.BACKEND_URL
        CartStore(
            if (backendUrl.isBlank()) UnavailableCartService
            else RemoteCartService(backendUrl, { tokens.token() })
        )
    }

    /**
     * The listings service, kept as a singleton so edits/mark-as-sold/delete
     * survive navigating away from the screen.
     *
     * Without a backend URL there is no data source, so
     * [UnavailableListingsService] reports an error instead of fake listings.
     */
    val listingsService: ListingsService by lazy {
        val backendUrl = BuildConfig.BACKEND_URL
        if (backendUrl.isNotBlank()) {
            RemoteListingsService(
                backendUrl = backendUrl,
                tokenProvider = FirebaseTokenProvider()::token
            )
        } else {
            UnavailableListingsService()
        }
    }

    /** Messages and offers between buyers and sellers, shared by the inbox and the product pages. */
    val messagesService: MessagesService by lazy {
        val backendUrl = BuildConfig.BACKEND_URL
        if (backendUrl.isBlank()) UnavailableMessagesService
        else RemoteMessagesService(backendUrl, FirebaseTokenProvider()::token)
    }

    /**
     * Marketplace service used to open one product by id (the API product search).
     * Without a backend URL the demo catalogue answers, so the screen still works.
     */
    private fun marketplaceService(): MarketplaceService =
        if (BuildConfig.BACKEND_URL.isBlank()) {
            DemoMarketplaceService()
        } else {
            RemoteMarketplaceService(BuildConfig.BACKEND_URL, { tokens.token() })
        }

    /** Source of the API product search (demo catalogue or the remote `/products` endpoint). */
    private fun productSearchService(): ProductSearchService =
        if (BuildConfig.BACKEND_URL.isBlank()) {
            ProductRepository()
        } else {
            RemoteProductSearchService(BuildConfig.BACKEND_URL, { tokens.token() })
        }

    /** Factory that builds [MessagesViewModel]: inbox, conversations and offers. */
    fun messagesViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                MessagesViewModel(service = messagesService, cartStore = cartStore)
            }
        }

    /** Factory of the bell: messages, offers, price changes in the cart and a sponsored product. */
    fun notificationsViewModelFactory(context: Context): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                val backendUrl = BuildConfig.BACKEND_URL
                NotificationsViewModel(
                    service = if (backendUrl.isBlank()) {
                        UnavailableNotificationsService
                    } else {
                        RemoteNotificationsService(backendUrl, FirebaseTokenProvider()::token)
                    },
                    seenStore = PrefsNotificationSeenStore(
                        context.applicationContext.getSharedPreferences("notifications", Context.MODE_PRIVATE)
                    )
                )
            }
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
                val compressor = ImageCompressor(context.applicationContext)
                ChatViewModel(
                    aiService = create(context),
                    firebaseRepository = firebaseRepository,
                    authManager = authManager,
                    chatHistory = SyncedChatHistoryStore(
                        local = FileChatHistoryStore(
                            directory = File(context.filesDir, "chat_history"),
                            openImage = { uri -> context.applicationContext.contentResolver.openInputStream(uri) }
                        ),
                        remote = FirestoreChatHistory(),
                        uploader = FirebaseChatImageUploader(compress = { uri -> compressor.compress(uri) })
                    ),
                    profilePhotoStore = ProfilePhotoStore(context.applicationContext),
                    encodeProfilePhoto = { uri ->
                        try {
                            val bytes = compressor.compress(uri, maxSide = 512, quality = 75)
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            "data:image/jpeg;base64,$base64"
                        } catch (_: Exception) {
                            null
                        }
                    }
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
                    RemoteMarketplaceService(BuildConfig.BACKEND_URL, FirebaseTokenProvider()::token),
                    cartStore
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

    fun postProductViewModelFactory(context: Context): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                val compressor = ImageCompressor(context.applicationContext)
                PostProductViewModel(
                    service = RemoteMarketplaceService(
                        BuildConfig.BACKEND_URL, FirebaseTokenProvider()::token
                    ),
                    aiService = create(context),
                    loadImage = { uri -> compressor.compress(uri) },
                    listingsService = listingsService
                )
            }
        }

    /**
     * Factory that builds [ProductDetailViewModel] for a product tapped in the marketplace:
     * the page is built from the search result (no detail endpoint yet, Backend#38) and
     * "Add" / "Buy now" use the real cart.
     */
    fun productDetailViewModelFactory(product: MarketplaceProduct): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ProductDetailViewModel(
                    productId = product.id,
                    marketplaceService = SelectedProductService(product),
                    cartRepository = StoreCartRepository(cartStore, product.cartProductType())
                )
            }
        }

    /** Factory for the Product Detail screen of one API search result (loads `/api/marketplace/products/{id}`). */
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

    /** Factory that builds [ProductForumViewModel] (app #150) for one product's questions. */
    fun productForumViewModelFactory(productType: String, productId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ProductForumViewModel(
                    service = RemoteForumService(BuildConfig.BACKEND_URL, FirebaseTokenProvider()::token),
                    productType = productType,
                    productId = productId
                )
            }
        }

    /** Factory of the reviews of one product: read by everyone, written by its buyers. */
    fun productReviewsViewModelFactory(productType: String, productId: String): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ProductReviewsViewModel(
                    service = RemoteReviewsService(BuildConfig.BACKEND_URL, FirebaseTokenProvider()::token),
                    productType = productType,
                    productId = productId
                )
            }
        }

    /** Factory that builds [SponsoredPartnersViewModel] (#141). */
    fun sponsoredPartnersViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                SponsoredPartnersViewModel(
                    RemoteMarketplaceService(BuildConfig.BACKEND_URL, FirebaseTokenProvider()::token)
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
