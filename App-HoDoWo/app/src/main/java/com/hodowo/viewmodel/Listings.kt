package com.hodowo.viewmodel

/** Whether a seller's listing is still for sale or already sold (issue #146). */
enum class ListingStatus { ACTIVE, SOLD }

/** One of the signed-in user's second-hand listings, as shown in "My Listings". */
data class Listing(
    val id: String,
    val title: String,
    val description: String,
    val condition: String,
    val priceLabel: String,
    val status: ListingStatus,
    val inquiryCount: Int,
    val postedAgo: String
)

/**
 * Normalises what the seller typed in the price field into a "$12.34" label.
 * Numeric input is shown with two decimals; anything else is kept as typed, and blank means "$0.00".
 */
fun formatPriceLabel(input: String): String {
    val raw = input.trim().removePrefix("$").trim()
    if (raw.isEmpty()) return "$0.00"
    val amount = raw.replace(',', '.').toBigDecimalOrNull() ?: return "$$raw"
    return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
}

/** "1 inquiry" / "2 inquiries". */
fun inquiryLabel(count: Int): String = if (count == 1) "1 inquiry" else "$count inquiries"
