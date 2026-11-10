package com.hodowo.data

import com.hodowo.viewmodel.PartCompatibility
import com.hodowo.viewmodel.ProductDetail
import com.hodowo.viewmodel.SuggestedPart
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Product Detail of a product the user tapped in the marketplace (app #150).
 *
 * There is no product-detail endpoint yet (Backend#38): the search result the user tapped
 * already has the title, price, photo, seller and specs, so the detail page is built from
 * it. When Backend#38 exists, [RemoteMarketplaceService.getProductDetail] can replace this.
 */
class SelectedProductService(private val product: MarketplaceProduct) : MarketplaceService {
    override suspend fun getProductDetail(id: String): ProductDetail = product.toProductDetail()

    override suspend fun recommendedDemoIds(): List<String> = listOf(product.id)
}

/** The three Product Detail variants (#140) from one search result. */
fun MarketplaceProduct.toProductDetail(): ProductDetail {
    val priceLabel = formatPriceCents((price * CENTS).roundToInt(), "eur")
    val images = listOfNotNull(imageUrl)
    val text = description.takeIf { it.isNotBlank() }
    val specs = listOfNotNull(
        brand?.let { "BRAND" to it },
        model?.let { "MODEL" to it },
        partNumber?.let { "PART NUMBER" to it },
        category?.let { "CATEGORY" to it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } },
        warrantyInfo?.let { "WARRANTY" to it }
    )
    return when (type) {
        "used" -> ProductDetail.SecondHand(
            id = id, title = title, priceLabel = priceLabel, images = images, description = text,
            condition = conditionLabel(condition),
            location = distanceKm?.let { String.format(Locale.US, "%.1f km away", it) } ?: handoverLabel(handoverMethods),
            sellerName = sellerName,
            sellerRating = null,
            postedAgo = ""
        )
        "part" -> ProductDetail.Part(
            id = id, title = title, priceLabel = priceLabel, images = images, description = text,
            sku = partNumber ?: id,
            compatibility = compatibility,
            compatibilityEvidence = null,
            specs = specs
        )
        else -> ProductDetail.New(
            id = id, title = title, priceLabel = priceLabel, images = images, description = text,
            partnerName = partnerName ?: "Official partner",
            rating = rating,
            reviewCount = reviewCount,
            specs = specs,
            matchedAppliance = null,
            questions = emptyList(),
            reviews = emptyList()
        )
    }
}

private const val CENTS = 100

/** The part a diagnosis suggests, so "Buy part" opens its Product Detail page (#149). */
fun SuggestedPart.toMarketplaceProduct(): MarketplaceProduct = MarketplaceProduct(
    id = id,
    title = name,
    description = "",
    type = "part",
    condition = "new",
    price = (priceCents ?: 0) / CENTS.toDouble(),
    imageUrl = imageUrl,
    isPart = true,
    compatibility = compatibility
)

private fun conditionLabel(condition: String): String = when (condition) {
    "unopened" -> "Unopened"
    "like-new" -> "Like new"
    "used-good" -> "Used, good"
    "parts-only" -> "For parts"
    "refurbished" -> "Refurbished"
    else -> condition.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
}

private fun handoverLabel(methods: List<String>): String = when {
    "pickup" in methods && "shipping" in methods -> "Pickup or shipping"
    "pickup" in methods -> "Local pickup"
    "shipping" in methods -> "Shipping available"
    else -> "Ask the seller"
}

/**
 * Cart actions of one Product Detail page on the real cart (Backend#42): errors such as
 * "already sold" are thrown as [CartException] and shown by the screen.
 */
class StoreCartRepository(
    private val cart: CartStore,
    private val productType: String?
) : CartRepository {
    override suspend fun addToCart(productId: String, quantity: Int): Boolean {
        val type = productType ?: throw CartException("This product cannot be bought yet")
        cart.add(type, productId, quantity)
        return true
    }

    /** "Buy now" puts the product in the cart; the screen then opens the cart to pay. */
    override suspend fun buyNow(productId: String): Boolean = addToCart(productId, 1)
}