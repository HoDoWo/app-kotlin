package com.hodowo.data

import com.hodowo.data.model.Product
import com.hodowo.data.model.ProductCondition
import com.hodowo.data.model.ProductFilters
import com.hodowo.data.model.ProductPage
import com.hodowo.data.model.ProductType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/** Source of the product search screen data (demo now, backend through [RemoteProductSearchService]). */
interface ProductSearchService {
    fun search(
        filters: ProductFilters,
        cursor: String? = null,
        pageSize: Int = 10
    ): Flow<ProductPage>

    fun categories(): List<String>
    fun brands(): List<String>
}

/**
 * Repository for the marketplace product search.
 *
 * Provides demo data backed by cursor pagination and filters. In a real
 * implementation this would call the backend (see HoDoWo/Backend#40).
 */
class ProductRepository(private val delayMillis: Long = 600) : ProductSearchService {

    private val products = buildList {
        add(
            Product(
                id = "bosch-pump-001",
                title = "Bosch Genuine OEM Drain Pump Motor WAN/WAE",
                partner = "Bosch Official Store",
                partnerVerified = true,
                price = 38.50,
                type = ProductType.REPLACEMENT_PART,
                condition = ProductCondition.NEW,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAVSdamDabayAgAr75y9_5WrJXXpAEbljWm-D4UcksWO2SkfuBCrwDnseBFWB4-E5fOslVSy64plsUDNxOlhZYCkZy7OOtclyqj70xM_KcAfH17kDLern1fikDBqBEsBbzCUNvVXuvVJFwW41Y1nzVsSyWWi7oymhdqv39mgJFqewgl7Mrx35hsseGuk2VhqeiLNJmes-0VeSqQS6h501TyqQRfKjpgarwRlpyctXo",
                rating = 4.9,
                reviews = 1240,
                stock = "In Stock",
                sponsored = true
            )
        )
        add(
            Product(
                id = "bosch-flap-002",
                title = "Bosch Universal Drain Flap & Seal Assembly",
                partner = "Official Partner",
                partnerVerified = true,
                price = 14.99,
                type = ProductType.REPLACEMENT_PART,
                condition = ProductCondition.NEW,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB69D8uZdiih_iVSm1D9541xxSGfukgSozY4RPyPijF3jtZ4buBfYDwm5j08eCt6wsESCSrkMKy2HlxMV-4umkcAiyTD4WqVbI3IkiQdpN3nScITY1ymPSLshdTVfdwPfAuDg4SnxvJHLb55cdI64HErAQnjRKY_1gxOm75kF6uN8wrJTwafsUu7jWvbgQSUd2DxzNL-7rf5W8Mhlyg9r475Imv_kPfAtGZePnHhfg",
                rating = 4.8,
                reviews = 430,
                stock = "In Stock",
                sponsored = false
            )
        )
        add(
            Product(
                id = "community-pump-003",
                title = "OEM Drain Pump Motor (Pulled from Bosch Serie 4)",
                partner = "Marco V.",
                partnerVerified = false,
                price = 18.00,
                type = ProductType.REPLACEMENT_PART,
                condition = ProductCondition.SECOND_HAND,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBc8GkpxgUMFD5W6pEp3PRVcKWDuFR6KmVb2Nn6Au9R1lMcq6zmZwl2iYZ760zWKZ4FcqxYdIWQ3wNZYIGmbV2-rNkmreM68AYmJ07TYvjmKER2uAcNwV0l6zbVCEHFa7WlLDuibYbMSB2jugBxPYMCE_kgYwJDOhAGO2umkSascynvHDf9Fh4UmGp64LtG1qctLJZRsUIpQZWUU0--9mCORuShoRCPtdLpronpY7k",
                rating = 5.0,
                reviews = 34,
                stock = null,
                distanceKm = 1.8,
                discountPercent = 53,
                sponsored = false
            )
        )
        add(
            Product(
                id = "community-filter-004",
                title = "Cleaned Drain Filter Chamber & Plug Cap",
                partner = "Elena R.",
                partnerVerified = false,
                price = 12.00,
                type = ProductType.REPLACEMENT_PART,
                condition = ProductCondition.SECOND_HAND,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB7YHgf622GxfpKgVcATGSIzbcauLdCNaM7AdsASeg0lAfeFIQt2KAdsZtUI4_g9XOX0DqCG6XsgiA4k4I8llJnRFLJF2HU3qzeDUC0qY7IkCN7FM5qqEcTiH6Tikek62olHwe5O-ZXMKJhXikSHkihVymxCZro7d4GDje3K3uIc42Tpvk9zzvulO917YqlVfCuyELnW-2OfM2EyfLZ-4TXxQxNwxZIsX1BfyZ2SoY",
                rating = 4.7,
                reviews = 18,
                stock = null,
                distanceKm = 4.1,
                sponsored = false
            )
        )
        add(
            Product(
                id = "bosch-motor-005",
                title = "Bosch Serie 4 Washing Machine Motor",
                partner = "Bosch Official Store",
                partnerVerified = true,
                price = 119.00,
                type = ProductType.WHOLE_APPLIANCE,
                condition = ProductCondition.NEW,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAVSdamDabayAgAr75y9_5WrJXXpAEbljWm-D4UcksWO2SkfuBCrwDnseBFWB4-E5fOslVSy64plsUDNxOlhZYCkZy7OOtclyqj70xM_KcAfH17kDLern1fikDBqBEsBbzCUNvVXuvVJFwW41Y1nzVsSyWWi7oymhdqv39mgJFqewgl7Mrx35hsseGuk2VhqeiLNJmes-0VeSqQS6h501TyqQRfKjpgarwRlpyctXo",
                rating = 4.9,
                reviews = 890,
                stock = "In Stock",
                sponsored = true
            )
        )
        add(
            Product(
                id = "bosch-hose-006",
                title = "Bosch Drain Hose Extension 2m",
                partner = "Official Partner",
                partnerVerified = true,
                price = 22.50,
                type = ProductType.REPLACEMENT_PART,
                condition = ProductCondition.NEW,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB69D8uZdiih_iVSm1D9541xxSGfukgSozY4RPyPijF3jtZ4buBfYDwm5j08eCt6wsESCSrkMKy2HlxMV-4umkcAiyTD4WqVbI3IkiQdpN3nScITY1ymPSLshdTVfdwPfAuDg4SnxvJHLb55cdI64HErAQnjRKY_1gxOm75kF6uN8wrJTwafsUu7jWvbgQSUd2DxzNL-7rf5W8Mhlyg9r475Imv_kPfAtGZePnHhfg",
                rating = 4.6,
                reviews = 210,
                stock = "In Stock",
                sponsored = false
            )
        )
        add(
            Product(
                id = "community-seal-007",
                title = "Used Washing Machine Door Seal Gasket",
                partner = "Hans L.",
                partnerVerified = false,
                price = 9.50,
                type = ProductType.REPLACEMENT_PART,
                condition = ProductCondition.SECOND_HAND,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBc8GkpxgUMFD5W6pEp3PRVcKWDuFR6KmVb2Nn6Au9R1lMcq6zmZwl2iYZ760zWKZ4FcqxYdIWQ3wNZYIGmbV2-rNkmreM68AYmJ07TYvjmKER2uAcNwV0l6zbVCEHFa7WlLDuibYbMSB2jugBxPYMCE_kgYwJDOhAGO2umkSascynvHDf9Fh4UmGp64LtG1qctLJZRsUIpQZWUU0--9mCORuShoRCPtdLpronpY7k",
                rating = 4.4,
                reviews = 12,
                stock = null,
                distanceKm = 3.2,
                discountPercent = 40,
                sponsored = false
            )
        )
        add(
            Product(
                id = "bosch-filter-008",
                title = "Bosch Pump Filter Cover Cap",
                partner = "Bosch Official Store",
                partnerVerified = true,
                price = 7.99,
                type = ProductType.REPLACEMENT_PART,
                condition = ProductCondition.NEW,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuB7YHgf622GxfpKgVcATGSIzbcauLdCNaM7AdsASeg0lAfeFIQt2KAdsZtUI4_g9XOX0DqCG6XsgiA4k4I8llJnRFLJF2HU3qzeDUC0qY7IkCN7FM5qqEcTiH6Tikek62olHwe5O-ZXMKJhXikSHkihVymxCZro7d4GDje3K3uIc42Tpvk9zzvulO917YqlVfCuyELnW-2OfM2EyfLZ-4TXxQxNwxZIsX1BfyZ2SoY",
                rating = 4.5,
                reviews = 320,
                stock = "Low Stock",
                sponsored = false
            )
        )
    }

    private val categories = listOf("Pumps", "Filters", "Hoses", "Seals", "Motors", "Electronics")
    private val brands = listOf("Bosch", "Siemens", "Universal", "OEM", "Community")

    /**
     * Searches products matching [filters], using cursor pagination.
     *
     * Changing the [type] filter re-queries the backend instead of filtering
     * locally (the repository applies it server-side).
     */
    override fun search(
        filters: ProductFilters,
        cursor: String?,
        pageSize: Int
    ): Flow<ProductPage> = flow {
        delay(delayMillis)

        val offset = if (cursor == null) 0 else cursor.toIntOrNull() ?: 0

        var filtered = products.filter { product ->
            val matchesQuery = filters.query.isBlank() ||
                product.title.contains(filters.query, ignoreCase = true) ||
                product.partner.contains(filters.query, ignoreCase = true)

            val matchesType = filters.type == null || product.type == filters.type
            val matchesCondition = filters.condition == null || product.condition == filters.condition
            val matchesCategory = filters.category == null || categories.any { it == filters.category }
            val matchesBrand = filters.brand == null || brands.any { b ->
                b == filters.brand && product.partner.contains(b, ignoreCase = true)
            }
            val matchesPrice = (filters.minPrice == null || product.price >= filters.minPrice!!) &&
                (filters.maxPrice == null || product.price <= filters.maxPrice!!)

            matchesQuery && matchesType && matchesCondition && matchesCategory &&
                matchesBrand && matchesPrice
        }

        // Sponsored items first, then by relevance.
        filtered = filtered.sortedByDescending { it.sponsored }.sortedByDescending { it.rating }

        val pageStart = offset.coerceIn(0, filtered.size)
        val pageEnd = (pageStart + pageSize).coerceAtMost(filtered.size)
        val pageProducts = if (pageStart < filtered.size) filtered.subList(pageStart, pageEnd) else emptyList()

        val nextCursor = if (pageEnd < filtered.size) pageEnd.toString() else null

        emit(
            ProductPage(
                products = pageProducts,
                nextCursor = nextCursor,
                hasMore = nextCursor != null
            )
        )
    }

    override fun categories(): List<String> = categories
    override fun brands(): List<String> = brands

    companion object {
        private val random = Random(System.currentTimeMillis())
    }
}
