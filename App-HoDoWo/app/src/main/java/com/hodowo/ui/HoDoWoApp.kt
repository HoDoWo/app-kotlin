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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.hodowo.data.AiServiceProvider
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.cartProductType
import com.hodowo.data.AuthManager
import com.hodowo.viewmodel.MyListingsViewModel
import com.hodowo.viewmodel.ProductDetailViewModel
import com.hodowo.viewmodel.ProductForumViewModel
import com.hodowo.ui.adaptive.LocalAppLayout
import com.hodowo.ui.adaptive.appLayoutInfoFor
import com.hodowo.ui.components.AppNavTab
import com.hodowo.ui.components.AppNavigationRail
import com.hodowo.ui.components.BottomNavBar
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
import com.hodowo.viewmodel.PostProductViewModel

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
    authManager: AuthManager = AiServiceProvider.authManager
) {
    val context = LocalContext.current
    val marketplaceViewModel: MarketplaceViewModel = viewModel(factory = AiServiceProvider.marketplaceViewModelFactory())
    val postProductViewModel: PostProductViewModel = viewModel(
        factory = AiServiceProvider.postProductViewModelFactory(context)
    )
    val cartViewModel: CartViewModel = viewModel(factory = AiServiceProvider.cartViewModelFactory())
    val checkoutViewModel: CheckoutViewModel = viewModel(factory = AiServiceProvider.checkoutViewModelFactory())
    val myListingsViewModel: MyListingsViewModel = viewModel(factory = AiServiceProvider.myListingsViewModelFactory())
    var currentTab by remember { mutableStateOf(AppNavTab.HOME) }
    var showCamera by remember { mutableStateOf(false) }
    val chatState by chatViewModel.uiState.collectAsState()
    var showAuthDialog by remember { mutableStateOf(false) }
    var showApplianceManuals by remember { mutableStateOf(false) }
    var showPostProduct by remember { mutableStateOf(false) }
    var showMarketplaceSearch by remember { mutableStateOf(false) }
    var showMyListings by remember { mutableStateOf(false) }
    var showCart by remember { mutableStateOf(false) }
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
            val useRail = layoutInfo.useNavigationRail && !showPostProduct

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
                                showCart = false
                                selectedMarketProduct = null
                                currentTab = it
                            },
                            bottomActions = {
                                NotificationIconButton(onClick = {
                                    Toast.makeText(context, "No notifications yet", Toast.LENGTH_SHORT).show()
                                })
                                ProfileAvatar(
                                    name = "",
                                    size = 34,
                                    onClick = {
                                        showApplianceManuals = false
                                        showMarketplaceSearch = false
                                        showPostProduct = false
                                        showMyListings = false
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
                            onAskAboutManual = { guide ->
                                currentTab = AppNavTab.AI_CHAT
                                chatViewModel.sendTextMessage(
                                    "I need help with \"${guide.issueTitle}\" on my ${guide.applianceName}."
                                )
                            },
                            profilePhotoUrl = chatState.userProfile?.photoUrl
                        )
                    }
                    AppNavTab.AI_CHAT -> {
                        ChatScreen(
                            viewModel = chatViewModel,
                            onOpenCamera = ::openCamera,
                            onOpenAuth = { showAuthDialog = true }
                        )
                    }
                    AppNavTab.PROFILE -> {
                        if (showApplianceManuals) {
                            ApplianceManualsScreen(
                                viewModel = chatViewModel,
                                onBack = { showApplianceManuals = false },
                                onNavigateAiChat = {
                                    showApplianceManuals = false
                                    currentTab = AppNavTab.AI_CHAT
                                }
                            )
                        } else if (showMyListings) {
                            MyListingsScreen(
                                viewModel = myListingsViewModel,
                                onBack = { showMyListings = false },
                                onPostProduct = {
                                    showMyListings = false
                                    currentTab = AppNavTab.MARKET
                                    showPostProduct = true
                                }
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
                                onPostProduct = {
                                    currentTab = AppNavTab.MARKET
                                    showPostProduct = true
                                }
                            )
                        }
                    }
                    AppNavTab.MARKET -> {
                        val product = selectedMarketProduct
                        if (showCart) {
                            BackHandler { showCart = false }
                            CartScreen(
                                viewModel = cartViewModel,
                                checkoutViewModel = checkoutViewModel,
                                onBack = { showCart = false },
                                onBrowse = {
                                    showCart = false
                                    showMarketplaceSearch = true
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
                                onBack = { selectedMarketProduct = null },
                                onOpenCart = { showCart = true }
                            )
                        } else if (showPostProduct) {
                            PostProductScreen(
                                viewModel = postProductViewModel,
                                userId = authManager.currentUserId,
                                onPublished = { showPostProduct = false },
                                onBack = { showPostProduct = false },
                                onOpenCart = { showCart = true }
                            )
                        } else if (showMarketplaceSearch) {
                            ProductSearchAndFiltersScreen(
                                viewModel = marketplaceViewModel,
                                onBack = { showMarketplaceSearch = false },
                                onCartClick = { showCart = true },
                                onOpenProduct = { selectedMarketProduct = it }
                            )
                        } else {
                            MarketplaceHomeScreen(
                                viewModel = marketplaceViewModel,
                                onOpenSearch = { showMarketplaceSearch = true },
                                onPostProduct = { showPostProduct = true },
                                onOpenProfile = { currentTab = AppNavTab.PROFILE },
                                onCartClick = { showCart = true },
                                onOpenProduct = { selectedMarketProduct = it },
                                profilePhotoUrl = chatState.userProfile?.photoUrl
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
                    !showPostProduct
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
            if (!showPostProduct && !useRail) {
                BottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = {
                        showApplianceManuals = false
                        showMarketplaceSearch = false
                        showPostProduct = false
                        showMyListings = false
                        showCart = false
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

    if (showAuthDialog) {
        AuthDialog(
            authManager = authManager,
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
