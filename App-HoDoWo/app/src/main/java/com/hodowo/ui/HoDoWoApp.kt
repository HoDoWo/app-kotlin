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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hodowo.data.AiServiceProvider
import com.hodowo.data.AuthManager
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLowest
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
    authManager: AuthManager = AiServiceProvider.authManager
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppNavTab.HOME) }
    var showCamera by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showApplianceManuals by remember { mutableStateOf(false) }

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
                                onNavigateMarket = { currentTab = AppNavTab.MARKET },
                                onNavigateArCore = { currentTab = AppNavTab.AR_CORE },
                                onNavigateProfile = { currentTab = AppNavTab.PROFILE },
                                onOpenApplianceManuals = { showApplianceManuals = true }
                            )
                        }
                    }
                    AppNavTab.MARKET, AppNavTab.AR_CORE -> {
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

@Composable
private fun NewChatButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = Primary,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("New chat", fontWeight = FontWeight.SemiBold) },
        modifier = modifier
    )
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