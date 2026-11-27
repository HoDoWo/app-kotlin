package com.hodowo.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hodowo.R
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.hodowo.ui.adaptive.AdaptiveContent
import com.hodowo.ui.adaptive.LocalAppLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hodowo.data.AuthManager
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.components.NotificationIconButton
import com.hodowo.ui.components.SettingsIconButton
import com.hodowo.ui.components.ProfileAvatar
import com.hodowo.viewmodel.isSameAs
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.MyListingsViewModel
import com.hodowo.viewmodel.Listing
import com.hodowo.viewmodel.ListingStatus
import com.hodowo.ui.imageData
import java.text.SimpleDateFormat
import java.util.Date

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
    /** Purchase history (#144). */
    onOpenOrders: () -> Unit = {},
    onPostProduct: () -> Unit = {},
    /** Settings: appearance, accessibility and language (#210). */
    onOpenSettings: () -> Unit = {},
    /** The login screen owns the session, so signing out here must show it again. */
    onSignedOut: () -> Unit = {}
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
    val userFallback = stringResource(R.string.profile_user_fallback)
    val displayEmail = if (isSignedIn) currentUser?.email ?: userFallback else stringResource(R.string.profile_guest_user)
    val profile = chatState?.userProfile
    val photoUrl = profile?.photoUrl?.takeIf { it.isNotBlank() }
    val defaultName = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: if (isSignedIn) {
            currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                ?: userFallback
        } else stringResource(R.string.profile_guest)

    val memberSince = when {
        !isSignedIn -> stringResource(R.string.profile_guest_account)
        profile?.memberSince != null -> stringResource(
            R.string.profile_member_since,
            SimpleDateFormat("yyyy", currentLocale()).format(Date(profile.memberSince))
        )
        else -> ""
    }

    // Read here: the click handlers below are not composable.
    val profileUpdatedMessage = stringResource(R.string.profile_updated)
    val applianceSavedMessage = stringResource(R.string.profile_appliance_saved)
    val applianceSignInMessage = stringResource(R.string.profile_appliance_sign_in)
    val bookmarkRemovedMessage = stringResource(R.string.profile_bookmark_removed)
    val signedOutMessage = stringResource(R.string.profile_signed_out)
    val askAiMessage = stringResource(R.string.profile_ask_ai_message)
    val notAvailable = stringResource(R.string.common_not_available)

    // Dialog state
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddApplianceDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
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
    // Built in composition (not remembered): its texts come from the resources.
    val activeSessionLabel = stringResource(R.string.profile_active_session)
    val registeredLabel = stringResource(R.string.profile_registered_in_profile)
    val ownedAppliances = run {
        val list = mutableListOf<ApplianceItemData>()
        // The appliance of the chat, unless it is already one of the registered appliances below.
        chatState?.appliance?.takeUnless { app ->
            (chatState?.userAppliances ?: emptyList()).any { app.isSameAs(it) }
        }?.let { app ->
            list.add(
                ApplianceItemData(
                    id = "active_session",
                    title = listOfNotNull(app.brand, app.name).joinToString(" "),
                    manualCount = activeSessionLabel,
                    subtitle = stringResource(R.string.profile_identified_via_photo, (app.confidence * 100).toInt()),
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
                    manualCount = pluralStringResource(R.plurals.profile_manual_count, app.manualCount, app.manualCount),
                    subtitle = app.subtitle ?: listOfNotNull(app.brand, registeredLabel).joinToString(" • "),
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
                subtitle = stringResource(R.string.profile_top_bar),
                actions = {
                    SettingsIconButton(onClick = onOpenSettings)
                    NotificationIconButton()
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
        AdaptiveContent(modifier = Modifier.padding(innerPadding), maxWidth = 980.dp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                                    Icon(Icons.Default.PhotoCamera, stringResource(R.string.profile_change_photo), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
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
                                    Icon(Icons.Default.Edit, stringResource(R.string.profile_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
                                    Text(if (isSignedIn) stringResource(R.string.profile_account) else stringResource(R.string.profile_sign_in), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.profile_saved_manuals), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.profile_saved_manuals_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(pluralStringResource(R.plurals.profile_saved_count, bookmarkedGuides.size, bookmarkedGuides.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                            Text(stringResource(R.string.profile_owned_appliances), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.profile_owned_appliances_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(R.string.profile_add), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
                                Text(stringResource(R.string.profile_no_appliances), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.profile_no_appliances_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = {
                                    newApplianceName = ""; newApplianceBrand = ""; newApplianceRoom = ""
                                    showAddApplianceDialog = true
                                }) { Text(stringResource(R.string.profile_add_appliance)) }
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

                // 4. APPLIANCES POSTED TO SELL
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.profile_listings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.profile_listings_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(R.string.profile_sell), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    when {
                        !isSignedIn -> {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().clickable { onOpenAuth() }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.profile_sign_in_to_sell), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            stringResource(R.string.profile_sign_in_to_sell_body),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        myListingsState?.isLoading == true && listings.isEmpty() -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    stringResource(R.string.profile_loading_listings),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        listings.isEmpty() -> {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().clickable { onPostProduct() }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.profile_no_listings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            stringResource(R.string.profile_no_listings_body),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        else -> {
                            val activeCount = listings.count { it.status == ListingStatus.ACTIVE }
                            val soldCount = listings.count { it.status == ListingStatus.SOLD }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ListingStatPill(text = pluralStringResource(R.plurals.profile_listings_active, activeCount, activeCount), highlighted = true)
                                ListingStatPill(text = pluralStringResource(R.plurals.profile_listings_sold, soldCount, soldCount), highlighted = false)
                                ListingStatPill(text = pluralStringResource(R.plurals.profile_listings_total, listings.size, listings.size), highlighted = false)
                            }

                            listings.take(2).forEach { listing ->
                                ProfileListingCard(listing = listing, onClick = onOpenMyListings)
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
                                        Text(
                                            pluralStringResource(R.plurals.profile_manage_listings, listings.size, listings.size),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                // Purchase history (#144): every order, with its status.
                if (isSignedIn) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenOrders() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.profile_orders), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.profile_orders_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // 5. ACCOUNT: signing out is a clear action at the end of the profile,
                // not hidden inside the "Account" details.
                if (isSignedIn) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showAccountDialog = true }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.profile_account_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        currentUser?.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showSignOutConfirm = true }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    stringResource(R.string.profile_sign_out),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit profile: name + photo (both persisted)
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text(stringResource(R.string.profile_edit)) },
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
                            Text(stringResource(R.string.profile_change_photo))
                        }
                    }
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text(stringResource(R.string.profile_display_name)) },
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
                        Toast.makeText(context, profileUpdatedMessage, Toast.LENGTH_SHORT).show()
                    }
                    showEditProfileDialog = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showEditProfileDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // Add appliance
    if (showAddApplianceDialog) {
        AlertDialog(
            onDismissRequest = { showAddApplianceDialog = false },
            title = { Text(stringResource(R.string.profile_register_appliance)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newApplianceName, { newApplianceName = it }, label = { Text(stringResource(R.string.profile_appliance_name)) }, placeholder = { Text(stringResource(R.string.profile_appliance_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newApplianceBrand, { newApplianceBrand = it }, label = { Text(stringResource(R.string.profile_appliance_brand)) }, placeholder = { Text(stringResource(R.string.profile_appliance_brand_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newApplianceRoom, { newApplianceRoom = it }, label = { Text(stringResource(R.string.profile_appliance_room)) }, placeholder = { Text(stringResource(R.string.profile_appliance_room_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = newApplianceName.isNotBlank(),
                    onClick = {
                        val saved = viewModel?.addAppliance(name = newApplianceName.trim(), brand = newApplianceBrand.trim(), room = newApplianceRoom.trim()) == true
                        Toast.makeText(
                            context,
                            if (saved) applianceSavedMessage else applianceSignInMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                        showAddApplianceDialog = false
                    }
                ) { Text(stringResource(R.string.profile_register)) }
            },
            dismissButton = { TextButton(onClick = { showAddApplianceDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // Bookmarked guide
    selectedGuideForDetails?.let { guide ->
        ManualGuideDialog(
            guide = guide,
            onDismiss = { selectedGuideForDetails = null },
            onAskAi = {
                viewModel?.sendTextMessage(askAiMessage.format(guide.issueTitle, guide.applianceName))
                selectedGuideForDetails = null
                onNavigateAiChat()
            },
            onRemove = {
                viewModel?.removeBookmark(guide)
                Toast.makeText(context, bookmarkRemovedMessage, Toast.LENGTH_SHORT).show()
                selectedGuideForDetails = null
            }
        )
    }

    // Account
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(stringResource(R.string.profile_account_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.profile_account_email, currentUser?.email ?: notAvailable))
                    Text(stringResource(R.string.profile_account_user_id, currentUser?.uid?.take(12) ?: notAvailable))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountDialog = false
                        showSignOutConfirm = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.profile_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { showAccountDialog = false }) { Text(stringResource(R.string.common_close)) } }
        )
    }

    // Sign out, always confirmed: the user knows what happens to their data.
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text(stringResource(R.string.profile_sign_out_confirm_title)) },
            text = {
                Text(stringResource(R.string.profile_sign_out_confirm_body))
            },
            confirmButton = {
                Button(
                    onClick = {
                        authManager?.signOut()
                        onSignedOut()
                        showSignOutConfirm = false
                        Toast.makeText(context, signedOutMessage, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.profile_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { showSignOutConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
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
                Text(guide.applianceName.uppercase(currentLocale()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(guide.overview, style = MaterialTheme.typography.bodyMedium)
                if (guide.steps.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.guide_steps_label), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    guide.steps.forEach { step ->
                        Text(stringResource(R.string.guide_step_line, step.number, step.title, step.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onAskAi) { Text(stringResource(R.string.guide_ask_ai)) } },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.common_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    )
}

@Composable
internal fun BookmarkedGuideCard(guide: ManualGuide, onClick: () -> Unit) {
    val isWide = LocalAppLayout.current.isWide
    val imageUrl = guide.imageUrl?.takeIf { it.isNotBlank() }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        if (isWide && imageUrl != null) {
            // Landscape: image on the right, text on the left
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(guide.applianceName.uppercase(currentLocale()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(guide.issueTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(guide.overview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(100.dp).height(90.dp).clip(RoundedCornerShape(8.dp))
                )
            }
        } else {
            // Portrait: text stacked, image below
            Column(modifier = Modifier.padding(14.dp)) {
                Text(guide.applianceName.uppercase(currentLocale()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(guide.issueTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(guide.overview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (imageUrl != null) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp))
                    )
                }
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
                    contentDescription = stringResource(R.string.profile_view_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun ListingStatPill(text: String, highlighted: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
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
                            text = if (isSold) stringResource(R.string.listing_status_sold) else stringResource(R.string.listing_status_active),
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