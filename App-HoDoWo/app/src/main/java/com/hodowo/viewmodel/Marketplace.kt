package com.hodowo.viewmodel

/** Whether a spare part fits the user's appliance (issue #140). */
enum class PartCompatibility { FITS, DOES_NOT_FIT, UNKNOWN }

/**
 * One marketplace item, in one of three shapes (issue #140): an official new
 * product, a user's second-hand listing, or a spare part.
 */
sealed interface ProductDetail {
    val id: String
    val title: String
    val priceLabel: String
    val images: List<String>
    val description: String?

    /** Official product sold by a partner, with social proof (community Q&A, reviews). */
    data class New(
        override val id: String,
        override val title: String,
        override val priceLabel: String,
        override val images: List<String>,
        override val description: String?,
        val partnerName: String,
        val rating: Double,
        val reviewCount: Int,
        val specs: List<Pair<String, String>>,
        val matchedAppliance: String?,
        val questions: List<CommunityQuestion>,
        val reviews: List<CustomerReview>
    ) : ProductDetail

    /** A user's second-hand listing. */
    data class SecondHand(
        override val id: String,
        override val title: String,
        override val priceLabel: String,
        override val images: List<String>,
        override val description: String?,
        val condition: String,
        val location: String,
        val sellerName: String,
        val sellerRating: Double?,
        val postedAgo: String
    ) : ProductDetail

    /** A spare part, with SKU and compatibility with the user's appliance. */
    data class Part(
        override val id: String,
        override val title: String,
        override val priceLabel: String,
        override val images: List<String>,
        override val description: String?,
        val sku: String,
        val compatibility: PartCompatibility,
        val compatibilityEvidence: String?,
        val specs: List<Pair<String, String>>
    ) : ProductDetail
}

data class CommunityQuestion(
    val question: String,
    val answererName: String,
    val answererBadge: String?,
    val answer: String,
    val upvotes: Int,
    val postedAgo: String
)

data class CustomerReview(
    val authorName: String,
    val rating: Int,
    val verifiedPurchase: Boolean,
    val body: String,
    val postedAgo: String
)
