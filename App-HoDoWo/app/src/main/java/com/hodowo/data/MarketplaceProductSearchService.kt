package com.hodowo.data

import com.hodowo.data.model.FilterOptions
import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductPage
import com.hodowo.data.model.ProductType
import com.hodowo.data.model.SearchKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Feeds the product search screen (#139) with the marketplace search (`/api/marketplace/search`):
 * official products, second-hand listings and spare parts. Every filter is applied by the
 * backend and the pages follow its cursor, so changing a filter always asks again.
 */
class MarketplaceProductSearchService(
    private val marketplace: MarketplaceService
) : ProductSearchService {

    override fun search(filters: ProductFilters, cursor: String?, pageSize: Int): Flow<ProductPage> = flow {
        val page = marketplace.searchPage(
            query = filters.query,
            type = filters.kind.apiType,
            condition = filters.usedCondition?.takeIf { filters.kind == SearchKind.SECOND_HAND },
            category = filters.category,
            brand = filters.brand,
            minPrice = filters.minPrice,
            maxPrice = filters.maxPrice,
            cursor = cursor,
            limit = pageSize
        )
        emit(
            ProductPage(
                products = page.items.map { it.toProduct() },
                nextCursor = page.nextCursor,
                hasMore = page.nextCursor != null
            )
        )
    }

    /** No invented lists: the sheet offers what the backend has on sale, or nothing. */
    override fun categories(): List<String> = emptyList()

    override fun brands(): List<String> = emptyList()

    override suspend fun filterOptions(): FilterOptions {
        val (categories, brands) = marketplace.searchFilters()
        return FilterOptions(categories, brands)
    }
}

/** One search result as the product search screen shows it. */
internal fun MarketplaceProduct.toProduct(): Product {
    // A community listing is second-hand whatever its condition says ("unopened" included).
    val isNew = type != "used" && (type == "new" || type == "part" || condition.equals("new", true))
    return Product(
        id = id,
        title = title,
        partner = partnerName ?: sellerName,
        partnerVerified = partnerName != null,
        price = price,
        currency = "€",
        type = if (isPart) ProductType.REPLACEMENT_PART else ProductType.WHOLE_APPLIANCE,
        condition = if (isNew) ProductCondition.NEW else ProductCondition.SECOND_HAND,
        imageUrl = imageUrl,
        rating = rating,
        reviews = reviewCount,
        distanceKm = distanceKm,
        sponsored = sponsored,
        refurbished = isRefurbished,
        discountPercent = discountPercent,
        source = this
    )
}
