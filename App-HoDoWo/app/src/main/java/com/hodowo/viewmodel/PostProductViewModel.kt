package com.hodowo.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.MarketplaceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostProductViewModel(
    private val service: MarketplaceService,
    private val aiService: HoDoWoAiService,
    /** Reads and compresses a picked photo so it can be uploaded. */
    private val loadImage: suspend (Uri) -> ByteArray
) : ViewModel() {
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isPolishing = MutableStateFlow(false)
    val isPolishing: StateFlow<Boolean> = _isPolishing.asStateFlow()
    private val _isEstimating = MutableStateFlow(false)
    val isEstimating: StateFlow<Boolean> = _isEstimating.asStateFlow()
    private val _isScanningLabel = MutableStateFlow(false)
    val isScanningLabel: StateFlow<Boolean> = _isScanningLabel.asStateFlow()

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
        imageUris: List<Uri>,
        diagnosticMediaUris: List<Uri>,
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
                val urls = imageUris.map { service.uploadListingImage(loadImage(it)) }
                val diagnosticUrls = diagnosticMediaUris.map { service.uploadListingImage(loadImage(it)) }
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

    /**
     * Rewrites [text] with the AI. If the backend is unreachable, the local
     * tidy-up still gives the button a visible effect.
     */
    fun polishDescription(text: String, onResult: (String) -> Unit) {
        if (text.isBlank() || _isPolishing.value) return
        viewModelScope.launch {
            _isPolishing.value = true
            val polished = runCatching { aiService.polishListingDescription(text) }
                .getOrElse {
                    _message.value = "AI Polish is unavailable, so the text was tidied locally."
                    localPolishDescription(text)
                }
            onResult(polished.trim())
            _isPolishing.value = false
        }
    }

    /** Sends every form field to the AI and returns its price range. */
    fun estimatePrice(details: ListingDetails, onResult: (PriceEstimate?) -> Unit) {
        if (_isEstimating.value) return
        viewModelScope.launch {
            _isEstimating.value = true
            val estimate = runCatching { aiService.estimateListingPrice(details) }
                .onFailure {
                    _message.value = it.message ?: "Could not estimate a price right now"
                }
                .getOrNull()
            onResult(estimate)
            _isEstimating.value = false
        }
    }

    /** Reads the model/part label from a photo and returns what the AI saw. */
    fun scanModelLabel(uri: Uri, onResult: (ModelScanResult?) -> Unit) {
        if (_isScanningLabel.value) return
        viewModelScope.launch {
            _isScanningLabel.value = true
            val result = runCatching { aiService.scanModelLabel(uri) }
                .onFailure {
                    _message.value = it.message ?: "Could not read the label"
                }
                .getOrNull()
            onResult(result)
            _isScanningLabel.value = false
        }
    }
}

/**
 * Local "AI Polish" fallback: trims, collapses repeated whitespace, capitalises
 * the first letter of every sentence and makes sure the text ends with a full
 * stop. It runs on the device, so the button works offline too.
 */
fun localPolishDescription(raw: String): String {
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
