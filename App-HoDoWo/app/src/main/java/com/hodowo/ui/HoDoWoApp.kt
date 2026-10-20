package com.hodowo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hodowo.BuildConfig
import com.hodowo.data.AiServiceProvider
import com.hodowo.data.AuthManager
import com.hodowo.data.auth.FirebaseAuthRepository
import com.hodowo.data.auth.GoogleSignInHelper
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.AuthViewModel
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.HomeViewModel
import com.hodowo.viewmodel.ManualGuide

enum class AppNavTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    AI_CHAT("AI Chat", Icons.Default.Forum),
    MARKET("Market", Icons.Default.Storefront),
    AR_CORE("AR Core", Icons.Default.ViewInAr),
    PROFILE("Profile", Icons.Default.Person)
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
    authManager: AuthManager = AiServiceProvider.authManager,
    authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthViewModel(FirebaseAuthRepository()) }
        }
    )
) {

    val context = LocalContext.current

    val authState by authViewModel.uiState.collectAsState()

    // Nothing else is shown until the user is signed in (#54).
    if (!authState.isSignedIn) {
        AuthRoute(
            viewModel = authViewModel,
            googleSignIn = remember { GoogleSignInHelper(BuildConfig.GOOGLE_WEB_CLIENT_ID) },
            context = context
        )
        return
    }

    var currentTab by remember { mutableStateOf(AppNavTab.HOME) }
    var showCamera by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
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
                            onOpenMarket = { currentTab = AppNavTab.MARKET },
                            onOpenProfile = { currentTab = AppNavTab.PROFILE },
                            onAskAboutManual = { guide ->
                                currentTab = AppNavTab.AI_CHAT
                                chatViewModel.sendTextMessage(
                                    "I need help with \"${guide.issueTitle}\" on my ${guide.applianceName}."
                                )
                            }
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
                        ProfileScreen(
                            authManager = authManager,
                            viewModel = chatViewModel,
                            onOpenAuth = { showAuthDialog = true },
                            onNavigateToChat = { currentTab = AppNavTab.AI_CHAT }
                        )
                    }
                    AppNavTab.MARKET, AppNavTab.AR_CORE -> {
                        NotImplementedScreen(
                            tabName = currentTab.label,
                            icon = currentTab.icon,
                            onBackToChat = { currentTab = AppNavTab.AI_CHAT }
                        )
                    }
                }
            }

            // Bottom Navigation Bar matching HTML mockup
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    }

    if (showAuthDialog) {
        AuthDialog(
            authManager = authManager,
            onDismiss = {
                showAuthDialog = false
                chatViewModel.refreshBookmarks()
            }
        )
    }
}

/**
 * Bottom Navigation Bar matching the HTML Tailwind mock:
 * Height 72dp, rounded-full active indicator, centered icons with label below.
 */
@Composable
private fun BottomNavBar(
    currentTab: AppNavTab,
    onTabSelected: (AppNavTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceContainerHigh.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppNavTab.values().forEach { tab ->
                val isSelected = currentTab == tab

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) SecondaryContainer else Color.Transparent,
                        modifier = Modifier
                            .width(56.dp)
                            .height(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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

/**
 * Profile and Bookmarked Manuals screen.
 */
@Composable
private fun ProfileScreen(
    authManager: AuthManager,
    viewModel: ChatViewModel,
    onOpenAuth: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val currentUser by authManager.currentUser.collectAsState()
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "My Profile & Bookmarks",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // User Account Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Primary,
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentUser != null && !authManager.isAnonymous) currentUser?.email ?: "User"
                        else "Guest User",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (currentUser != null && !authManager.isAnonymous) "Signed in with Firebase"
                        else "Tap to sign in or create account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onOpenAuth,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (currentUser != null && !authManager.isAnonymous) "Manage" else "Sign In")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bookmarked Manuals Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Manual Guides",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${state.bookmarkedGuides.size} saved",
                style = MaterialTheme.typography.labelMedium,
                color = Primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.bookmarkedGuides.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No bookmarked manuals yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the bookmark icon on any AI manual guide in chat to save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToChat,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start a Chat")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.bookmarkedGuides) { guide ->
                    BookmarkedGuideCard(guide = guide)
                }
            }
        }
    }
}

@Composable
private fun BookmarkedGuideCard(guide: ManualGuide) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = guide.applianceName.uppercase(java.util.Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = guide.issueTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = guide.overview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            guide.imageUrl?.let { url ->
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageData(url))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    // Whole image, full width: the height follows the image's own proportions.
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}