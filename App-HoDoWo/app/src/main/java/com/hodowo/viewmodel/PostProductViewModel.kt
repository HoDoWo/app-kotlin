package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.FirebaseRepository
import com.hodowo.data.MarketplaceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostProductViewModel(
    private val service: MarketplaceService,
    private val repository: FirebaseRepository
) : ViewModel() {
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun submit(
        userId: String,
        title: String,
        description: String,
        category: String,
        brand: String,
        model: String,
        partNumber: String,
        listingKind: String,
        condition: String,
        priceText: String,
        imageUris: List<android.net.Uri>,
        diagnosticMediaUris: List<android.net.Uri>,
        handoverMethods: List<String>,
        pickupZip: String,
        shippingPriceCents: Int?,
        onSuccess: () -> Unit
    ) {
        val price = priceText.toDoubleOrNull()
        if (title.trim().length < 3 || description.trim().length < 30 || category.isBlank() ||
            price == null || price <= 0 ||
            imageUris.isEmpty() || imageUris.size > 8 || diagnosticMediaUris.size > 8 ||
            handoverMethods.isEmpty() ||
            (handoverMethods.contains("pickup") && pickupZip.isBlank())
        ) {
            _message.value = "Add the required details, 1–8 photos, a valid price, and a handover method."
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _message.value = null
            runCatching {
                val urls = imageUris.map { repository.uploadMarketplaceImage(userId, it) }
                val diagnosticUrls = diagnosticMediaUris.map { repository.uploadMarketplaceImage(userId, it) }
                service.createListing(
                    title, description, category, brand, model, partNumber, listingKind,
                    condition, price, urls, diagnosticUrls, handoverMethods, pickupZip, shippingPriceCents
                )
            }.onSuccess {
                _message.value = "Listing published"
                onSuccess()
            }.onFailure { _message.value = it.message ?: "Could not publish listing" }
            _isSubmitting.value = false
        }
    }
}

/**
 * Local "AI Polish" for a listing narrative: trims, collapses repeated whitespace,
 * capitalises the first letter of every sentence and makes sure the text ends
 * with a full stop. It runs on the device, so the button works offline too.
 */
fun polishListingDescription(raw: String): String {
    val cleaned = raw.trim().replace(Regex("\\s+"), " ")
    if (cleaned.isEmpty()) return cleaned

    val capitalised = buildString {
        var startOfSentence = true
        cleaned.forEach { char ->
            if (startOfSentence && char.isLetter()) {
                append(char.uppercaseChar())
                startOfSentence = false
            } else {
                append(char)
                if (char == '.' || char == '!' || char == '?') startOfSentence = true
            }
        }
    }
    return if (capitalised.last() in ".!?") capitalised else "$capitalised."
}
