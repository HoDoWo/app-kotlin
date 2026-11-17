package com.hodowo.data

import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductPage
import com.hodowo.data.model.ProductType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Feeds the product search screen with the marketplace search (`/api/marketplace/search`):
 * official products, second-hand listings and spare parts, with the filters applied by the
 * backend. The backend answers with one list, so the screen gets a single page.
 */
class MarketplaceProductSearchService(
    private val marketplace: MarketplaceService
) : ProductSearchService {

    override fun search(filters: ProductFilters, cursor: String?, pageSize: Int): Flow<ProductPage> = flow {
        val found = marketplace.search(
            query = filters.query,
            type = when (filters.type) {
                ProductType.REPLACEMENT_PART -> "part"
                ProductType.WHOLE_APPLIANCE -> "appliance"
                null -> "all"
            },
            condition = when (filters.condition) {
                ProductCondition.NEW -> "new"
                ProductCondition.SECOND_HAND -> "used"
                null -> null
            },
            category = filters.category,
            brand = filters.brand,
            maxPrice = filters.maxPrice
        )
        val products = found.map { it.toProduct() }.filter { filters.minPrice == null || it.price >= filters.minPrice }
        emit(ProductPage(products = products, nextCursor = null, hasMore = false))
    }

    override fun categories(): List<String> = CATEGORIES

    override fun brands(): List<String> = BRANDS

    private companion object {
        val CATEGORIES = listOf("Pumps", "Filters", "Hoses", "Seals", "Motors", "Electronics")
        val BRANDS = listOf("Bosch", "Siemens", "Universal", "OEM", "Community")
    }
}

/** One search result as the product search screen shows it. */
internal fun MarketplaceProduct.toProduct(): Product {
    val isNew = type == "new" || condition.equals("new", true) || condition.equals("unopened", true)
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
