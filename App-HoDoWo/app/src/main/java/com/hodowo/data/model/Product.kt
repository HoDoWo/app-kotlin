package com.hodowo.data.model

import com.hodowo.data.MarketplaceProduct

/** Type of item shown in the search results. */
enum class ProductType(val label: String, val icon: String) {
    WHOLE_APPLIANCE("Whole Appliances", "kitchen"),
    REPLACEMENT_PART("Replacement Parts", "check_circle")
}

/** The segmented chips of the search (#139): each one asks the backend again. */
enum class SearchKind(val label: String, val apiType: String) {
    ALL("All", "all"),
    NEW("New", "new"),
    SECOND_HAND("Second-hand", "used"),
    PARTS("Spare parts", "part")
}

/** Second-hand conditions a listing can have (Backend listings_service.CONDITIONS), for the filter sheet. */
val USED_CONDITIONS: List<Pair<String, String>> = listOf(
    "like-new" to "Like new",
    "used-good" to "Used, good",
    "refurbished" to "Refurbished",
    "parts-only" to "For parts"
)

/** Categories and brands the filter sheet offers: only what is on sale now. */
data class FilterOptions(
    val categories: List<String> = emptyList(),
    val brands: List<String> = emptyList()
)

/** Condition of a product. */
enum class ProductCondition(val label: String) {
    NEW("Brand New"),
    SECOND_HAND("Second-Hand")
}

/** A product returned by the marketplace search. */
data class Product(
    val id: String,
    val title: String,
    val partner: String,
    val partnerVerified: Boolean,
    val price: Double,
    val currency: String = "€",
    val type: ProductType,
    val condition: ProductCondition,
    val imageUrl: String?,
    val rating: Double,
    val reviews: Int,
    val stock: String? = null,
    val distanceKm: Double? = null,
    /** True when this result is a sponsored / promoted placement. */
    val sponsored: Boolean = false,
    /** True when the listing is refurbished (community). */
    val refurbished: Boolean = false,
    /** Optional discount badge shown on second-hand listings. */
    val discountPercent: Int? = null,
    /** The backend search result this row was built from, so the detail page shows all of its data. */
    val source: MarketplaceProduct? = null
)

/**
 * Maps a search result to the `product_type` expected by the cart endpoint. The backend type
 * wins: a brand-new part listed by a user is still "used" (a community listing) for the cart.
 */
fun Product.cartProductType(): String? = source?.type?.takeIf { it in CART_TYPES } ?: when {
    condition == ProductCondition.SECOND_HAND -> "used"
    type == ProductType.REPLACEMENT_PART -> "part"
    else -> "new"
}

private val CART_TYPES = setOf("new", "used", "part")

/** The badge of a result card: who sells it and what it is. */
fun Product.kind(): SearchKind = when (cartProductType()) {
    "used" -> SearchKind.SECOND_HAND
    "part" -> SearchKind.PARTS
    else -> SearchKind.NEW
}

/** Unique key of a result: the same id can exist in two collections. */
fun Product.key(): String = "${cartProductType()}:$id"

/** Result page returned by the repository, using cursor pagination. */
data class ProductPage(
    val products: List<Product>,
    val nextCursor: String?,
    val hasMore: Boolean
)

/** Filters of the product search (#139); all of them are applied by the backend. */
data class ProductFilters(
    val query: String = "",
    val kind: SearchKind = SearchKind.ALL,
    val category: String? = null,
    val brand: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    /** One second-hand condition ("like-new"...); only with [SearchKind.SECOND_HAND]. */
    val usedCondition: String? = null
) {
    fun isEmpty(): Boolean = this == ProductFilters()
}

/** Filters set in the sheet (the query and the chips are shown elsewhere), for its badge. */
fun ProductFilters.activeFilterCount(): Int =
    listOfNotNull(category, brand, minPrice ?: maxPrice, usedCondition).size

/** UI state of the product search screen. */
sealed class ProductUiState {
    object Loading : ProductUiState()
    data class Success(
        val products: List<Product>,
        val filters: ProductFilters,
        /** Pull to refresh: the list stays on screen while it reloads. */
        val isRefreshing: Boolean = false,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        /** The next page failed: the footer offers to try again. */
        val loadMoreFailed: Boolean = false
    ) : ProductUiState()
    data class Error(val message: String, val canRetry: Boolean = true) : ProductUiState()
    object Empty : ProductUiState()
}

/** The product the detail page, the cart and the forum work with (from the search result when there is one). */
fun Product.toMarketplaceProduct(): MarketplaceProduct = source ?: MarketplaceProduct(
    id = id,
    title = title,
    description = "",
    type = when {
        condition == ProductCondition.SECOND_HAND -> "used"
        type == ProductType.REPLACEMENT_PART -> "part"
        else -> "new"
    },
    condition = if (condition == ProductCondition.NEW) "new" else if (refurbished) "refurbished" else "used-good",
    price = price,
    discountPercent = discountPercent,
    sellerName = partner,
    partnerName = partner.takeIf { partnerVerified },
    sponsored = sponsored,
    imageUrl = imageUrl,
    rating = rating,
    reviewCount = reviews,
    distanceKm = distanceKm,
    isPart = type == ProductType.REPLACEMENT_PART,
    isRefurbished = refurbished
)
