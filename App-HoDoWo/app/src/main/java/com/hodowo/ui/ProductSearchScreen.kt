package com.hodowo.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductType
import com.hodowo.data.model.ProductUiState
import com.hodowo.data.model.activeFilterCount
import com.hodowo.ui.theme.*
import com.hodowo.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSearchScreen(
    viewModel: ProductViewModel,
    onBack: () -> Unit = {},
    onCart: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var filterSheetExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { searchQuery = viewModel.filters.query }

    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ProductTopBar(
                onBack = onBack,
                onCart = onCart,
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it; viewModel.onQueryChanged(it) },
                activeFilterCount = viewModel.filters.activeFilterCount(),
                onFilterPressed = { filterSheetExpanded = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Active filter chips rail (horizontal scroll)
            val activeChips = mutableListOf<String>()
            if (viewModel.filters.query.isNotBlank()) activeChips.add(viewModel.filters.query)
            viewModel.filters.type?.let { activeChips.add(it.label) }
            viewModel.filters.condition?.let { activeChips.add(it.label) }
            viewModel.filters.category?.let { activeChips.add(it) }
            viewModel.filters.brand?.let { activeChips.add(it) }
            viewModel.filters.minPrice?.let { activeChips.add("$${it}") }
            viewModel.filters.maxPrice?.let { activeChips.add("max $${it}") }

            if (activeChips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeChips.forEach { chip ->
                        RemovableFilterChip(label = chip, onRemove = { viewModel.onClearFilters() })
                    }
                }
            }

            // Items Classification (segmented chips)
            ItemTypeSelector(
                types = viewModel.types,
                selected = viewModel.filters.type,
                onSelect = { viewModel.onTypeSelected(it) }
            )

            // Condition Status (chips)
            ConditionSelector(
                conditions = viewModel.conditions,
                selected = viewModel.filters.condition,
                onSelect = { viewModel.onConditionSelected(it) }
            )

            // Results
            when (val s = state) {
                is ProductUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                ProductUiState.Empty -> {
                    EmptyState(onRetry = { viewModel.onRefresh() })
                }
                is ProductUiState.Error -> {
                    ErrorState(message = s.message, onRetry = { viewModel.onRefresh() })
                }
                is ProductUiState.Success -> {
                    if (s.products.isEmpty()) {
                        EmptyState(onRetry = { viewModel.onRefresh() })
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(s.products, key = { it.id }) { product ->
                                ProductCard(product = product, onAddToCart = { onCart() })
                            }
                            if (s.hasMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Primary)
                                    }
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 32.dp)
                        .fillMaxWidth()
                        .heightIn(max = 700.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Category
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.categories.forEach { cat ->
                            FilterChip(
                                selected = viewModel.filters.category == cat,
                                onClick = { viewModel.onCategorySelected(cat) },
                                label = { Text(cat) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (viewModel.filters.category == cat) Primary else SurfaceContainerLow
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Brand
                    Text(
                        text = "Brand",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.brands.forEach { brand ->
                            FilterChip(
                                selected = viewModel.filters.brand == brand,
                                onClick = { viewModel.onBrandSelected(brand) },
                                label = { Text(brand) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (viewModel.filters.brand == brand) Primary else SurfaceContainerLow
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Price range
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
                            onValueChange = { min = it; viewModel.onPriceRangeSelected(min.toDoubleOrNull(), max.toDoubleOrNull()) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Min") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = max,
                            onValueChange = { max = it; viewModel.onPriceRangeSelected(min.toDoubleOrNull(), max.toDoubleOrNull()) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Max") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Condition
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
                                onClick = { viewModel.onConditionSelected(condition) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.onClearFilters(); filterSheetExpanded = false },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Clear All", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = OnSurface)
                        }
                        Button(
                            onClick = { filterSheetExpanded = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Apply", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = OnPrimary)
                        }
                    }
                }

                // Close on background tap
                Box(modifier = Modifier.fillMaxSize().clickable { filterSheetExpanded = false })
            }
        }
    }
}

/** Top bar matching the HTML mockup: back arrow, title, search field, filter button, cart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductTopBar(
    onBack: () -> Unit,
    onCart: () -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    activeFilterCount: Int,
    onFilterPressed: () -> Unit
) {
    TopAppBar(
        modifier = Modifier.fillMaxWidth(),
        title = {
            Text(
                text = "Product Search & Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Go back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Search field
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .background(SurfaceContainerLowest)
                        .clip(RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(20.dp).padding(horizontal = 8.dp)
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { onQueryChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Search parts, part numbers...",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = OnSurfaceVariant
                                )
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceContainerLowest,
                            unfocusedContainerColor = SurfaceContainerLowest,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear query",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp).padding(horizontal = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Filter button with active count badge
                Button(
                    onClick = onFilterPressed,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = "Filters",
                            tint = OnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Filters",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OnPrimary
                            )
                        )
                        if (activeFilterCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge(
                                modifier = Modifier.size(20.dp),
                                content = {
                                    Text(
                                        text = activeFilterCount.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF190064)
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onCart) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Shopping Cart",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

/** Removable filter chip shown in the horizontal rail. */
@Composable
private fun RemovableFilterChip(label: String, onRemove: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = { onRemove() },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceContainer)
    )
}

/** Segmented chip selector for item type (Whole Appliances / Replacement Parts / All). */
@Composable
private fun ItemTypeSelector(
    types: List<ProductType>,
    selected: ProductType?,
    onSelect: (ProductType?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Items Classification",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Secondary,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = "Showing ${if (selected == null) "All" else selected.label}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TypeChip(
                label = "All",
                selected = selected == null,
                icon = "category",
                onClick = { onSelect(null) }
            )
            types.forEach { type ->
                TypeChip(
                    label = type.label,
                    selected = selected == type,
                    icon = type.icon,
                    onClick = { onSelect(type) }
                )
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    icon: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Primary else SurfaceContainerLow,
        border = if (!selected) BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f)) else null,
        modifier = Modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (selected) SurfaceContainerHigh else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (selected) OnPrimary else Color.Transparent)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) OnPrimary else OnSurfaceVariant
                )
            )
        }
    }
}

/** Condition selector chips (Brand New / Second-Hand). */
@Composable
private fun ConditionSelector(
    conditions: List<ProductCondition>,
    selected: ProductCondition?,
    onSelect: (ProductCondition?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Condition Status",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Secondary,
                    letterSpacing = 0.5.sp
                )
            )
            val selectedCount = if (selected == null) conditions.size else 1
            Text(
                text = "$selectedCount Selected",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Primary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            conditions.forEach { condition ->
                ConditionChip(
                    label = condition.label,
                    selected = selected == condition || selected == null,
                    onClick = { onSelect(condition) }
                )
            }
        }
    }
}

@Composable
private fun ConditionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = when (label) {
        "Brand New" -> SecondaryContainer
        "Second-Hand" -> TertiaryContainer
        else -> SurfaceContainerLow
    }
    val textColor = when (label) {
        "Brand New" -> OnSecondaryContainer
        "Second-Hand" -> OnTertiaryContainer
        else -> OnSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) color else SurfaceContainerLow,
        border = if (!selected) BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.5f)) else null,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (selected) Primary else Color.Transparent)
                    .border(
                        BorderStroke(1.dp, if (selected) Primary else OutlineVariant.copy(alpha = 0.5f)),
                        RoundedCornerShape(50.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnPrimary
                        ),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Primary else textColor
                )
            )
        }
    }
}

/** Product result card matching the HTML mockup items. */
@Composable
private fun ProductCard(
    product: Product,
    onAddToCart: () -> Unit
) {
    Surface(
        onClick = {},
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Promoted ribbon for sponsored items
            if (product.sponsored) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFDDB8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Top Promoted Partner",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFDDB8)
                            )
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainerLow)
                ) {
                    product.imageUrl?.let { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                            contentDescription = product.title,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Type badge
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (product.type == ProductType.REPLACEMENT_PART) "OEM" else "Part",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .background(Color(0xFF213145).copy(alpha = 0.85f))
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                // Product details
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                text = product.partner,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                            )
                            if (product.partnerVerified) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        if (product.sponsored) {
                            Text(
                                text = "Sponsored",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TertiaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface
                        ),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            text = "(${product.reviews})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OnSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeBadge(label = product.type.label)
                        ConditionBadge(
                            label = product.condition.label,
                            color = if (product.condition == ProductCondition.NEW) SecondaryContainer else TertiaryContainer,
                            textColor = if (product.condition == ProductCondition.NEW) OnSecondaryContainer else OnTertiaryContainer
                        )
                        product.distanceKm?.let { km ->
                            DistanceChip(distanceKm = km)
                        }
                    }
                }

                // Price column
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${product.currency}${product.price}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                    product.discountPercent?.let { discount ->
                        Text(
                            text = "-$discount%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TertiaryContainer
                            ),
                            modifier = Modifier.background(Color(0xFFFFDDB8).copy(alpha = 0.2f))
                                .clip(RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* view specs */ },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AddShoppingCart,
                            contentDescription = null,
                            tint = OnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "View Specs",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                        )
                    }
                }

                Button(
                    onClick = onAddToCart,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AddShoppingCart,
                            contentDescription = null,
                            tint = OnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add to Cart",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OnPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = OnSurface,
        modifier = Modifier.background(SurfaceContainer).clip(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun ConditionBadge(label: String, color: Color, textColor: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier.background(color).clip(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun DistanceChip(distanceKm: Double) {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "${"%.1f".format(distanceKm)} km",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
            )
        }
    }
}

/** Empty state. */
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
            style = MaterialTheme.typography.bodyMedium.copy(
                color = OnSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Again")
        }
    }
}

/** Error state. */
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
            style = MaterialTheme.typography.bodyMedium.copy(
                color = OnSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retry")
        }
    }
}
