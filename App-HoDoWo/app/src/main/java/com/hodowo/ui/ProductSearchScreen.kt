package com.hodowo.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductType
import com.hodowo.data.model.ProductUiState
import com.hodowo.data.model.activeFilterCount
import com.hodowo.ui.theme.*
import com.hodowo.viewmodel.ProductViewModel

// Literals taken verbatim from the mockup's tailwind palette.
private val MockPrimary = Color(0xFF26008C)
private val MockPrimaryContainer = Color(0xFF3B02C7)
private val MockOnPrimaryFixed = Color(0xFF190064)

@Composable
fun ProductSearchScreen(
    viewModel: ProductViewModel,
    onBack: () -> Unit = {},
    onCart: () -> Unit = {},
    onProductClick: (Product) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val cartCount by viewModel.cartCount.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterSheetExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { searchQuery = viewModel.filters.query }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val filters = viewModel.filters

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Background,
        topBar = {
            ProductTopBar(
                onBack = onBack,
                onCart = onCart,
                cartCount = cartCount,
                searchQuery = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.onQueryChanged(it)
                },
                activeFilterCount = filters.activeFilterCount(),
                onFilterPressed = { filterSheetExpanded = true }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
            ) {
                item { DiagnosticRail() }

                item {
                    MatrixControls(
                        type = filters.type,
                        condition = filters.condition,
                        onTypeSelected = { viewModel.onTypeSelected(it) },
                        onConditionSelected = { viewModel.onConditionSelected(it) }
                    )
                }

                when (val s = state) {
                    is ProductUiState.Loading -> item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = MockPrimary) }
                    }

                    ProductUiState.Empty -> item {
                        EmptyState(onRetry = { viewModel.onRefresh() })
                    }

                    is ProductUiState.Error -> item {
                        ErrorState(message = s.message, onRetry = { viewModel.onRefresh() })
                    }

                    is ProductUiState.Success -> {
                        if (s.products.isEmpty()) {
                            item { EmptyState(onRetry = { viewModel.onRefresh() }) }
                        } else {
                            items(s.products, key = { it.id }) { product ->
                                ProductCard(
                                    product = product,
                                    onOpenProduct = { onProductClick(product) },
                                    onAddToCart = { viewModel.addToCart(product) },
                                    onBuyNow = { viewModel.addToCart(product) },
                                    onContactSeller = { onProductClick(product) }
                                )
                            }
                            if (s.hasMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) { CircularProgressIndicator(color = MockPrimary) }
                                    LaunchedEffect(s.products.lastOrNull()?.id) {
                                        viewModel.loadMore()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (filterSheetExpanded) {
        FilterSheet(
            viewModel = viewModel,
            onClose = { filterSheetExpanded = false }
        )
    }
}

/** Fixed header: row 1 back / title / cart, row 2 search field / filter button. */
@Composable
private fun ProductTopBar(
    onBack: () -> Unit,
    onCart: () -> Unit,
    cartCount: Int,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    activeFilterCount: Int,
    onFilterPressed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-8).dp)) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Go back",
                    tint = OnSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Product Search & Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCart) {
                Box {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Shopping Cart",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    if (cartCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(16.dp)
                                .background(MockPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (cartCount > 9) "9+" else cartCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search console
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(SurfaceContainerLow)
                    .clip(RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MockPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = OnSurface),
                    cursorBrush = SolidColor(MockPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search parts, part numbers...",
                                style = MaterialTheme.typography.titleMedium.copy(color = OnSurfaceVariant),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear query",
                        tint = OnSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }

            // Filter button + count badge
            Button(
                onClick = onFilterPressed,
                colors = ButtonDefaults.buttonColors(containerColor = MockPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filters",
                        tint = OnPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    if (activeFilterCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MockPrimaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeFilterCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MockOnPrimaryFixed
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Horizontal scrollable diagnostic filter rail (Fits / Under $50). */
@Composable
private fun DiagnosticRail() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MockPrimary,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = TertiaryFixed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Fits: Bosch Serie 4",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OnPrimary
                    )
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = OnPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = SurfaceContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Under \$50",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** Dual control card: Items Classification (2 segments) + Condition Status chips. */
@Composable
private fun MatrixControls(
    type: ProductType?,
    condition: ProductCondition?,
    onTypeSelected: (ProductType?) -> Unit,
    onConditionSelected: (ProductCondition?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(SurfaceContainerLowest, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Items Classification
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MockPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "ITEMS CLASSIFICATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Secondary,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
                Text(
                    text = if (type == ProductType.WHOLE_APPLIANCE) "Showing Appliances" else "Showing Parts",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SegmentChip(
                    label = "Whole Appliances",
                    icon = Icons.Default.Kitchen,
                    selected = type == ProductType.WHOLE_APPLIANCE,
                    modifier = Modifier.weight(1f),
                    onClick = { onTypeSelected(ProductType.WHOLE_APPLIANCE) }
                )
                SegmentChip(
                    label = "Replacement Parts",
                    icon = Icons.Default.CheckCircle,
                    selected = type != ProductType.WHOLE_APPLIANCE,
                    modifier = Modifier.weight(1f),
                    onClick = { onTypeSelected(ProductType.REPLACEMENT_PART) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainerLow))

        // Condition Status
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "CONDITION STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Secondary,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
                Text(
                    text = when (condition) {
                        null -> "Both Selected"
                        ProductCondition.NEW -> "Brand New"
                        ProductCondition.SECOND_HAND -> "Second-Hand"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MockPrimary
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConditionChip(
                    label = "Brand New",
                    selected = condition == ProductCondition.NEW || condition == null,
                    background = SecondaryContainer,
                    contentColor = OnSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { onConditionSelected(ProductCondition.NEW) }
                )
                ConditionChip(
                    label = "Second-Hand",
                    selected = condition == ProductCondition.SECOND_HAND || condition == null,
                    background = TertiaryFixed,
                    contentColor = OnTertiaryFixed,
                    modifier = Modifier.weight(1f),
                    onClick = { onConditionSelected(ProductCondition.SECOND_HAND) }
                )
            }
        }
    }
}

@Composable
private fun SegmentChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MockPrimary else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) OnPrimary else OnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) OnPrimary else OnSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConditionChip(
    label: String,
    selected: Boolean,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) background else SurfaceContainerLow,
        border = if (selected) null else BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f)),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckBox,
                contentDescription = null,
                tint = if (selected) contentColor else OnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) contentColor else OnSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Product result card with four visual variants matching the mockup. */
@Composable
private fun ProductCard(
    product: Product,
    onOpenProduct: () -> Unit,
    onAddToCart: () -> Unit,
    onBuyNow: () -> Unit,
    onContactSeller: () -> Unit
) {
    val isPromoted = product.sponsored
    val isCommunity = product.distanceKm != null && product.discountPercent != null
    val isRefurbished = product.refurbished ||
        (product.distanceKm != null && product.discountPercent == null && product.condition == ProductCondition.SECOND_HAND)

    Surface(
        onClick = onOpenProduct,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column {
            if (isPromoted) PromotedRibbon()

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Thumbnail(product = product, isPromoted = isPromoted)

                    Column(modifier = Modifier.weight(1f)) {
                        CardHeader(
                            product = product,
                            isPromoted = isPromoted,
                            isCommunity = isCommunity,
                            isRefurbished = isRefurbished
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = product.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface,
                                lineHeight = 22.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RatingRow(product = product, isPromoted = isPromoted)
                        if (isCommunity || isRefurbished) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SellerLine(product = product, isCommunity = isCommunity)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PriceRow(product = product, isPromoted = isPromoted)
                    }
                }

                CardActions(
                    product = product,
                    isPromoted = isPromoted,
                    isCommunity = isCommunity,
                    isRefurbished = isRefurbished,
                    onOpenProduct = onOpenProduct,
                    onAddToCart = onAddToCart,
                    onBuyNow = onBuyNow,
                    onContactSeller = onContactSeller
                )
            }
        }
    }
}

@Composable
private fun PromotedRibbon() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(MockPrimaryContainer, MockPrimary, MockPrimaryContainer)))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = TertiaryFixed,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "TOP PROMOTED PARTNER",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TertiaryFixed,
                letterSpacing = 0.5.sp
            )
        )
    }
}

@Composable
private fun Thumbnail(product: Product, isPromoted: Boolean) {
    val size = if (isPromoted) 112.dp else 96.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
    ) {
        product.imageUrl?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                contentDescription = product.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        when {
            isPromoted -> Text(
                text = "OEM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(InverseSurface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            product.discountPercent != null -> Text(
                text = "-${product.discountPercent}%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = OnTertiaryFixed
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .background(TertiaryFixed, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun CardHeader(
    product: Product,
    isPromoted: Boolean,
    isCommunity: Boolean,
    isRefurbished: Boolean
) {
    when {
        isPromoted -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = product.partner,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MockPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = null,
                tint = MockPrimary,
                modifier = Modifier.size(14.dp)
            )
        }

        isCommunity || isRefurbished -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            KindChip(
                label = if (isCommunity) "Community Listing" else "Refurbished",
                icon = if (isCommunity) Icons.Default.Handyman else Icons.Default.Autorenew
            )
            DistanceLabel(
                distanceKm = product.distanceKm ?: 0.0,
                color = if (isCommunity) MockPrimary else OnSurfaceVariant,
                useNearMe = isRefurbished
            )
        }

        else -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PartnerChip()
            product.stock?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun PartnerChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SecondaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = null,
            tint = OnSecondaryContainer,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "Official Partner",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = OnSecondaryContainer
            )
        )
    }
}

@Composable
private fun KindChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SurfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Secondary,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
        )
    }
}

@Composable
private fun DistanceLabel(distanceKm: Double, color: Color, useNearMe: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = if (useNearMe) Icons.Default.NearMe else Icons.Default.LocationOn,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "${"%.1f".format(distanceKm)} km",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        )
    }
}

@Composable
private fun RatingRow(product: Product, isPromoted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = TertiaryContainer,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = product.rating.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
        )
        Text(
            text = if (isPromoted) "(${"%,d".format(product.reviews)} reviews)"
            else "(${product.reviews})",
            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant)
        )
    }
}

@Composable
private fun SellerLine(product: Product, isCommunity: Boolean) {
    val text = buildString {
        append("Sold by ")
        append(product.partner)
        if (isCommunity) {
            append(" (★ ${product.rating} · ${product.reviews} fixes)")
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceVariant),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PriceRow(product: Product, isPromoted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${product.currency}${"%.2f".format(product.price)}",
            style = if (isPromoted) {
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            } else {
                MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
        )
        if (!isPromoted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Pill(
                    text = if (product.type == ProductType.REPLACEMENT_PART) "Part" else "Whole",
                    background = SurfaceContainer,
                    contentColor = MockPrimary
                )
                Pill(
                    text = product.condition.label,
                    background = if (product.condition == ProductCondition.NEW) SecondaryContainer else TertiaryFixed,
                    contentColor = if (product.condition == ProductCondition.NEW) OnSecondaryContainer else OnTertiaryFixed
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, contentColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun CardActions(
    product: Product,
    isPromoted: Boolean,
    isCommunity: Boolean,
    isRefurbished: Boolean,
    onOpenProduct: () -> Unit,
    onAddToCart: () -> Unit,
    onBuyNow: () -> Unit,
    onContactSeller: () -> Unit
) {
    val firstName = product.partner.substringBefore(" ")
    when {
        isPromoted -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                label = "View Specs",
                icon = Icons.Default.Visibility,
                background = SurfaceContainerHigh,
                contentColor = OnSurface,
                modifier = Modifier.weight(1f),
                onClick = onOpenProduct
            )
            ActionButton(
                label = "Add to Cart",
                icon = Icons.Default.ShoppingCart,
                background = MockPrimary,
                contentColor = OnPrimary,
                modifier = Modifier.weight(1f),
                onClick = onAddToCart
            )
        }

        isCommunity -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                label = "Chat with $firstName",
                icon = Icons.Default.Chat,
                background = SurfaceContainer,
                contentColor = OnSurface,
                modifier = Modifier.weight(1f),
                onClick = onContactSeller
            )
            ActionButton(
                label = "Pickup Today",
                icon = Icons.Default.DirectionsRun,
                background = Secondary,
                contentColor = OnSecondary,
                modifier = Modifier.weight(1f),
                onClick = onContactSeller
            )
        }

        isRefurbished -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                label = "Message $firstName",
                icon = Icons.Default.Forum,
                background = SurfaceContainer,
                contentColor = OnSurface,
                modifier = Modifier.weight(1f),
                onClick = onContactSeller
            )
            ActionButton(
                label = "Buy Now",
                icon = Icons.Default.ShoppingBag,
                background = MockPrimary,
                contentColor = OnPrimary,
                modifier = Modifier.weight(1f),
                onClick = onBuyNow
            )
        }

        else -> Button(
            onClick = onAddToCart,
            colors = ButtonDefaults.buttonColors(containerColor = MockPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddShoppingCart,
                contentDescription = null,
                tint = OnPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Add to Cart",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = OnPrimary
                )
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = background,
        shadowElevation = if (background == MockPrimary || background == Secondary) 2.dp else 0.dp,
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Bottom sheet with category / brand / price / condition filters. */
@Composable
private fun FilterSheet(viewModel: ProductViewModel, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onClose() }
        )
        Surface(
            onClick = {},
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.categories.forEach { cat ->
                        FilterChip(
                            selected = viewModel.filters.category == cat,
                            onClick = { viewModel.onCategorySelected(cat) },
                            label = { Text(cat) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Brand",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.brands.forEach { brand ->
                        FilterChip(
                            selected = viewModel.filters.brand == brand,
                            onClick = { viewModel.onBrandSelected(brand) },
                            label = { Text(brand) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Price Range",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var min by remember { mutableStateOf(viewModel.filters.minPrice?.toString() ?: "") }
                    var max by remember { mutableStateOf(viewModel.filters.maxPrice?.toString() ?: "") }
                    OutlinedTextField(
                        value = min,
                        onValueChange = {
                            min = it
                            viewModel.onPriceRangeSelected(min.toDoubleOrNull(), max.toDoubleOrNull())
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Min") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = max,
                        onValueChange = {
                            max = it
                            viewModel.onPriceRangeSelected(min.toDoubleOrNull(), max.toDoubleOrNull())
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Max") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Condition",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.conditions.forEach { condition ->
                        ConditionChip(
                            label = condition.label,
                            selected = viewModel.filters.condition == condition,
                            background = if (condition == ProductCondition.NEW) SecondaryContainer else TertiaryFixed,
                            contentColor = if (condition == ProductCondition.NEW) OnSecondaryContainer else OnTertiaryFixed,
                            onClick = { viewModel.onConditionSelected(condition) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.onClearFilters(); onClose() },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            "Clear All",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = MockPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(
                            "Apply",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "No products found",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
        )
        Text(
            text = "Try adjusting your search or filters.",
            style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceVariant),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MockPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Again")
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceVariant),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MockPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retry")
        }
    }
}
