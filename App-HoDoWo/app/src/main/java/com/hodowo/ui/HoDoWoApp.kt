package com.hodowo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hodowo.data.AiServiceProvider
import com.hodowo.data.AuthManager
import com.hodowo.data.model.cartProductType
import com.hodowo.data.cartProductType as marketplaceCartProductType
import com.hodowo.ui.components.AppNavTab
import com.hodowo.ui.components.BottomNavBar
import com.hodowo.ui.components.NewChatButton
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.ProductSearchScreen
import com.hodowo.viewmodel.CartViewModel
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.CheckoutViewModel
import com.hodowo.viewmodel.HomeViewModel
import com.hodowo.viewmodel.MarketplaceViewModel
import com.hodowo.viewmodel.ProductDetailViewModel
import com.hodowo.viewmodel.ProductViewModel

/** Inner navigation of the Market tab. */
private sealed interface MarketRoute {
    data object Home : MarketRoute
    data object Search : MarketRoute
    data class Detail(
        val productId: String,
        val productType: String?,
        val backTo: MarketRoute
    ) : MarketRoute
    data class Cart(val backTo: MarketRoute) : MarketRoute
}

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
    productViewModel: ProductViewModel = viewModel(
        factory = AiServiceProvider.productViewModelFactory(
            LocalContext.current.applicationContext
        )
    ),
    authManager: AuthManager = AiServiceProvider.authManager
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(AppNavTab.HOME) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    val chatState by chatViewModel.uiState.collectAsState()
    var showAuthDialog by rememberSaveable { mutableStateOf(false) }
    var showApplianceManuals by rememberSaveable { mutableStateOf(false) }
    var marketRoute by rememberSaveable(stateSaver = MarketRouteSaver) {
        mutableStateOf<MarketRoute>(MarketRoute.Home)
    }

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
        Column(modifier = Modifier.fillMaxSize()) {
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
                            onOpenMarket = {
                                marketRoute = MarketRoute.Home
                                currentTab = AppNavTab.MARKET
                            },
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
                        } else {
                            ProfileScreen(
                                authManager = authManager,
                                viewModel = chatViewModel,
                                onOpenAuth = { showAuthDialog = true },
                                onNavigateHome = { currentTab = AppNavTab.HOME },
                                onNavigateAiChat = { currentTab = AppNavTab.AI_CHAT },
                                onNavigateMarket = {
                                    marketRoute = MarketRoute.Home
                                    currentTab = AppNavTab.MARKET
                                },
                                onNavigateArCore = { currentTab = AppNavTab.AR_CORE },
                                onNavigateProfile = { currentTab = AppNavTab.PROFILE },
                                onOpenApplianceManuals = { showApplianceManuals = true }
                            )
                        }
                    }
                    AppNavTab.MARKET -> {
                        when (val route = marketRoute) {
                            MarketRoute.Home -> {
                                val marketplaceViewModel: MarketplaceViewModel = viewModel(
                                    key = "marketplace-home",
                                    factory = AiServiceProvider.marketplaceViewModelFactory()
                                )
                                MarketplaceHomeScreen(
                                    viewModel = marketplaceViewModel,
                                    onOpenSearch = { marketRoute = MarketRoute.Search },
                                    onPostProduct = { marketRoute = MarketRoute.Search },
                                    onOpenProfile = { currentTab = AppNavTab.PROFILE },
                                    onCartClick = { marketRoute = MarketRoute.Cart(MarketRoute.Home) },
                                    onOpenProduct = { product ->
                                        marketRoute = MarketRoute.Detail(
                                            productId = product.id,
                                            productType = product.marketplaceCartProductType(),
                                            backTo = MarketRoute.Home
                                        )
                                    },
                                    profilePhotoUrl = chatState.userProfile?.photoUrl
                                )
                            }

                            MarketRoute.Search -> ProductSearchScreen(
                                viewModel = productViewModel,
                                onBack = { marketRoute = MarketRoute.Home },
                                onCart = { marketRoute = MarketRoute.Cart(MarketRoute.Search) },
                                onProductClick = { product ->
                                    marketRoute = MarketRoute.Detail(
                                        productId = product.id,
                                        productType = product.cartProductType(),
                                        backTo = MarketRoute.Search
                                    )
                                }
                            )

                            is MarketRoute.Detail -> {
                                val detailViewModel: ProductDetailViewModel = viewModel(
                                    key = "product-detail-${route.productId}",
                                    factory = AiServiceProvider.productDetailViewModelFactory(
                                        productId = route.productId,
                                        productType = route.productType
                                    )
                                )
                                ProductDetailScreen(
                                    viewModel = detailViewModel,
                                    onBack = { marketRoute = route.backTo },
                                    onOpenCart = { marketRoute = MarketRoute.Cart(route) }
                                )
                            }

                            is MarketRoute.Cart -> {
                                val cartViewModel: CartViewModel = viewModel(
                                    factory = AiServiceProvider.cartViewModelFactory()
                                )
                                val checkoutViewModel: CheckoutViewModel = viewModel(
                                    factory = AiServiceProvider.checkoutViewModelFactory()
                                )
                                CartScreen(
                                    viewModel = cartViewModel,
                                    checkoutViewModel = checkoutViewModel,
                                    onBack = { marketRoute = route.backTo },
                                    onBrowse = {
                                        marketRoute = MarketRoute.Search
                                        currentTab = AppNavTab.MARKET
                                    }
                                )
                            }
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

                // The chat has its own "+" in the header; every other section gets this button.
                if (currentTab != AppNavTab.AI_CHAT) {
                    NewChatButton(
                        onClick = { startNewChat() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    )
                }
            }

            // Bottom Navigation Bar matching HTML mockup
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = {
                    showApplianceManuals = false
                    marketRoute = MarketRoute.Home
                    currentTab = it
                }
            )
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

/** Saves the Market navigation across configuration changes (rotation). */
private val MarketRouteSaver: Saver<MarketRoute, String> = Saver(
    save = { route -> route.encode() },
    restore = { encoded -> decodeMarketRoute(encoded) }
)

private fun encodePart(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")

private fun decodePart(value: String): String =
    runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

private fun MarketRoute.encode(): String = when (this) {
    MarketRoute.Home -> "home"
    MarketRoute.Search -> "search"
    is MarketRoute.Detail -> listOf(
        "detail",
        encodePart(productId),
        productType?.let(::encodePart) ?: "",
        backTo.encode()
    ).joinToString("|")

    is MarketRoute.Cart -> "cart|" + backTo.encode()
}

private fun decodeMarketRoute(encoded: String): MarketRoute? = when {
    encoded == "home" -> MarketRoute.Home
    encoded == "search" -> MarketRoute.Search
    encoded.startsWith("detail|") -> {
        val parts = encoded.split("|", limit = 4)
        val backTo = parts.getOrNull(3)?.let(::decodeMarketRoute)
        if (parts.size == 4 && backTo != null) {
            MarketRoute.Detail(
                productId = decodePart(parts[1]),
                productType = parts[2].takeIf { it.isNotEmpty() }?.let(::decodePart),
                backTo = backTo
            )
        } else {
            null
        }
    }

    encoded.startsWith("cart|") -> {
        decodeMarketRoute(encoded.substringAfter("cart|"))?.let { MarketRoute.Cart(it) }
    }

    else -> null
}
