package com.hodowo.viewmodel

import android.net.Uri
import com.hodowo.data.FirebaseRepository
import com.hodowo.data.MarketplaceProduct
import com.hodowo.data.MarketplaceService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostProductViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: MarketplaceService
    private lateinit var repository: FirebaseRepository
    private lateinit var viewModel: PostProductViewModel
    private val mockUri: Uri = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        viewModel = PostProductViewModel(service, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submitFailsValidationOnShortTitleOrDescriptionOrBlankCategory() {
        var successCalled = false

        // Short title
        viewModel.submit(
            userId = "u1",
            title = "Ab",
            description = "Valid description long enough for 30 characters",
            category = "washer",
            brand = "Bosch",
            model = "WAN",
            partNumber = "P1",
            listingKind = "part",
            condition = "new",
            priceText = "25.0",
            imageUris = listOf(mockUri),
            diagnosticMediaUris = emptyList(),
            handoverMethods = listOf("shipping"),
            pickupZip = "",
            shippingPriceCents = null,
            onSuccess = { successCalled = true }
        )
        assertFalse(successCalled)
        assertEquals("Add the required details, 1–8 photos, a valid price, and a handover method.", viewModel.message.value)

        // Blank category
        viewModel.submit(
            userId = "u1",
            title = "Bosch Drain Pump",
            description = "Valid description long enough for 30 characters",
            category = "",
            brand = "Bosch",
            model = "WAN",
            partNumber = "P1",
            listingKind = "part",
            condition = "new",
            priceText = "25.0",
            imageUris = listOf(mockUri),
            diagnosticMediaUris = emptyList(),
            handoverMethods = listOf("shipping"),
            pickupZip = "",
            shippingPriceCents = null,
            onSuccess = { successCalled = true }
        )
        assertFalse(successCalled)
    }

    @Test
    fun submitFailsValidationOnTooManyImagesOrPickupWithoutZip() {
        var successCalled = false
        val nineUris = List(9) { mockUri }

        // > 8 imageUris
        viewModel.submit(
            userId = "u1",
            title = "Bosch Drain Pump",
            description = "Valid description long enough for 30 characters",
            category = "washer",
            brand = "Bosch",
            model = "WAN",
            partNumber = "P1",
            listingKind = "part",
            condition = "new",
            priceText = "25.0",
            imageUris = nineUris,
            diagnosticMediaUris = emptyList(),
            handoverMethods = listOf("shipping"),
            pickupZip = "",
            shippingPriceCents = null,
            onSuccess = { successCalled = true }
        )
        assertFalse(successCalled)

        // > 8 diagnosticMediaUris
        viewModel.submit(
            userId = "u1",
            title = "Bosch Drain Pump",
            description = "Valid description long enough for 30 characters",
            category = "washer",
            brand = "Bosch",
            model = "WAN",
            partNumber = "P1",
            listingKind = "part",
            condition = "new",
            priceText = "25.0",
            imageUris = listOf(mockUri),
            diagnosticMediaUris = nineUris,
            handoverMethods = listOf("shipping"),
            pickupZip = "",
            shippingPriceCents = null,
            onSuccess = { successCalled = true }
        )
        assertFalse(successCalled)

        // handover pickup with blank zip
        viewModel.submit(
            userId = "u1",
            title = "Bosch Drain Pump",
            description = "Valid description long enough for 30 characters",
            category = "washer",
            brand = "Bosch",
            model = "WAN",
            partNumber = "P1",
            listingKind = "part",
            condition = "new",
            priceText = "25.0",
            imageUris = listOf(mockUri),
            diagnosticMediaUris = emptyList(),
            handoverMethods = listOf("pickup"),
            pickupZip = "",
            shippingPriceCents = null,
            onSuccess = { successCalled = true }
        )
        assertFalse(successCalled)
    }

    @Test
    fun polishTrimsWhitespaceCapitalisesSentencesAndAddsAFullStop() {
        assertEquals(
            "Genuine OEM motor. Brushes replaced 6 months ago.",
            polishListingDescription("  genuine   OEM motor.  brushes replaced 6 months ago  ")
        )
        assertEquals("", polishListingDescription("   "))
        assertEquals(
            "Already tidied.",
            polishListingDescription("Already tidied.")
        )
    }

    @Test
    fun submitUploadsImagesAndCreatesListingOnValidInput() = runTest(dispatcher) {
        var successCalled = false
        val product = MarketplaceProduct(id = "p-100", title = "Bosch Drain Pump", description = "Valid description long enough for 30 characters", price = 25.0)

        coEvery { repository.uploadMarketplaceImage(any(), any()) } returns "http://example.com/img.jpg"
        coEvery {
            service.createListing(
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns product

        viewModel.submit(
            userId = "u1",
            title = "Bosch Drain Pump",
            description = "Valid description long enough for 30 characters",
            category = "washer",
            brand = "Bosch",
            model = "WAN",
            partNumber = "P1",
            listingKind = "part",
            condition = "new",
            priceText = "25.00",
            imageUris = listOf(mockUri),
            diagnosticMediaUris = listOf(mockUri),
            handoverMethods = listOf("pickup", "shipping"),
            pickupZip = "90210",
            shippingPriceCents = 500,
            onSuccess = { successCalled = true }
        )

        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("Listing published", viewModel.message.value)
        assertFalse(viewModel.isSubmitting.value)
    }

    @Test
    fun submitHandlesUploadFailureGracefully() = runTest(dispatcher) {
        var successCalled = false
        coEvery { repository.uploadMarketplaceImage(any(), any()) } throws RuntimeException("Upload failed")

        viewModel.submit(
            userId = "u1",
            title = "Bosch Drain Pump",
            description = "Valid description long enough for 30 characters",
            category = "washer",
            brand = "Bosch",
            model = "WAN",
            partNumber = "P1",
            listingKind = "part",
            condition = "new",
            priceText = "25.00",
            imageUris = listOf(mockUri),
            diagnosticMediaUris = emptyList(),
            handoverMethods = listOf("shipping"),
            pickupZip = "",
            shippingPriceCents = 500,
            onSuccess = { successCalled = true }
        )

        advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals("Upload failed", viewModel.message.value)
        assertFalse(viewModel.isSubmitting.value)
    }
}
