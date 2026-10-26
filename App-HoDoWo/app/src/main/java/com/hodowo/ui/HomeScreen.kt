package com.hodowo.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.components.NotificationIconButton
import com.hodowo.ui.components.ProfileAvatar
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.Secondary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.ui.theme.Tertiary
import com.hodowo.viewmodel.HomeUiState
import com.hodowo.viewmodel.HomeViewModel
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualsSection
import com.hodowo.viewmodel.RecentManual
import com.hodowo.viewmodel.relativeTimeLabel

/** Light orange of the "Market" action (tertiary-fixed in the mockup). */
private val TertiaryFixed = Color(0xFFFFDDB8)

/**
 * Home screen from the Stitch mockup: greeting, search, quick actions,
 * daily tip, the user's recent manuals and the most popular manuals of all users.
 *
 * @param onSearch sends the text to the AI chat as a question.
 * @param onDiagnose opens the camera to photograph an appliance.
 * @param onAskAboutManual opens the chat with a question about a manual.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSearch: (String) -> Unit,
    onDiagnose: () -> Unit,
    onOpenArCore: () -> Unit,
    onOpenMarket: () -> Unit,
    onOpenProfile: () -> Unit,
    onAskAboutManual: (ManualGuide) -> Unit,
    /** Photo of the signed-in user for the top bar; null shows the default avatar. */
    profilePhotoUrl: String? = null
) {
    val state by viewModel.uiState.collectAsState()
    var openedManual by remember { mutableStateOf<RecentManual?>(null) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Reload every time Home is shown, so manuals created in the chat appear.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Download the images of both lists into Coil's disk cache, so they can be seen offline.
    LaunchedEffect(state.recentManuals, state.popularManuals) {
        (state.recentManuals + state.popularManuals)
            .mapNotNull { it.guide.imageUrl }
            .filter { it.startsWith("http") }
            .distinct()
            .forEach { url -> context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AppTopBar(
            subtitle = "HOME",
            actions = {
                NotificationIconButton(
                    onClick = {
                        Toast.makeText(context, "No notifications yet", Toast.LENGTH_SHORT).show()
                    }
                )
                ProfileAvatar(name = "", size = 34, onClick = onOpenProfile, photoUrl = profilePhotoUrl)
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Extra space at the bottom, so the "New chat" button never covers a card.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Greeting(state.userName) }
            item { HomeSearchBar(onSearch = onSearch) }
            item {
                QuickActions(
                    onDiagnose = onDiagnose,
                    onOpenArCore = onOpenArCore,
                    onOpenMarket = onOpenMarket
                )
            }
            item { HelpfulTipCard(state.tip) }
            item {
                SectionHeader(section = state.section, onSelect = viewModel::selectSection)
            }
            manualsSection(
                state = state,
                onOpen = { openedManual = it },
                onImageClick = { viewerUrl = it },
                onRetry = viewModel::refresh,
                onDiagnose = onDiagnose
            )
        }
    }

    openedManual?.let { manual ->
        ManualDetailDialog(
            manual = manual,
            onDismiss = { openedManual = null },
            onImageClick = { viewerUrl = it },
            onAskInChat = {
                openedManual = null
                onAskAboutManual(manual.guide)
            }
        )
    }

    viewerUrl?.let { url ->
        FullscreenImageViewer(url = url, onDismiss = { viewerUrl = null })
    }
}

private fun LazyListScope.manualsSection(
    state: HomeUiState,
    onOpen: (RecentManual) -> Unit,
    onImageClick: (String) -> Unit,
    onRetry: () -> Unit,
    onDiagnose: () -> Unit
) {
    val isPopular = state.section == ManualsSection.POPULAR
    val manuals = state.shownManuals

    when {
        manuals.isNotEmpty() -> {
            // Saved copy shown because the backend could not be reached.
            if (state.shownFailed) {
                item { OfflineRow(onRetry = onRetry) }
            }
            itemsIndexed(manuals, key = { index, manual -> "${state.section}-${manual.guide.manualId ?: index}" }) { index, manual ->
                RecentManualCard(
                    manual = manual,
                    now = state.now,
                    highlighted = index % 2 == 0,
                    isPopular = isPopular,
                    onClick = { onOpen(manual) },
                    onImageClick = onImageClick
                )
            }
        }
        state.isLoadingShown -> item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
            }
        }
        state.shownFailed -> item { ManualsErrorRow(onRetry = onRetry) }
        isPopular -> item { EmptyPopularCard() }
        else -> item { EmptyManualsCard(onDiagnose = onDiagnose) }
    }
}

/** Title of the list with the "Recent / Most popular" switch next to it. */
@Composable
private fun SectionHeader(section: ManualsSection, onSelect: (ManualsSection) -> Unit) {
    val other = if (section == ManualsSection.RECENT) ManualsSection.POPULAR else ManualsSection.RECENT

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Tapping the title also switches list.
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .clickable { onSelect(other) }
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = SurfaceContainerHigh,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Row(modifier = Modifier.padding(3.dp)) {
                SwitchOption(
                    label = "Recent",
                    selected = section == ManualsSection.RECENT,
                    onClick = { onSelect(ManualsSection.RECENT) }
                )
                SwitchOption(
                    label = "Popular",
                    selected = section == ManualsSection.POPULAR,
                    onClick = { onSelect(ManualsSection.POPULAR) }
                )
            }
        }
    }
}

@Composable
private fun SwitchOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Primary else Color.Transparent
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else OnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun Greeting(userName: String) {
    Column {
        Text(
            text = "Hello $userName 👋",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "What do you need today?",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant
        )
    }
}

@Composable
private fun HomeSearchBar(onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    fun submit() {
        val text = query.trim()
        if (text.isNotEmpty()) {
            focusManager.clearFocus()
            onSearch(text)
            query = ""
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text("Search appliance, issue, or error code...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceVariant) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceContainerLowest,
            unfocusedContainerColor = SurfaceContainerLowest,
            focusedBorderColor = Primary,
            unfocusedBorderColor = OutlineVariant.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun QuickActions(
    onDiagnose: () -> Unit,
    onOpenArCore: () -> Unit,
    onOpenMarket: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButton(
            label = "Diagnose",
            subtitle = "Issue",
            icon = Icons.Outlined.DocumentScanner,
            iconTint = Primary,
            iconBackground = Primary.copy(alpha = 0.1f),
            onClick = onDiagnose,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            label = "ARcore",
            icon = Icons.Outlined.Kitchen,
            iconTint = Secondary,
            iconBackground = SecondaryContainer,
            onClick = onOpenArCore,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            label = "Market",
            icon = Icons.Outlined.ShoppingBag,
            iconTint = Tertiary,
            iconBackground = TertiaryFixed,
            onClick = onOpenMarket,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.2f)),
        modifier = modifier.height(96.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = CircleShape, color = iconBackground, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HelpfulTipCard(tip: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLow,
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "💡", fontSize = 22.sp)
            Column {
                Text(
                    text = "Helpful Tip",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = tip, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentManualCard(
    manual: RecentManual,
    now: Long,
    highlighted: Boolean,
    isPopular: Boolean,
    onClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val guide = manual.guide

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ApplianceChip(name = guide.applianceName, highlighted = highlighted, modifier = Modifier.weight(1f, fill = false))
                if (isPopular) {
                    PopularityLabel(searches = manual.searchCount, saves = manual.saveCount)
                } else generatedLabel(manual.createdAtMillis, now)?.let { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }

            Column {
                Text(
                    text = guide.issueTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (guide.overview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = guide.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            guide.imageUrl?.let { url -> GuideImage(url = url, onClick = { onImageClick(url) }) }
        }
    }
}

/** "🔍 12  🔖 3": how many times a popular guide was searched and saved. */
@Composable
private fun PopularityLabel(searches: Int, saves: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Default.Search, contentDescription = "Searches", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(text = "$searches", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Default.Bookmark, contentDescription = "Saves", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(text = "$saves", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

/** "Generated 2h ago" for recent manuals, "Yesterday" / a date for older ones. */
private fun generatedLabel(createdAtMillis: Long?, now: Long): String? {
    val label = relativeTimeLabel(createdAtMillis, now) ?: return null
    return if (label == "Just now" || label.endsWith("ago")) "Generated ${label.replaceFirstChar { it.lowercase() }}" else label
}

@Composable
private fun ApplianceChip(name: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlighted) Primary.copy(alpha = 0.1f) else SecondaryContainer,
        modifier = modifier
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (highlighted) Primary else Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Guide image at full width; tapping it opens the full-screen viewer (zoom, download). */
@Composable
private fun GuideImage(url: String, onClick: () -> Unit) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(imageData(url)).crossfade(true).build(),
        contentDescription = "Guide image, tap to enlarge",
        // Whole image, full width: the height follows the image's own proportions.
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerHigh)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun EmptyPopularCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLowest,
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "No popular guides yet: they appear when users search and save guides.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(20.dp)
        )
    }
}

/** Shown above a saved copy of the list when the backend cannot be reached. */
@Composable
private fun OfflineRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "You're offline: showing the saved copy.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyManualsCard(onDiagnose: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLowest,
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No manuals yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Take a photo of an appliance and ask what you need: your guides will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDiagnose,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Diagnose an issue")
            }
        }
    }
}

@Composable
private fun ManualsErrorRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Couldn't load the manuals.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun ManualDetailDialog(
    manual: RecentManual,
    onDismiss: () -> Unit,
    onImageClick: (String) -> Unit,
    onAskInChat: () -> Unit
) {
    val guide = manual.guide

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ApplianceChip(name = guide.applianceName, highlighted = true, modifier = Modifier.weight(1f, fill = false))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(text = guide.issueTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (guide.overview.isNotBlank()) {
                    Text(text = guide.overview, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                }
                guide.imageUrl?.let { url -> GuideImage(url = url, onClick = { onImageClick(url) }) }

                guide.steps.forEach { step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = Primary, modifier = Modifier.size(26.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "${step.number}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = step.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (step.description.isNotBlank()) {
                                Text(text = step.description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                        }
                    }
                }

                guide.warnings.forEach { warning ->
                    Text(
                        text = "⚠️ $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = onAskInChat,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ask about this in the chat")
                }
            }
        }
    }
}
