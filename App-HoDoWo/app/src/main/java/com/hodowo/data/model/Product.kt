package com.hodowo.data.model

/** Type of item shown in the search results. */
enum class ProductType(val label: String, val icon: String) {
    WHOLE_APPLIANCE("Whole Appliances", "kitchen"),
    REPLACEMENT_PART("Replacement Parts", "check_circle")
}

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
    val currency: String = "$",
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
    val discountPercent: Int? = null
)

/** Maps a search result to the `product_type` expected by the cart endpoint. */
fun Product.cartProductType(): String? = when {
    condition == ProductCondition.SECOND_HAND -> "used"
    type == ProductType.REPLACEMENT_PART -> "part"
    else -> "new"
}

/** Result page returned by the repository, using cursor pagination. */
data class ProductPage(
    val products: List<Product>,
    val nextCursor: String?,
    val hasMore: Boolean
)

/** Filters applied to the product search. */
data class ProductFilters(
    val query: String = "",
    val type: ProductType? = null,
    val condition: ProductCondition? = null,
    val category: String? = null,
    val brand: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val fitAppliance: String? = null,
    val maxPriceFilter: Double? = null,
    val underPrice: Double? = null
) {
    fun isEmpty(): Boolean =
        query.isBlank() &&
            type == null &&
            condition == null &&
            category == null &&
            brand == null &&
            minPrice == null &&
            maxPrice == null &&
            fitAppliance == null
}

/** Extension to count active filters. */
fun ProductFilters.activeFilterCount(): Int =
    listOf(
        query.takeIf { it.isNotBlank() },
        type?.label,
        condition?.label,
        category,
        brand,
        minPrice?.toString(),
        maxPrice?.toString()
    ).count { it != null }

/** UI state of the product search screen. */
sealed class ProductUiState {
    object Loading : ProductUiState()
    data class Success(
        val products: List<Product>,
        val page: ProductPage,
        val filters: ProductFilters,
        val isRefreshing: Boolean = false,
        val hasMore: Boolean = true
    ) : ProductUiState()
    data class Error(val message: String, val canRetry: Boolean = true) : ProductUiState()
    object Empty : ProductUiState()
}
