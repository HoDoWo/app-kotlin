package com.hodowo.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.Outline
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hodowo.data.model.FilterOptions
import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductUiState
import com.hodowo.data.model.SearchKind
import com.hodowo.data.model.USED_CONDITIONS
import com.hodowo.data.model.activeFilterCount
import com.hodowo.data.model.key
import com.hodowo.data.model.kind
import com.hodowo.ui.theme.Background
import com.hodowo.ui.theme.OnPrimary
import com.hodowo.ui.theme.OnSecondaryContainer
import com.hodowo.ui.theme.OnSurface
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.OnTertiaryFixed
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SurfaceContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.ui.theme.TertiaryContainer
import com.hodowo.ui.theme.TertiaryFixed
import com.hodowo.viewmodel.ActiveFilter
import com.hodowo.viewmodel.ProductViewModel
import java.util.Locale

// The deep indigo of the HoDoWo mockups.
private val SearchPrimary = Color(0xFF26008C)
private val SearchPrimaryContainer = Color(0xFF3B02C7)

/**
 * Product search (#139): search box, type chips (all / new / second-hand / spare parts),
 * removable chips of the active filters, the filter sheet, paged results and pull to refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSearchScreen(
    viewModel: ProductViewModel,
    onBack: () -> Unit = {},
    onCart: () -> Unit = {},
    onProductClick: (Product) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val filters by viewModel.filtersState.collectAsState()
    val options by viewModel.filterOptions.collectAsState()
    val cartCount by viewModel.cartCount.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    // Kept across rotation; the ViewModel keeps the search itself.
    var searchQuery by rememberSaveable { mutableStateOf(viewModel.filters.query) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        SearchHeader(
            query = searchQuery,
            onQueryChange = {
                searchQuery = it
                viewModel.onQueryChanged(it)
            },
            onClearQuery = {
                searchQuery = ""
                viewModel.onQueryChanged("")
            },
            onBack = onBack,
            onCart = onCart,
            cartCount = cartCount,
            sheetFilterCount = filters.activeFilterCount(),
            onOpenFilters = { showFilters = true }
        )
        KindChips(selected = filters.kind, onSelect = viewModel::onKindSelected)
        ActiveFilterChips(filters = filters, onRemove = viewModel::removeFilter)

        val isRefreshing = (state as? ProductUiState.Success)?.isRefreshing == true
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when (val s = state) {
                ProductUiState.Loading -> CenteredBox { CircularProgressIndicator(color = SearchPrimary) }
                ProductUiState.Empty -> StateMessage(
                    icon = Icons.Default.SearchOff,
                    title = "No products found",
                    text = if (filters.isEmpty()) "Nothing is on sale right now." else "Try other words or fewer filters.",
                    action = if (filters.activeFilterCount() > 0) "Clear filters" else "Try again",
                    onAction = { if (filters.activeFilterCount() > 0) viewModel.onClearFilters() else viewModel.retry() }
                )
                is ProductUiState.Error -> StateMessage(
                    icon = Icons.Default.WifiOff,
                    title = "Something went wrong",
                    text = s.message,
                    action = if (s.canRetry) "Retry" else null,
                    onAction = viewModel::retry
                )
                is ProductUiState.Success -> ResultList(
                    state = s,
                    onOpen = onProductClick,
                    onAddToCart = viewModel::addToCart,
                    onLoadMore = viewModel::loadMore
                )
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            filters = filters,
            options = options,
            onApply = { category, brand, min, max, condition ->
                viewModel.applyFilters(category, brand, min, max, condition)
                showFilters = false
            },
            onClear = {
                viewModel.onClearFilters()
                showFilters = false
            },
            onDismiss = { showFilters = false }
        )
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    onCart: () -> Unit,
    cartCount: Int,
    sheetFilterCount: Int,
    onOpenFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = OnSurface)
            }
            Text(
                text = "Search the marketplace",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCart) {
                // The badge sits on the icon's corner, inside the button (it used to be cut off).
                BadgedBox(
                    badge = {
                        if (cartCount > 0) {
                            Badge(containerColor = SearchPrimary) {
                                Text(if (cartCount > 9) "9+" else cartCount.toString(), color = OnPrimary)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", tint = OnSurfaceVariant)
                }
            }
        }
        // Same compact bar as the Market home: one line, small text.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Outline, modifier = Modifier.size(20.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Model code, part or appliance…",
                                color = Outline,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, color = OnSurface),
                            cursorBrush = SolidColor(SearchPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Surface(
                onClick = onOpenFilters,
                shape = RoundedCornerShape(12.dp),
                color = if (sheetFilterCount > 0) PrimaryFixed else SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    BadgedBox(
                        badge = {
                            if (sheetFilterCount > 0) {
                                Badge(containerColor = SearchPrimary) { Text("$sheetFilterCount", color = OnPrimary) }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Filters",
                            tint = if (sheetFilterCount > 0) SearchPrimary else OnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Segmented chips: each one asks the backend again (#139). */
@Composable
private fun KindChips(selected: SearchKind, onSelect: (SearchKind) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchKind.entries.forEach { kind ->
            val isSelected = kind == selected
            Surface(
                onClick = { onSelect(kind) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) SearchPrimaryContainer else SurfaceContainer,
                shadowElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) OnPrimary else OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** The sheet filters in use, each with its own "x". */
@Composable
private fun ActiveFilterChips(filters: ProductFilters, onRemove: (ActiveFilter) -> Unit) {
    val chips = buildList {
        filters.category?.let { add(ActiveFilter.CATEGORY to it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }) }
        filters.brand?.let { add(ActiveFilter.BRAND to it) }
        priceLabel(filters.minPrice, filters.maxPrice)?.let { add(ActiveFilter.PRICE to it) }
        filters.usedCondition?.let { key ->
            add(ActiveFilter.CONDITION to (USED_CONDITIONS.firstOrNull { it.first == key }?.second ?: key))
        }
    }
    if (chips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (filter, label) ->
            Surface(
                onClick = { onRemove(filter) },
                shape = RoundedCornerShape(50),
                color = SecondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = OnSecondaryContainer)
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove $label",
                        tint = OnSecondaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun priceLabel(min: Double?, max: Double?): String? = when {
    min != null && max != null -> "${euro(min)} – ${euro(max)}"
    min != null -> "From ${euro(min)}"
    max != null -> "Up to ${euro(max)}"
    else -> null
}

private fun euro(value: Double): String =
    if (value % 1.0 == 0.0) "€${value.toLong()}" else "€" + String.format(Locale.ROOT, "%.2f", value)

@Composable
private fun ResultList(
    state: ProductUiState.Success,
    onOpen: (Product) -> Unit,
    onAddToCart: (Product) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    // The next page starts loading a few rows before the end.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.products.size, state.hasMore) {
        if (nearEnd && state.hasMore && !state.loadMoreFailed) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "${state.products.size}${if (state.hasMore) "+" else ""} results",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant
            )
        }
        items(state.products, key = { it.key() }) { product ->
            ResultCard(product = product, onOpen = { onOpen(product) }, onAddToCart = { onAddToCart(product) })
        }
        if (state.isLoadingMore) {
            item { CenteredBox(Modifier.padding(16.dp)) { CircularProgressIndicator(color = SearchPrimary) } }
        } else if (state.loadMoreFailed) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Couldn't load more results", color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onLoadMore) { Text("Try again", color = SearchPrimary) }
                }
            }
        } else if (!state.hasMore && state.products.size > ProductViewModel.PAGE_SIZE) {
            item {
                Text(
                    "That's everything",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }
    }
}

/** A result: photo, type badge, sponsored label, title, who sells it, rating (if any) and price. */
@Composable
private fun ResultCard(product: Product, onOpen: () -> Unit, onAddToCart: () -> Unit) {
    val kind = product.kind()
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (product.sponsored) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SearchPrimary)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = TertiaryFixed, modifier = Modifier.size(14.dp))
                    Text(
                        "SPONSORED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = TertiaryFixed
                    )
                }
            }
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    if (product.imageUrl != null) {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = OutlineVariant)
                    }
                    product.discountPercent?.takeIf { it > 0 }?.let {
                        Text(
                            "-$it%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnTertiaryFixed,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .background(TertiaryFixed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    KindBadge(kind, refurbished = product.refurbished)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (product.partnerVerified) {
                            Icon(Icons.Default.Verified, contentDescription = "Official partner", tint = SearchPrimary, modifier = Modifier.size(13.dp))
                        }
                        Text(
                            text = if (product.partnerVerified) product.partner else "Sold by ${product.partner}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (product.partnerVerified) SearchPrimary else OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Only real reviews: no stars without them.
                    if (product.reviews > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = TertiaryContainer, modifier = Modifier.size(13.dp))
                            Text(
                                text = String.format(Locale.ROOT, "%.1f (%d)", product.rating, product.reviews),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = euro(product.price),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = onAddToCart,
                            shape = RoundedCornerShape(10.dp),
                            color = SearchPrimary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AddShoppingCart,
                                    contentDescription = "Add to cart",
                                    tint = OnPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindBadge(kind: SearchKind, refurbished: Boolean) {
    val (label, background, content, icon) = when (kind) {
        SearchKind.SECOND_HAND -> Badge(
            if (refurbished) "Refurbished" else "Second-hand", TertiaryFixed, OnTertiaryFixed, Icons.Default.Autorenew
        )
        SearchKind.PARTS -> Badge("Spare part", SurfaceContainerHigh, SearchPrimary, Icons.Default.Build)
        else -> Badge("New", SecondaryContainer, OnSecondaryContainer, Icons.Default.Verified)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(11.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = content)
    }
}

private data class Badge(val label: String, val background: Color, val content: Color, val icon: ImageVector)

/** Bottom sheet: category, brand, price range and, for second-hand only, the condition. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: ProductFilters,
    options: FilterOptions,
    onApply: (category: String?, brand: String?, min: Double?, max: Double?, condition: String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    // A draft: nothing is searched until "Show results".
    var category by rememberSaveable { mutableStateOf(filters.category) }
    var brand by rememberSaveable { mutableStateOf(filters.brand) }
    var min by rememberSaveable { mutableStateOf(filters.minPrice?.let(::plainNumber).orEmpty()) }
    var max by rememberSaveable { mutableStateOf(filters.maxPrice?.let(::plainNumber).orEmpty()) }
    var condition by rememberSaveable { mutableStateOf(filters.usedCondition) }
    val secondHand = filters.kind == SearchKind.SECOND_HAND

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            SheetSection("Category") {
                if (options.categories.isEmpty()) {
                    SheetHint("No categories available right now")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        options.categories.forEach { value ->
                            SheetChip(
                                label = value.replaceFirstChar { it.titlecase(Locale.ROOT) },
                                selected = category.equals(value, ignoreCase = true),
                                onClick = { category = if (category.equals(value, ignoreCase = true)) null else value }
                            )
                        }
                    }
                }
            }

            SheetSection("Brand") {
                if (options.brands.isEmpty()) {
                    SheetHint("No brands available right now")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        options.brands.forEach { value ->
                            SheetChip(
                                label = value,
                                selected = brand.equals(value, ignoreCase = true),
                                onClick = { brand = if (brand.equals(value, ignoreCase = true)) null else value }
                            )
                        }
                    }
                }
            }

            SheetSection("Price (€)") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PriceField("Min", min, { min = it }, Modifier.weight(1f))
                    PriceField("Max", max, { max = it }, Modifier.weight(1f))
                }
            }

            SheetSection("Condition") {
                if (!secondHand) {
                    SheetHint("Choose \"Second-hand\" above to filter by condition")
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        USED_CONDITIONS.forEach { (key, label) ->
                            SheetChip(
                                label = label,
                                selected = condition == key,
                                onClick = { condition = if (condition == key) null else key }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onClear,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OutlineVariant),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Clear all", color = OnSurface) }
                Button(
                    onClick = {
                        onApply(category, brand, parsePrice(min), parsePrice(max), condition.takeIf { secondHand })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SearchPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Show results", color = OnPrimary, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = OnSurfaceVariant)
        content()
    }
}

@Composable
private fun SheetHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
}

@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SearchPrimaryContainer,
            selectedLabelColor = OnPrimary
        )
    )
}

@Composable
private fun PriceField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == '.' || it == ',' }.take(8)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}

private fun parsePrice(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

private fun plainNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) { content() }
}

/** Empty and error states; inside a scrollable column so pull to refresh works on them too. */
@Composable
private fun StateMessage(
    icon: ImageVector,
    title: String,
    text: String,
    action: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(SurfaceContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = OnSurfaceVariant) }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = OnSurface)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.width(1.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = SearchPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text(action, color = OnPrimary) }
        }
    }
}
