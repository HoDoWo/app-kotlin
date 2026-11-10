package com.hodowo.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hodowo.ui.adaptive.AdaptiveContent
import com.hodowo.data.MarketplaceProduct
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.components.ProfileAvatar
import com.hodowo.ui.theme.OnPrimary
import com.hodowo.ui.theme.OnSecondary
import com.hodowo.ui.theme.OnSurface
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.OnTertiaryFixed
import com.hodowo.ui.theme.Outline
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.Secondary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SecondaryFixed
import com.hodowo.ui.theme.SurfaceContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.ui.theme.TertiaryContainer
import com.hodowo.ui.theme.TertiaryFixed
import com.hodowo.viewmodel.MarketplaceViewModel

@Composable
fun MarketplaceHomeScreen(
    viewModel: MarketplaceViewModel,
    onOpenSearch: () -> Unit,
    onPostProduct: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onCartClick: () -> Unit = {},
    /** Opens the Product Detail page (#140) with its community forum (#150). */
    onOpenProduct: (MarketplaceProduct) -> Unit = {},
    /** Photo of the signed-in user, as on the Home top bar. */
    profilePhotoUrl: String? = null
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val cartCount by viewModel.cartCount.collectAsState()

    // Feedback of "add to cart" comes from the backend: added, already sold, out of stock...
    val cartMessage by viewModel.cartMessage.collectAsState()
    LaunchedEffect(cartMessage) {
        cartMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeCartMessage()
        }
    }

    var selectedTypeChip by remember { mutableStateOf("All") }
    var selectedStateChip by remember { mutableStateOf("All") }
    var searchInput by remember { mutableStateOf("") }

    // Partition products into Partner Deals and Community Items
    val partnerDeals = products.filter { it.partnerName != null || it.condition in listOf("new", "unopened", "brand-new") }
    val communityDeals = products.filter { it.partnerName == null && it.condition !in listOf("new", "unopened", "brand-new") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Same top bar as Home: real logo, "MARKETPLACE" and the signed-in user's photo.
            AppTopBar(
                subtitle = "MARKETPLACE",
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
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
                    ProfileAvatar(name = "", size = 34, onClick = onOpenProfile, photoUrl = profilePhotoUrl)
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
                        // Search only: the cart is in the top bar, with its badge.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
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
                                        text = if (searchInput.isBlank()) "Model code, OEM part #, or appliance..." else searchInput,
                                        color = if (searchInput.isBlank()) Outline else OnSurface,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Category & State Filters
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Row 1: Type
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "TYPE:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Outline,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                listOf("All", "Full Appliances", "Replacement Parts").forEach { chip ->
                                    val isSelected = selectedTypeChip == chip
                                    Surface(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                selectedTypeChip = chip
                                                val mappedType = when (chip) {
                                                    "Full Appliances" -> "appliance"
                                                    "Replacement Parts" -> "part"
                                                    else -> "all"
                                                }
                                                viewModel.setClassification(mappedType)
                                            },
                                        color = if (isSelected) Primary else SurfaceContainerLowest,
                                        shape = CircleShape,
                                        shadowElevation = 1.dp
                                    ) {
                                        Text(
                                            text = chip,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) OnPrimary else OnSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Row 2: State
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "STATE:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Outline,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                listOf(
                                    "All" to "all",
                                    "Brand New (Partners)" to "new",
                                    "Second-Hand (Community)" to "used"
                                ).forEach { (label, value) ->
                                    val isSelected = selectedStateChip == label
                                    Surface(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                selectedStateChip = label
                                                viewModel.setCondition(value)
                                            },
                                        color = if (isSelected) Primary else SurfaceContainerLowest,
                                        shape = CircleShape,
                                        shadowElevation = 1.dp
                                    ) {
                                        Text(
                                            text = label,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) OnPrimary else OnSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Featured Partner Sponsored Banner
                item {
                    FeaturedPartnerBanner(
                        onViewPartDetails = onOpenSearch
                    )
                }

                // Community Sell Prompt CTA Banner
                item {
                    CommunitySellPromptBanner(
                        onPostItem = onPostProduct
                    )
                }

                // Official Partner Deals Section
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
                            items(partnerDeals, key = { it.id }) { item ->
                                PartnerDealCarouselCard(
                                    product = item,
                                    onOpen = { onOpenProduct(item) },
                                    onAddToCart = {
                                        viewModel.addToCart(item)
                                    }
                                )
                            }
                        }
                    }
                }

                // Community & Pre-Owned Deals Section
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
                            communityDeals.forEach { item ->
                                CommunityDealRowItem(
                                    product = item,
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

/**
 * Featured Partner Sponsored Banner
 */
@Composable
private fun FeaturedPartnerBanner(
    onViewPartDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PrimaryFixed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "B",
                            color = Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Bosch Official Store",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .background(PrimaryFixed, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Part • Brand New",
                            color = Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(SurfaceContainer, CircleShape)
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

            // Body
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bosch WAN Serie 4 Drain Motor & Eco Accessories",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                    modifier = Modifier.weight(1f),
                    lineHeight = 20.sp
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = "https://lh3.googleusercontent.com/aida-public/AB6AXuAAHMYQKJYNSiL8AxhO2_mFYZSeqhDG7lDk7OGCipDHhG55lIV10-bn9x8vABIGofZ6rYZQhheAko7ccMawBqnH-_m0YMqYjzTX74xbRgN8PZ86PS7J8m-I3us3jcCc26VNJQi1xw_TzLhvE8AByiQiP5BNu0Q2UXXOt51lIqguWDAP_dBfmFUIbbbEEgokrRgd0dXDB1pwoq4buRNmbihWNWaaTj1C5tCoRDL-LUM",
                        contentDescription = "Bosch WAN Serie 4",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$89.00 MSRP",
                        fontSize = 11.sp,
                        color = Outline,
                        textDecoration = TextDecoration.LineThrough
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$64.50",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Text(
                            text = "(-27%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TertiaryContainer
                        )
                    }
                }

                Button(
                    onClick = onViewPartDetails,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("View Part Details", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
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
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
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
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sell Parts & Appliances",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
                Text(
                    text = "Have an old washer or spare valve? List in 2 minutes to local DIYers.",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 15.sp
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPostItem() },
                color = SurfaceContainerLowest,
                shape = RoundedCornerShape(8.dp),
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
                        .background(SurfaceContainerLowest.copy(alpha = 0.9f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
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
                Text(
                    text = product.sellerName,
                    fontSize = 11.sp,
                    color = OnSurfaceVariant
                )
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
 * Community Deal Row Item
 */
@Composable
private fun CommunityDealRowItem(
    product: MarketplaceProduct,
    onOpen: () -> Unit,
    onChat: () -> Unit,
    onBuy: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
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
                        .background(SurfaceContainerLowest.copy(alpha = 0.9f), CircleShape)
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
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = listOfNotNull(product.distanceKm?.let { "$it km away" }, product.sellerName).joinToString(" • "),
                                fontSize = 10.sp,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Stars only for products with real reviews.
                        if (product.rating > 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = TertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${product.rating}",
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