package com.hodowo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hodowo.BuildConfig
import com.hodowo.data.AiServiceProvider
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.cartProductType
import com.hodowo.data.model.toMarketplaceProduct as searchResultToMarketplaceProduct
import com.hodowo.data.toMarketplaceProduct
import com.hodowo.data.AuthManager
import com.hodowo.data.auth.FirebaseAuthRepository
import com.hodowo.data.auth.GoogleSignInHelper
import com.hodowo.viewmodel.AuthViewModel
import com.hodowo.viewmodel.Listing
import com.hodowo.viewmodel.MyListingsViewModel
import com.hodowo.viewmodel.OrdersViewModel
import com.hodowo.viewmodel.ProductDetailViewModel
import com.hodowo.viewmodel.MessagesViewModel
import com.hodowo.viewmodel.NotificationsViewModel
import com.hodowo.data.AppNotification
import com.hodowo.data.NotificationTarget
import com.hodowo.viewmodel.ProductForumViewModel
import com.hodowo.viewmodel.ProductReviewsViewModel
import com.hodowo.ui.adaptive.LocalAppLayout
import com.hodowo.ui.adaptive.appLayoutInfoFor
import com.hodowo.ui.components.AppNavTab
import com.hodowo.ui.components.AppNavigationRail
import com.hodowo.ui.components.BottomNavBar
import com.hodowo.ui.components.LocalNotificationBell
import com.hodowo.ui.components.NotificationBell
import com.hodowo.ui.components.NotificationIconButton
import com.hodowo.ui.components.ProfileAvatar
import com.hodowo.ui.components.NewChatButton
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.viewmodel.CartViewModel
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.CheckoutViewModel
import com.hodowo.viewmodel.HomeViewModel
import com.hodowo.viewmodel.MarketplaceViewModel
import com.hodowo.viewmodel.ProductViewModel
import com.hodowo.viewmodel.PostProductViewModel
import com.hodowo.viewmodel.SponsoredPartnersViewModel

/**
 * Root composable of the application.
 */
@Composable
fun HoDoWoApp(
    chatViewModel: ChatViewModel = viewModel(
        factory = AiServiceProvider.chatViewModelFactory(
            LocalContext.current.applicationContext
        )
    ),
    homeViewModel: HomeViewModel = viewModel(
        factory = AiServiceProvider.homeViewModelFactory(
            LocalContext.current.applicationContext
        )
    ),
    authManager: AuthManager = AiServiceProvider.authManager,
    authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthViewModel(FirebaseAuthRepository()) }
        }
    )
) {

    val context = LocalContext.current

    // The auth state of AuthManager is the single source of truth: it already
    // follows FirebaseAuth (restored session, email login, Google, sign out).
    // AuthViewModel only owns the login form.
    val currentUser by authManager.currentUser.collectAsState()
    val isSignedIn = currentUser != null && !authManager.isAnonymous

    // Nothing else is shown until the user is signed in (#54).
    if (!isSignedIn) {
        AuthRoute(
            viewModel = authViewModel,
            googleSignIn = remember { GoogleSignInHelper(BuildConfig.GOOGLE_WEB_CLIENT_ID) },
            context = context
        )
        return
    }
    val marketplaceViewModel: MarketplaceViewModel = viewModel(factory = AiServiceProvider.marketplaceViewModelFactory())
    val postProductViewModel: PostProductViewModel = viewModel(
        factory = AiServiceProvider.postProductViewModelFactory(context)
    )
    val cartViewModel: CartViewModel = viewModel(factory = AiServiceProvider.cartViewModelFactory())
    val checkoutViewModel: CheckoutViewModel = viewModel(factory = AiServiceProvider.checkoutViewModelFactory())
    val sponsoredPartnersViewModel: SponsoredPartnersViewModel = viewModel(
        factory = AiServiceProvider.sponsoredPartnersViewModelFactory()
    )
    val productViewModel: ProductViewModel = viewModel(factory = AiServiceProvider.productViewModelFactory())
    val myListingsViewModel: MyListingsViewModel = viewModel(factory = AiServiceProvider.myListingsViewModelFactory())
    var currentTab by rememberSaveable { mutableStateOf(AppNavTab.HOME) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    val chatState by chatViewModel.uiState.collectAsState()
    var showAuthDialog by rememberSaveable { mutableStateOf(false) }
    var showApplianceManuals by rememberSaveable { mutableStateOf(false) }
    var showPostProduct by rememberSaveable { mutableStateOf(false) }
    var showMarketplaceSearch by rememberSaveable { mutableStateOf(false) }
    var showMyListings by rememberSaveable { mutableStateOf(false) }
    // A listing of "My listings" open in the 4 steps of the sell form.
    var editingListing by remember { mutableStateOf<Listing?>(null) }
    var showSponsored by rememberSaveable { mutableStateOf(false) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    // Order confirmation after paying and the purchase history in the profile (#144).
    val ordersViewModel: OrdersViewModel = viewModel(factory = AiServiceProvider.ordersViewModelFactory())
    var showOrderConfirmation by rememberSaveable { mutableStateOf(false) }
    var showOrders by rememberSaveable { mutableStateOf(false) }
    // Messages and offers with buyers and sellers of second-hand listings.
    var showMessages by rememberSaveable { mutableStateOf(false) }
    val messagesViewModel: MessagesViewModel = viewModel(factory = AiServiceProvider.messagesViewModelFactory())
    val messagesState by messagesViewModel.uiState.collectAsState()
    // The unread badge on the marketplace: refreshed every time the marketplace is shown.
    LaunchedEffect(currentTab) {
        if (currentTab == AppNavTab.MARKET) messagesViewModel.loadInbox()
    }
    // The bell: messages, offers, price changes in the cart and a sponsored product.
    val notificationsViewModel: NotificationsViewModel = viewModel(
        factory = AiServiceProvider.notificationsViewModelFactory(context)
    )
    val notificationsState by notificationsViewModel.uiState.collectAsState()
    // Checked again every 15 seconds while the app is open, and at once when the section
    // changes, so the bell follows new messages, offers and sponsored products.
    LaunchedEffect(currentTab) {
        while (true) {
            notificationsViewModel.load()
            delay(NOTIFICATIONS_REFRESH_MS)
        }
    }
    // Product opened from a marketplace card: its Product Detail page (#140) with the forum (#150).
    var selectedMarketProduct by remember { mutableStateOf<MarketplaceProduct?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    /** "New chat" button of every section: starts an empty chat and shows it. */
    fun startNewChat() {
        chatViewModel.startNewChat()
        currentTab = AppNavTab.AI_CHAT
    }

    fun openCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            showCamera = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /** Opens what a notification is about, from any section. */
    fun openNotification(notification: AppNotification) {
        notificationsViewModel.close()
        val target = notification.target ?: return
        showApplianceManuals = false
        showMarketplaceSearch = false
        showPostProduct = false
        showMyListings = false
        editingListing = null
        showSponsored = false
        showOrders = false
        showOrderConfirmation = false
        showCart = false
        showMessages = false
        selectedMarketProduct = null
        currentTab = AppNavTab.MARKET
        when (target) {
            is NotificationTarget.Conversation -> {
                messagesViewModel.openConversation(target.id)
                showMessages = true
            }
            NotificationTarget.Cart -> showCart = true
            is NotificationTarget.Product -> selectedMarketProduct = target.product
            NotificationTarget.MyListings -> {
                showMyListings = true
                currentTab = AppNavTab.PROFILE
            }
        }
    }

    if (notificationsState.isOpen) {
        NotificationsSheet(
            state = notificationsState,
            onOpen = ::openNotification,
            onDismiss = notificationsViewModel::close
        )
    }

    CompositionLocalProvider(
        LocalNotificationBell provides NotificationBell(notificationsState.badge, notificationsViewModel::open)
    ) {
        if (showCamera) {
            BackHandler {
                showCamera = false
            }
            CameraScreen(
                onImageCaptured = { uri ->
                    showCamera = false
                    currentTab = AppNavTab.AI_CHAT
                    chatViewModel.setSelectedImage(uri)
                },
                onClose = {
                    showCamera = false
                }
            )
        } else {
            // The window size decides between a vertical navigation rail (landscape
            // and tablets) and the bottom tab bar (portrait phones).
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val layoutInfo = appLayoutInfoFor(width = maxWidth, isLandscape = isLandscape)
                val useRail = layoutInfo.useNavigationRail && !showPostProduct && editingListing == null

                CompositionLocalProvider(LocalAppLayout provides layoutInfo) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useRail) {
                            AppNavigationRail(
                                modifier = Modifier.width(80.dp),
                                currentTab = currentTab,
                                onTabSelected = {
                                    showApplianceManuals = false
                                    showMarketplaceSearch = false
                                    showPostProduct = false
                                    showMyListings = false
                                    editingListing = null
                                    showSponsored = false
                                    showOrders = false
                                    showOrderConfirmation = false
                                    showCart = false
                                    showMessages = false
                                    selectedMarketProduct = null
                                    currentTab = it
                                },
                                bottomActions = {
                                    NotificationIconButton()
                                    ProfileAvatar(
                                        name = "",
                                        size = 34,
                                        onClick = {
                                            showApplianceManuals = false
                                            showMarketplaceSearch = false
                                            showPostProduct = false
                                            showMyListings = false
                                            showSponsored = false
                                            showOrders = false
                                            showOrderConfirmation = false
                                            showCart = false
                                            selectedMarketProduct = null
                                            currentTab = AppNavTab.PROFILE
                                        },
                                        photoUrl = chatState.userProfile?.photoUrl
                                    )
                                }
                            )
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (currentTab) {
                                    AppNavTab.HOME -> {
                                        HomeScreen(
                                            viewModel = homeViewModel,
                                            onSearch = { query ->
                                                currentTab = AppNavTab.AI_CHAT
                                                chatViewModel.sendTextMessage(query)
                                            },
                                            onDiagnose = ::openCamera,
                                            onOpenArCore = { currentTab = AppNavTab.AR_CORE },
                                            onOpenMarket = { currentTab = AppNavTab.MARKET },
                                            onOpenProfile = { currentTab = AppNavTab.PROFILE },
                                            onOpenMyListings = {
                                                showMyListings = true
                                                currentTab = AppNavTab.PROFILE
                                            },
                                            onOpenSponsored = {
                                                showSponsored = true
                                                currentTab = AppNavTab.MARKET
                                            },
                                            onAskAboutManual = { guide ->
                                                currentTab = AppNavTab.AI_CHAT
                                                chatViewModel.sendTextMessage(
                                                    "I need help with \"${guide.issueTitle}\" on my ${guide.applianceName}."
                                                )
                                            },
                                            onOpenManualChat = { guide ->
                                                currentTab = AppNavTab.AI_CHAT
                                                chatViewModel.openChatForGuide(guide)
                                            },
                                            profilePhotoUrl = chatState.userProfile?.photoUrl
                                        )
                                    }
                                    AppNavTab.AI_CHAT -> {
                                        ChatScreen(
                                            viewModel = chatViewModel,
                                            onOpenCamera = ::openCamera,
                                            onOpenAuth = { showAuthDialog = true },
                                            // "Buy part" of the part the diagnosis suggests (#30): its Product Detail page.
                                            onBuyPart = { part ->
                                                showCart = false
                                                showMessages = false
                                                currentTab = AppNavTab.MARKET
                                                selectedMarketProduct = part.toMarketplaceProduct()
                                            }
                                        )
                                    }
                                    AppNavTab.PROFILE -> {
                                        if (showOrders) {
                                            BackHandler { showOrders = false }
                                            PurchaseHistoryScreen(
                                                viewModel = ordersViewModel,
                                                onBack = { showOrders = false },
                                                onBrowse = {
                                                    showOrders = false
                                                    currentTab = AppNavTab.MARKET
                                                    showMarketplaceSearch = true
                                                },
                                                onOpenItem = { item, order ->
                                                    showOrders = false
                                                    currentTab = AppNavTab.MARKET
                                                    selectedMarketProduct = item.toMarketplaceProduct(order.sellerName)
                                                }
                                            )
                                        } else if (showApplianceManuals) {
                                            ApplianceManualsScreen(
                                                viewModel = chatViewModel,
                                                onBack = { showApplianceManuals = false },
                                                onNavigateAiChat = {
                                                    showApplianceManuals = false
                                                    currentTab = AppNavTab.AI_CHAT
                                                }
                                            )
                                        } else if (showMyListings && editingListing != null) {
                                            val listing = editingListing
                                            BackHandler { editingListing = null }
                                            PostProductScreen(
                                                viewModel = postProductViewModel,
                                                userId = authManager.currentUserId,
                                                onPublished = {
                                                    editingListing = null
                                                    myListingsViewModel.load()
                                                },
                                                onBack = { editingListing = null },
                                                onOpenCart = {
                                                    editingListing = null
                                                    currentTab = AppNavTab.MARKET
                                                    showCart = true
                                                },
                                                editing = listing
                                            )
                                        } else if (showMyListings) {
                                            MyListingsScreen(
                                                viewModel = myListingsViewModel,
                                                onBack = { showMyListings = false },
                                                onPostProduct = {
                                                    showMyListings = false
                                                    currentTab = AppNavTab.MARKET
                                                    showPostProduct = true
                                                },
                                                onOpenListing = { listing ->
                                                    showMyListings = false
                                                    currentTab = AppNavTab.MARKET
                                                    selectedMarketProduct = listing.toMarketplaceProduct()
                                                },
                                                onEditListing = { editingListing = it }
                                            )
                                        } else {
                                            ProfileScreen(
                                                authManager = authManager,
                                                viewModel = chatViewModel,
                                                myListingsViewModel = myListingsViewModel,
                                                onOpenAuth = { showAuthDialog = true },
                                                onNavigateHome = { currentTab = AppNavTab.HOME },
                                                onNavigateAiChat = { currentTab = AppNavTab.AI_CHAT },
                                                onNavigateMarket = { currentTab = AppNavTab.MARKET },
                                                onNavigateArCore = { currentTab = AppNavTab.AR_CORE },
                                                onNavigateProfile = { currentTab = AppNavTab.PROFILE },
                                                onOpenApplianceManuals = { showApplianceManuals = true },
                                                onOpenMyListings = { showMyListings = true },
                                                onOpenOrders = { showOrders = true },
                                                onPostProduct = {
                                                    currentTab = AppNavTab.MARKET
                                                    showPostProduct = true
                                                },
                                                onSignedOut = {
                                                    // Back to the login screen owned by AuthViewModel.
                                                    authViewModel.signOut()
                                                }
                                            )
                                        }
                                    }
                                    AppNavTab.MARKET -> {
                                        val product = selectedMarketProduct
                                        if (showOrderConfirmation) {
                                            fun leaveConfirmation() {
                                                showOrderConfirmation = false
                                                showCart = false
                                                ordersViewModel.closeConfirmation()
                                            }
                                            BackHandler { leaveConfirmation() }
                                            OrderConfirmationScreen(
                                                viewModel = ordersViewModel,
                                                onViewOrders = {
                                                    leaveConfirmation()
                                                    currentTab = AppNavTab.PROFILE
                                                    showOrders = true
                                                },
                                                onBackToMarket = { leaveConfirmation() },
                                                onOpenItem = { item, order ->
                                                    leaveConfirmation()
                                                    selectedMarketProduct = item.toMarketplaceProduct(order.sellerName)
                                                }
                                            )
                                        } else if (showMessages && !showCart) {
                                            MessagesScreen(
                                                viewModel = messagesViewModel,
                                                onBack = { showMessages = false },
                                                onOpenCart = { showCart = true }
                                            )
                                        } else if (showCart) {
                                            BackHandler { showCart = false }
                                            CartScreen(
                                                viewModel = cartViewModel,
                                                checkoutViewModel = checkoutViewModel,
                                                onBack = { showCart = false },
                                                onBrowse = {
                                                    showCart = false
                                                    showMarketplaceSearch = true
                                                },
                                                onOpenProduct = { line ->
                                                    showCart = false
                                                    selectedMarketProduct = line.toMarketplaceProduct()
                                                },
                                                onOrderPlaced = { session ->
                                                    ordersViewModel.showConfirmation(session)
                                                    showOrderConfirmation = true
                                                }
                                            )
                                        } else if (product != null) {
                                            BackHandler { selectedMarketProduct = null }
                                            val productType = product.cartProductType()
                                            ProductDetailScreen(
                                                viewModel = viewModel(
                                                    key = "detail-${product.type}-${product.id}",
                                                    factory = AiServiceProvider.productDetailViewModelFactory(product)
                                                ),
                                                forumViewModel = productType?.let {
                                                    viewModel<ProductForumViewModel>(
                                                        key = "forum-$it-${product.id}",
                                                        factory = AiServiceProvider.productForumViewModelFactory(it, product.id)
                                                    )
                                                },
                                                reviewsViewModel = productType?.let {
                                                    viewModel<ProductReviewsViewModel>(
                                                        key = "reviews-$it-${product.id}",
                                                        factory = AiServiceProvider.productReviewsViewModelFactory(it, product.id)
                                                    )
                                                },
                                                onBack = { selectedMarketProduct = null },
                                                onOpenCart = { showCart = true },
                                                onContactSeller = if (product.type == "used") {
                                                    { makeOffer: Boolean ->
                                                        messagesViewModel.contactSeller(product.id, makeOffer)
                                                        showMessages = true
                                                    }
                                                } else null
                                            )
                                        } else if (showPostProduct) {
                                            PostProductScreen(
                                                viewModel = postProductViewModel,
                                                userId = authManager.currentUserId,
                                                onPublished = { showPostProduct = false },
                                                onBack = { showPostProduct = false },
                                                onOpenCart = { showCart = true }
                                            )
                                        } else if (showSponsored) {
                                            BackHandler { showSponsored = false }
                                            SponsoredPartnersScreen(
                                                viewModel = sponsoredPartnersViewModel,
                                                onBack = { showSponsored = false },
                                                onOpenProduct = { selectedMarketProduct = it }
                                            )
                                        } else if (showMarketplaceSearch) {
                                            ProductSearchScreen(
                                                viewModel = productViewModel,
                                                onBack = { showMarketplaceSearch = false },
                                                onCart = { showCart = true },
                                                onProductClick = { selectedMarketProduct = it.searchResultToMarketplaceProduct() }
                                            )
                                        } else {
                                            MarketplaceHomeScreen(
                                                viewModel = marketplaceViewModel,
                                                onOpenSearch = { showMarketplaceSearch = true },
                                                onPostProduct = { showPostProduct = true },
                                                onOpenProfile = { currentTab = AppNavTab.PROFILE },
                                                onCartClick = { showCart = true },
                                                onOpenSponsored = { showSponsored = true },
                                                onOpenProduct = { selectedMarketProduct = it },
                                                profilePhotoUrl = chatState.userProfile?.photoUrl,
                                                onOpenMessages = { showMessages = true },
                                                unreadMessages = messagesState.unread
                                            )
                                        }
                                    }
                                    AppNavTab.AR_CORE -> {
                                        NotImplementedScreen(
                                            tabName = currentTab.label,
                                            icon = currentTab.icon,
                                            onBackToChat = { currentTab = AppNavTab.AI_CHAT }
                                        )
                                    }
                                }

                                // The chat has its own "+" in the header. Product Detail, the cart and the sell
                                // form have their own sticky bar at the bottom, which this button would overlap.
                                if (currentTab != AppNavTab.AI_CHAT && selectedMarketProduct == null && !showCart &&
                                    !showPostProduct && editingListing == null &&
                                    !(showMessages && currentTab == AppNavTab.MARKET)
                                ) {
                                    NewChatButton(
                                        onClick = { startNewChat() },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(16.dp)
                                    )
                                }
                            }

                            // Bottom Navigation Bar matching HTML mockup. The sell form has its own
                            // sticky publish bar, so the tab bar is hidden while it is open.
                            if (!showPostProduct && editingListing == null && !useRail) {
                                BottomNavBar(
                                    currentTab = currentTab,
                                    onTabSelected = {
                                        showApplianceManuals = false
                                        showMarketplaceSearch = false
                                        showPostProduct = false
                                        showMyListings = false
                                        editingListing = null
                                        showSponsored = false
                                        showOrders = false
                                        showOrderConfirmation = false
                                        showCart = false
                                        showMessages = false
                                        selectedMarketProduct = null
                                        currentTab = it
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

    }

    if (showAuthDialog) {
        AuthDialog(
            authManager = authManager,
            onSignedOut = {
                // The login screen owns the session state: signing out here must
                // send the user back to it, not leave the app in a guest mode.
                authViewModel.signOut()
            },
            onDismiss = {
                showAuthDialog = false
                chatViewModel.refreshBookmarks()
                chatViewModel.refreshChatHistory()
            }
        )
    }
}

/**
 * Clean placeholder screen for tabs not implemented yet.
 */
@Composable
private fun NotImplementedScreen(
    tabName: String,
    icon: ImageVector,
    onBackToChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = SecondaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "$tabName Module",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This screen is currently not implemented yet. Please use the AI Chat to diagnose appliances and troubleshoot issues.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBackToChat,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Go to AI Chat")
        }
    }
}

/** How often the bell asks the backend for new notifications while the app is open. */
private const val NOTIFICATIONS_REFRESH_MS = 15_000L
