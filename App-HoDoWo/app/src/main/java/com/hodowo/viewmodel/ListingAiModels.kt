package com.hodowo.viewmodel

/** Every field the seller already filled in, sent to the price estimator. */
data class ListingDetails(
    val title: String,
    val description: String,
    val category: String,
    val brand: String,
    val model: String,
    val condition: String,
    val listingKind: String,
    val photoCount: Int,
    val pickup: Boolean,
    val shipping: Boolean,
    val location: String
)

/** AI price range for the sell form, in [currency] units (USD by default). */
data class PriceEstimate(
    val low: Double,
    val high: Double,
    val currency: String = "USD",
    val rationale: String = ""
)

/** Values read from a photo of the model/part label. */
data class ModelScanResult(
    val brand: String?,
    val model: String?,
    val partNumber: String?,
    val confidence: Float,
    val evidence: String?
)
