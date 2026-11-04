package com.hodowo.ui

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
    viewModel: ProductViewModel = viewModel(),
    onBack: () -> Unit = {},
    onCart: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var filterSheetExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { searchQuery = viewModel.filters.query }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Product Search & Filters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; viewModel.onQueryChanged(it) },
                            modifier = Modifier.width(180.dp),
                            singleLine = true,
                            placeholder = { Text("Search...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Primary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerLowest,
                                unfocusedContainerColor = SurfaceContainerLowest,
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = OutlineVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { filterSheetExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "Filters", tint = OnPrimary, modifier = Modifier.size(20.dp))
                            if (viewModel.filters.activeFilterCount() > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge { Text(viewModel.filters.activeFilterCount().toString()) }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onCart) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Active filter chips
            val activeChips = mutableListOf<String>()
            if (viewModel.filters.query.isNotBlank()) activeChips.add(viewModel.filters.query)
            viewModel.filters.type?.let { activeChips.add(it.label) }
            viewModel.filters.condition?.let { activeChips.add(it.label) }
            viewModel.filters.category?.let { activeChips.add(it) }
            viewModel.filters.brand?.let { activeChips.add(it) }
            if (activeChips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeChips.forEach { chip ->
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.onClearFilters() },
                            label = { Text(chip) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = SurfaceContainer)
                        )
                    }
                }
            }

            // Type selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeChip(
                    label = "All",
                    selected = viewModel.filters.type == null,
                    onClick = { viewModel.onTypeSelected(null) }
                )
                viewModel.types.forEach { type ->
                    TypeChip(
                        label = type.label,
                        selected = viewModel.filters.type == type,
                        onClick = { viewModel.onTypeSelected(type) }
                    )
                }
            }

            // Condition selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.conditions.forEach { condition ->
                    ConditionChip(
                        label = condition.label,
                        selected = viewModel.filters.condition == condition,
                        onClick = { viewModel.onConditionSelected(condition) }
                    )
                }
            }

            // Pull to refresh button (simplified)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { viewModel.onRefresh() }) {
                    Text("Refresh", color = Primary)
                }
            }

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
                            contentPadding = PaddingValues(bottom = 16.dp)
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
        ModalBottomSheet(
            onDismissRequest = { filterSheetExpanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .fillMaxWidth()
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Category", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.categories.forEach { cat ->
                        FilterChip(
                            onClick = { viewModel.onCategorySelected(cat) },
                            label = { Text(cat) },
                            selected = viewModel.filters.category == cat,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (viewModel.filters.category == cat) Primary else SurfaceContainerLow
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Brand", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.brands.forEach { brand ->
                        FilterChip(
                            onClick = { viewModel.onBrandSelected(brand) },
                            label = { Text(brand) },
                            selected = viewModel.filters.brand == brand,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (viewModel.filters.brand == brand) Primary else SurfaceContainerLow
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text("Price Range", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.Bold)
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

                Text("Condition", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, fontWeight = FontWeight.Bold)
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
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Clear All", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = OnSurface)
                    }
                    Button(
                        onClick = { filterSheetExpanded = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Apply", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = OnPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
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
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) OnPrimary else OnSurfaceVariant
            )
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
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Primary else textColor
            )
        }
    }
}

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
            if (product.sponsored) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFDDB8), modifier = Modifier.size(16.dp))
                        Text("Top Promoted Partner", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFDDB8))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Text(
                        text = if (product.type == ProductType.REPLACEMENT_PART) "OEM" else "Part",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color(0xFF213145).copy(alpha = 0.85f))
                            .clip(RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = product.partner,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        if (product.sponsored) {
                            Text("Sponsored", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TertiaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = TertiaryContainer, modifier = Modifier.size(14.dp))
                        Text(product.rating.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OnSurface)
                        Text("(${product.reviews})", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeBadge(label = product.type.label)
                        ConditionBadge(label = product.condition.label, color = if (product.condition == ProductCondition.NEW) SecondaryContainer else TertiaryContainer, textColor = if (product.condition == ProductCondition.NEW) OnSecondaryContainer else OnTertiaryContainer)
                        product.distanceKm?.let { km ->
                            Text("${"%.1f".format(km)} km", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = OnSurface, modifier = Modifier.background(SurfaceContainerHigh).clip(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${product.currency}${product.price}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    product.discountPercent?.let { discount ->
                        Text("-$discount%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TertiaryContainer, modifier = Modifier.background(Color(0xFFFFDDB8).copy(alpha = 0.2f)).clip(RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("View Specs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnSurface)
                }
                Button(
                    onClick = onAddToCart,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("Add to Cart", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OnPrimary)
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
private fun EmptyState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("No products found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = OnSurface)
        Text("Try adjusting your search or filters.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
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
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = OnSurface)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Retry")
        }
    }
}
