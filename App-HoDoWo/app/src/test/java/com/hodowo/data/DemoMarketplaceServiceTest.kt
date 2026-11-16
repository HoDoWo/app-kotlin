package com.hodowo.data

import com.hodowo.viewmodel.PartCompatibility
import com.hodowo.viewmodel.ProductDetail
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** [DemoMarketplaceService]'s sample data for the three Product Detail variants (issue #140). */
class DemoMarketplaceServiceTest {

    private val service = DemoMarketplaceService()

    @Test
    fun recommendedDemoIdsListsOneOfEachVariant() = runTest {
        val ids = service.recommendedDemoIds()

        assertEquals(
            listOf(
                DemoMarketplaceService.NEW_PRODUCT.id,
                DemoMarketplaceService.SECOND_HAND_LISTING.id,
                DemoMarketplaceService.PART_FITS.id
            ),
            ids
        )
    }

    @Test
    fun theNewProductHasSpecsAndCommunityContent() = runTest {
        val product = service.getProductDetail(DemoMarketplaceService.NEW_PRODUCT.id) as ProductDetail.New

        assertEquals("Partner Certified", product.partnerName)
        assertTrue(product.rating > 0)
        assertTrue(product.reviewCount > 0)
        assertTrue(product.specs.isNotEmpty())
        assertEquals("Bosch Serie 4 Washer (WAN28209GB)", product.matchedAppliance)
        assertEquals(2, product.questions.size)
        assertEquals(1, product.reviews.size)
        assertTrue(product.reviews.single().verifiedPurchase)
    }

    @Test
    fun theSecondHandListingHasSellerAndConditionDetails() = runTest {
        val product = service.getProductDetail(DemoMarketplaceService.SECOND_HAND_LISTING.id) as ProductDetail.SecondHand

        assertEquals("Like new", product.condition)
        assertEquals("Marc O.", product.sellerName)
        assertEquals(4.7, product.sellerRating!!, 0.0001)
    }

    @Test
    fun theFitsPartHasNoWarning() = runTest {
        val product = service.getProductDetail(DemoMarketplaceService.PART_FITS.id) as ProductDetail.Part

        assertEquals(PartCompatibility.FITS, product.compatibility)
        assertEquals("Matches model WAN28209GB", product.compatibilityEvidence)
    }

    @Test
    fun theMismatchedPartExplainsWhy() = runTest {
        val product = service.getProductDetail(DemoMarketplaceService.PART_DOES_NOT_FIT.id) as ProductDetail.Part

        assertEquals(PartCompatibility.DOES_NOT_FIT, product.compatibility)
        assertTrue(product.compatibilityEvidence!!.contains("Serie 4"))
    }

    @Test
    fun theUnknownCompatibilityPartHasNoEvidence() = runTest {
        val product = service.getProductDetail(DemoMarketplaceService.PART_UNKNOWN.id) as ProductDetail.Part

        assertEquals(PartCompatibility.UNKNOWN, product.compatibility)
        assertNull(product.compatibilityEvidence)
    }

    @Test(expected = IOException::class)
    fun anUnknownIdThrows() = runTest {
        service.getProductDetail("does-not-exist")
    }
}
