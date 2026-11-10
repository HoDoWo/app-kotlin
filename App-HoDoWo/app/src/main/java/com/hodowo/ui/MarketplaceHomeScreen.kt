package com.hodowo.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hodowo.data.LocationDistanceHelper
import com.hodowo.data.MarketplaceProduct
import com.hodowo.ui.adaptive.AdaptiveContent
import com.hodowo.ui.adaptive.LocalAppLayout
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.components.ProfileAvatar
import com.hodowo.ui.theme.OnPrimary
import com.hodowo.ui.theme.OnSurface
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.Outline
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.Secondary
import com.hodowo.ui.theme.SurfaceContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.ui.theme.TertiaryContainer
import com.hodowo.viewmodel.MarketplaceViewModel

@Composable
fun MarketplaceHomeScreen(
    viewModel: MarketplaceViewModel,
    onOpenSearch: () -> Unit,
    onPostProduct: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onCartClick: () -> Unit = {},
    /** Opens the Product Detail page with its community forum. */
    onOpenProduct: (MarketplaceProduct) -> Unit = {},
    /** Photo of the signed-in user, as on the Home top bar. */
    profilePhotoUrl: String? = null,
    /** User's saved location for dynamic distance calculation to community listings. */
    userLocation: String? = null,
    /** User's ID to filter out own listings from community deals. */
    currentUserId: String? = null
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val cartCount by viewModel.cartCount.collectAsState()

    // Feedback of "add to cart"
    val cartMessage by viewModel.cartMessage.collectAsState()
    LaunchedEffect(cartMessage) {
        cartMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeCartMessage()
        }
    }

    var selectedTypeChip by rememberSaveable { mutableStateOf("All") }
    var selectedStateChip by rememberSaveable { mutableStateOf("All") }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    // Filter button badge: how many filters are active
    val activeFilterCount = remember(selectedTypeChip, selectedStateChip) {
        (if (selectedTypeChip != "All") 1 else 0) + (if (selectedStateChip != "All") 1 else 0)
    }

    // Filter out current user's own listings from community marketplace
    val visibleProducts = remember(products, currentUserId) {
        products.filter { currentUserId == null || it.sellerId != currentUserId }
    }

    // Partition using backend 	ype field as canonical classifier:
    //   "new"  -> partner_products (Official Partner Deals)
    //   "part" -> parts collection  (also Official Partner Deals)
    //   "used" -> listings collection (Community & Pre-Owned Deals)
    val partnerDealsAll = remember(visibleProducts) {
        visibleProducts
            .filter { it.type == "new" || it.type == "part" }
            .sortedWith(
                compareByDescending<MarketplaceProduct> { it.sponsored }
                    .thenByDescending { it.partnerTier ?: 0 }
                    .thenByDescending { it.rating }
            )
    }

    val communityDealsAll = remember(visibleProducts) {
        visibleProducts.filter { it.type == "used" }
    }

    // Filter by Type Chip
    val filteredPartnerDeals = remember(partnerDealsAll, selectedTypeChip) {
        partnerDealsAll.filter { item ->
            when (selectedTypeChip) {
                "Full Appliances" -> !item.isPart
                "Replacement Parts" -> item.isPart
                else -> true
            }
        }
    }

    val filteredCommunityDeals = remember(communityDealsAll, selectedTypeChip) {
        communityDealsAll.filter { item ->
            when (selectedTypeChip) {
                "Full Appliances" -> !item.isPart
                "Replacement Parts" -> item.isPart
                else -> true
            }
        }
    }

    // Random ad product chosen from top partners
    val topPartnerProducts = remember(partnerDealsAll) {
        val top = partnerDealsAll.filter { it.sponsored || (it.partnerTier ?: 0) >= 1 }
        if (top.isNotEmpty()) top else partnerDealsAll
    }
    val adProduct = remember(topPartnerProducts) {
        if (topPartnerProducts.isNotEmpty()) topPartnerProducts.random() else null
    }

    val showPartnerSection = selectedStateChip == "All" || selectedStateChip == "Brand New (Partners)"
    val showCommunitySection = selectedStateChip == "All" || selectedStateChip == "Second-Hand (Community)"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val useRail = LocalAppLayout.current.useNavigationRail
            AppTopBar(
                subtitle = "MARKETPLACE",
                actions = {
                    IconButton(onClick = {
                        Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = OnSurfaceVariant
                        )
                    }
                    BadgedBox(
                        badge = {
                            if (cartCount > 0) {
                                Badge(containerColor = Primary) {
                                    Text(
                                        text = "$cartCount",
                                        color = OnPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = onCartClick) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Shopping Cart",
                                tint = OnSurfaceVariant
                            )
                        }
                    }
                    if (!useRail) {
                        ProfileAvatar(name = "", size = 34, onClick = onOpenProfile, photoUrl = profilePhotoUrl)
                    }
                }
            )

            // Main Scrollable Content
            AdaptiveContent(maxWidth = 1100.dp) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Interactive Search & Scan Action Strip + Filter Chips
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenSearch() },
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceContainerLowest,
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Model code, OEM part #, or appliance...",
                                        color = Outline,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Filter button – opens bottom sheet
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { showFilterSheet = true },
                                    color = if (activeFilterCount > 0) Primary else SurfaceContainerLowest,
                                    shape = CircleShape,
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Filters",
                                            tint = if (activeFilterCount > 0) OnPrimary else OnSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (activeFilterCount > 0) "Filters (${activeFilterCount})" else "Filter",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (activeFilterCount > 0) OnPrimary else OnSurfaceVariant
                                        )
                                    }
                                }
                                if (selectedTypeChip != "All") {
                                    Surface(
                                        modifier = Modifier.clip(CircleShape),
                                        color = PrimaryFixed,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = selectedTypeChip,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Primary
                                        )
                                    }
                                }
                                if (selectedStateChip != "All") {
                                    Surface(
                                        modifier = Modifier.clip(CircleShape),
                                        color = PrimaryFixed,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = selectedStateChip,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Featured Partner Sponsored Banner ("Ad")
                    if (adProduct != null && selectedStateChip != "Second-Hand (Community)") {
                        item {
                            FeaturedPartnerBanner(
                                product = adProduct,
                                onOpenProduct = { onOpenProduct(adProduct) },
                                onAddToCart = { viewModel.addToCart(adProduct) }
                            )
                        }
                    }

                    // Community Sell Prompt CTA Banner
                    item {
                        CommunitySellPromptBanner(
                            onPostItem = onPostProduct
                        )
                    }

                    // Official Partner Deals Section
                    if (showPartnerSection && filteredPartnerDeals.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Official Partner Deals",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OnSurface
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onOpenSearch() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "See all",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Primary
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Horizontal Carousel of Partner Cards
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(filteredPartnerDeals, key = { it.id }) { item ->
                                        PartnerDealCarouselCard(
                                            product = item,
                                            onOpen = { onOpenProduct(item) },
                                            onAddToCart = { viewModel.addToCart(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Community & Pre-Owned Deals Section
                    if (showCommunitySection && filteredCommunityDeals.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Community & Pre-Owned",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OnSurface
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(SurfaceContainer, CircleShape)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Near You",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = OnSurfaceVariant
                                        )
                                    }
                                }

                                // List of Community Items
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    filteredCommunityDeals.forEach { item ->
                                        val distanceKm = remember(userLocation, item.location) {
                                            LocationDistanceHelper.calculateDistanceKm(userLocation, item.location) ?: item.distanceKm
                                        }
                                        val formattedDistance = LocationDistanceHelper.formatDistance(distanceKm)

                                        CommunityDealRowItem(
                                            product = item,
                                            distanceText = formattedDistance,
                                            onOpen = { onOpenProduct(item) },
                                            onChat = {
                                                Toast.makeText(context, "Chat with ${item.sellerName}", Toast.LENGTH_SHORT).show()
                                            },
                                            onBuy = {
                                                viewModel.addToCart(item)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // The filters live in a bottom sheet: they are hidden until the user asks
    // for them with the "Filter" button, so the deals sections stay clean.
    if (showFilterSheet) {
        FilterSheet(
            selectedTypeChip = selectedTypeChip,
            selectedStateChip = selectedStateChip,
            onSelectType = { value ->
                selectedTypeChip = when (value) {
                    "appliance" -> "Full Appliances"
                    "part" -> "Replacement Parts"
                    else -> "All"
                }
                viewModel.setClassification(value)
            },
            onSelectState = { value ->
                selectedStateChip = when (value) {
                    "new" -> "Brand New (Partners)"
                    "used" -> "Second-Hand (Community)"
                    else -> "All"
                }
                viewModel.setCondition(value)
            },
            onClear = {
                selectedTypeChip = "All"
                selectedStateChip = "All"
                viewModel.setClassification("all")
                viewModel.setCondition("all")
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

/**
 * Filter submenu of the marketplace home: the type and the condition (state)
 * of the items to show. Every option is a plain "submenu" entry; applying one
 * refreshes the deals lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    selectedTypeChip: String,
    selectedStateChip: String,
    onSelectType: (String) -> Unit,
    onSelectState: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Text(
                text = "Choose what the community sections show.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )

            Text(
                text = "Type",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
            FilterOptions(
                options = listOf(
                    "All" to "all",
                    "Full Appliances" to "appliance",
                    "Replacement Parts" to "part"
                ),
                selectedValue = if (selectedTypeChip == "All") "all" else if (selectedTypeChip == "Full Appliances") "appliance" else "part",
                onSelect = onSelectType
            )

            Text(
                text = "Condition",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
            FilterOptions(
                options = listOf(
                    "All" to "all",
                    "Brand New (Partners)" to "new",
                    "Second-Hand (Community)" to "used"
                ),
                selectedValue = when (selectedStateChip) {
                    "Brand New (Partners)" -> "new"
                    "Second-Hand (Community)" -> "used"
                    else -> "all"
                },
                onSelect = onSelectState
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

/** One row of filter choices inside the filter submenu. */
@Composable
private fun FilterOptions(
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(value) },
                shape = RoundedCornerShape(12.dp),
                color = if (selectedValue == value) Primary else SurfaceContainerLowest,
                border = BorderStroke(1.dp, if (selectedValue == value) Primary else OutlineVariant.copy(alpha = 0.4f))
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedValue == value) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedValue == value) OnPrimary else OnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}
@Composable
private fun FeaturedPartnerBanner(
    product: MarketplaceProduct,
    onOpenProduct: () -> Unit,
    onAddToCart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onOpenProduct() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Ambient subtle radial/blur accent
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(110.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(PrimaryFixed.copy(alpha = 0.45f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Partner Info & Ad Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val partnerName = product.partnerName ?: product.sellerName
                    val initialLetter = partnerName.firstOrNull()?.uppercase() ?: "P"

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(PrimaryFixed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialLetter,
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = partnerName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Partner",
                            tint = Primary,
                            modifier = Modifier.size(15.dp)
                        )
                        if (product.rating > 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = String.format("%.1f", product.rating),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurface
                                )
                                if (product.reviewCount > 0) {
                                    Text(
                                        text = "(${product.reviewCount})",
                                        fontSize = 10.sp,
                                        color = Outline
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(PrimaryFixed, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (product.isPart) "Part • Brand New" else "Full Appliance • Brand New",
                                color = Primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(SurfaceContainerHigh, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Ad",
                                color = OnSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Product Title & Image
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        modifier = Modifier.weight(1f),
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerHigh)
                    ) {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Price & CTA Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        product.originalPrice?.let { orig ->
                            Text(
                                text = "$${orig.toInt()} MSRP",
                                fontSize = 11.sp,
                                color = Outline,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$${product.price.toInt()}",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                            product.discountPercent?.let { disc ->
                                Text(
                                    text = "(-$disc%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TertiaryContainer
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onAddToCart,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Add to Cart", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Community Sell Prompt CTA Banner
 */
@Composable
private fun CommunitySellPromptBanner(
    onPostItem: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryFixed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sell Parts & Appliances",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Text(
                    text = "List spares & machines directly to nearby DIYers",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 15.sp
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPostItem() },
                color = SurfaceContainerLowest,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Post Item",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Primary
                    )
                }
            }
        }
    }
}

/**
 * Official Partner Deal Carousel Card
 */
@Composable
private fun PartnerDealCarouselCard(
    product: MarketplaceProduct,
    onOpen: () -> Unit,
    onAddToCart: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(SurfaceContainerHigh)
            ) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(SurfaceContainerLowest.copy(alpha = 0.92f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (product.isPart) "Part • Brand New" else "Full Appliance • Brand New",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.partnerName ?: product.sellerName,
                        fontSize = 11.sp,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (product.rating > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = String.format("%.1f", product.rating),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                        }
                    }
                }

                Text(
                    text = product.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                product.warrantyInfo?.let { warranty ->
                    Box(
                        modifier = Modifier
                            .background(SurfaceContainerHigh, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = warranty,
                            fontSize = 10.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        product.originalPrice?.let { orig ->
                            Text(
                                text = "$${orig.toInt()}",
                                fontSize = 11.sp,
                                color = Outline,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        Text(
                            text = "$${product.price.toInt()}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAddToCart() },
                        color = Primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AddShoppingCart,
                                contentDescription = "Add to cart",
                                tint = OnPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Community Deal Row Item with dynamic distance and chat/buy actions
 */
@Composable
private fun CommunityDealRowItem(
    product: MarketplaceProduct,
    distanceText: String,
    onOpen: () -> Unit,
    onChat: () -> Unit,
    onBuy: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceContainerHigh)
            ) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(SurfaceContainerLowest.copy(alpha = 0.92f), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (product.isRefurbished) "Part • Refurbished" else if (product.isPart) "Part • Pre-Owned" else "Appliance • Like New",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$distanceText • ${product.sellerName}",
                                fontSize = 10.sp,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (product.rating > 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = String.format("%.1f", product.rating),
                                    fontSize = 11.sp,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = product.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = product.description,
                        fontSize = 11.sp,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$${product.price.toInt()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onChat() },
                            color = SurfaceContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Chat",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onBuy,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Buy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
