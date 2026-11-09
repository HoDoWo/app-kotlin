package com.hodowo.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hodowo.data.AuthManager
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.components.NotificationIconButton
import com.hodowo.ui.components.ProfileAvatar
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.MyListingsViewModel
import com.hodowo.viewmodel.Listing
import com.hodowo.viewmodel.ListingStatus
import com.hodowo.ui.imageData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authManager: AuthManager? = null,
    viewModel: ChatViewModel? = null,
    myListingsViewModel: MyListingsViewModel? = null,
    onOpenAuth: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateAiChat: () -> Unit = {},
    onNavigateMarket: () -> Unit = {},
    onNavigateArCore: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onOpenApplianceManuals: () -> Unit = {},
    onOpenMyListings: () -> Unit = {},
    onPostProduct: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser = authManager?.currentUser?.collectAsState()?.value
    val chatState = viewModel?.uiState?.collectAsState()?.value
    val uploadingPhoto = viewModel?.isUploadingPhoto?.collectAsState()?.value ?: false

    // Toasts coming from the ViewModel (photo upload errors etc.)
    LaunchedEffect(viewModel) {
        viewModel?.userMessages?.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Profile photo picker (no storage permission needed)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel?.updateProfilePhoto(uri)
    }
    fun pickPhoto() {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Identity
    val isSignedIn = currentUser != null && authManager != null && !authManager.isAnonymous
    val displayEmail = if (isSignedIn) currentUser?.email ?: "User" else "Guest User"
    val profile = chatState?.userProfile
    val photoUrl = profile?.photoUrl?.takeIf { it.isNotBlank() }
    val defaultName = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: if (isSignedIn) {
            currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                ?: "User"
        } else "Guest"

    val memberSince = when {
        !isSignedIn -> "Guest Account"
        profile?.memberSince != null ->
            "Member since " + SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(profile.memberSince))
        else -> ""
    }

    // Dialog state
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddApplianceDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var selectedGuideForDetails by remember { mutableStateOf<ManualGuide?>(null) }

    var editNameInput by remember(defaultName) { mutableStateOf(defaultName) }
    var newApplianceName by remember { mutableStateOf("") }
    var newApplianceBrand by remember { mutableStateOf("") }
    var newApplianceRoom by remember { mutableStateOf("") }

    val bookmarkedGuides = chatState?.bookmarkedGuides ?: emptyList()
    val myListingsState = myListingsViewModel?.uiState?.collectAsState()?.value
    val listings = myListingsState?.listings ?: emptyList()

    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            myListingsViewModel?.load()
        }
    }

    // Owned appliances: active session (if any) + persisted Firestore ones. No fake sample data.
    val ownedAppliances = remember(chatState?.appliance, chatState?.userAppliances) {
        val list = mutableListOf<ApplianceItemData>()
        chatState?.appliance?.let { app ->
            list.add(
                ApplianceItemData(
                    id = "active_session",
                    title = listOfNotNull(app.brand, app.name).joinToString(" "),
                    manualCount = "Active session",
                    subtitle = "Identified via photo • ${(app.confidence * 100).toInt()}% match",
                    icon = Icons.Default.LocalLaundryService,
                    isPrimary = true,
                    isUserRegistered = false,
                    brand = app.brand,
                    model = app.model
                )
            )
        }
        (chatState?.userAppliances ?: emptyList()).forEach { app ->
            val icon = when {
                app.category?.contains("wash", true) == true || app.name.contains("wash", true) -> Icons.Default.LocalLaundryService
                app.category?.contains("coffee", true) == true || app.name.contains("coffee", true) -> Icons.Default.Coffee
                app.category?.contains("micro", true) == true || app.name.contains("micro", true) -> Icons.Default.Microwave
                else -> Icons.Default.Kitchen
            }
            list.add(
                ApplianceItemData(
                    id = app.id,
                    title = app.name,
                    manualCount = "${app.manualCount} manual${if (app.manualCount != 1) "s" else ""}",
                    subtitle = app.subtitle ?: listOfNotNull(app.brand, "Registered in profile").joinToString(" • "),
                    icon = icon,
                    isPrimary = false,
                    isUserRegistered = true,
                    brand = app.brand,
                    model = app.model
                )
            )
        }
        list
    }

    Scaffold(
        topBar = {
            AppTopBar(
                subtitle = "PROFILE",
                actions = {
                    NotificationIconButton(
                        onClick = {
                            Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show()
                        }
                    )
                    ProfileAvatar(
                        name = defaultName,
                        size = 34,
                        photoUrl = photoUrl,
                        onClick = {
                            editNameInput = defaultName
                            showEditProfileDialog = true
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. PROFILE HEADER
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).clickable { pickPhoto() }) {
                        ProfileAvatar(name = defaultName, size = 64, photoUrl = photoUrl)
                        if (uploadingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PhotoCamera, "Change photo", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(defaultName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(displayEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(
                                onClick = { editNameInput = defaultName; showEditProfileDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, "Edit Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (memberSince.isNotBlank()) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Text(
                                        text = memberSince,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { if (isSignedIn) showAccountDialog = true else onOpenAuth() },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(if (isSignedIn) "Account" else "Sign In", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. SAVED MANUALS
            if (bookmarkedGuides.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("Saved Repair Manuals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Your bookmarked AI troubleshooting guides", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${bookmarkedGuides.size} saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    bookmarkedGuides.forEach { guide ->
                        BookmarkedGuideCard(guide = guide, onClick = { selectedGuideForDetails = guide })
                    }
                }
            }

            // 3. OWNED APPLIANCES
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Owned Appliances", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Manage registered appliances & repair manuals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.clickable {
                            newApplianceName = ""; newApplianceBrand = ""; newApplianceRoom = ""
                            showAddApplianceDialog = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Add", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (ownedAppliances.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Kitchen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No appliances yet", fontWeight = FontWeight.SemiBold)
                            Text("Register your appliances to keep their manuals in one place.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = {
                                newApplianceName = ""; newApplianceBrand = ""; newApplianceRoom = ""
                                showAddApplianceDialog = true
                            }) { Text("Add appliance") }
                        }
                    }
                }

                ownedAppliances.forEach { item ->
                    ApplianceCard(
                        icon = item.icon,
                        title = item.title,
                        manualCount = item.manualCount,
                        subtitle = item.subtitle,
                        badgeColor = if (item.isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        onClick = {
                            // Opens the appliance screen listing its related manuals
                            viewModel?.openApplianceManuals(item.id, item.title, item.brand, item.model, item.isUserRegistered)
                            onOpenApplianceManuals()
                        }
                    )
                }
            }

            // 4. MY LISTINGS entry point (#146 has the full screen; posting to sell is #145, not built yet)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Appliances Posted to Sell", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Manage your marketplace listings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.clickable { onPostProduct() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Sell, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("+ Sell", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenMyListings() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Manage your listings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Edit profile: name + photo (both persisted)
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.clip(CircleShape).clickable { pickPhoto() }) {
                            ProfileAvatar(name = editNameInput.ifBlank { defaultName }, size = 56, photoUrl = photoUrl)
                            if (uploadingPhoto) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp)
                        }
                        TextButton(onClick = { pickPhoto() }) {
                            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Change photo")
                        }
                    }
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val name = editNameInput.trim()
                    if (name.isNotBlank() && name != defaultName) {
                        viewModel?.updateUserDisplayName(name)
                        Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                    }
                    showEditProfileDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") } }
        )
    }

    // Add appliance
    if (showAddApplianceDialog) {
        AlertDialog(
            onDismissRequest = { showAddApplianceDialog = false },
            title = { Text("Register New Appliance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newApplianceName, { newApplianceName = it }, label = { Text("Appliance name *") }, placeholder = { Text("e.g. LG Smart Fridge") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newApplianceBrand, { newApplianceBrand = it }, label = { Text("Brand (optional)") }, placeholder = { Text("e.g. LG, Bosch, Samsung") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newApplianceRoom, { newApplianceRoom = it }, label = { Text("Room / location (optional)") }, placeholder = { Text("e.g. Kitchen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = newApplianceName.isNotBlank(),
                    onClick = {
                        val saved = viewModel?.addAppliance(name = newApplianceName.trim(), brand = newApplianceBrand.trim(), room = newApplianceRoom.trim()) == true
                        Toast.makeText(
                            context,
                            if (saved) "Appliance registered to your profile!" else "Sign in to keep your appliances",
                            Toast.LENGTH_SHORT
                        ).show()
                        showAddApplianceDialog = false
                    }
                ) { Text("Register") }
            },
            dismissButton = { TextButton(onClick = { showAddApplianceDialog = false }) { Text("Cancel") } }
        )
    }

    // Bookmarked guide
    selectedGuideForDetails?.let { guide ->
        ManualGuideDialog(
            guide = guide,
            onDismiss = { selectedGuideForDetails = null },
            onAskAi = {
                viewModel?.sendTextMessage("I need help with \"${guide.issueTitle}\" on my ${guide.applianceName}.")
                selectedGuideForDetails = null
                onNavigateAiChat()
            },
            onRemove = {
                viewModel?.removeBookmark(guide)
                Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                selectedGuideForDetails = null
            }
        )
    }

    // Account
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Account Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Email: ${currentUser?.email ?: "N/A"}")
                    Text("User ID: ${currentUser?.uid?.take(12) ?: "N/A"}...")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authManager?.signOut()
                        showAccountDialog = false
                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sign Out") }
            },
            dismissButton = { TextButton(onClick = { showAccountDialog = false }) { Text("Close") } }
        )
    }

}

/** Shared by Profile (bookmarks) and ApplianceManualsScreen. [onRemove] null = no Remove button. */
@Composable
internal fun ManualGuideDialog(
    guide: ManualGuide,
    onDismiss: () -> Unit,
    onAskAi: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(guide.issueTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(guide.applianceName.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(guide.overview, style = MaterialTheme.typography.bodyMedium)
                if (guide.steps.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Steps:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    guide.steps.forEach { step ->
                        Text("${step.number}. ${step.title}: ${step.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onAskAi) { Text("Ask AI") } },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
internal fun BookmarkedGuideCard(guide: ManualGuide, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(guide.applianceName.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(guide.issueTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(guide.overview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            guide.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

private data class ApplianceItemData(
    val id: String = "",
    val title: String,
    val manualCount: String,
    val subtitle: String,
    val icon: ImageVector,
    val isPrimary: Boolean,
    val isUserRegistered: Boolean = false,
    val brand: String? = null,
    val model: String? = null
)

@Composable
fun ApplianceCard(
    icon: ImageVector,
    title: String,
    manualCount: String,
    subtitle: String,
    badgeColor: Color,
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, false)
                        )
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = manualCount,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun ProfileListingCard(
    listing: Listing,
    onClick: () -> Unit
) {
    val isSold = listing.status == ListingStatus.SOLD
    val statusColor = if (isSold) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!listing.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageData(listing.imageUrl),
                    contentDescription = listing.title,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Sell, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = listing.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.12f)) {
                        Text(
                            text = if (isSold) "Sold" else "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "${listing.condition} • ${listing.postedAgo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = listing.priceLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}