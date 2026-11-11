package com.hodowo.data

import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.PartCompatibility
import com.hodowo.viewmodel.ProductDetail
import com.hodowo.viewmodel.SuggestedPart
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The part a diagnosis suggests inside the error resolution card (#149). */
class SuggestedPartTest {

    private fun part(confidence: String = "0.92", extra: String = "") = JSONObject(
        """{"id":"part-7","name":"Drain pump","price_cents":3850,"image_url":"http://x/p.jpg",
            "compatible":true,"confidence":$confidence$extra}"""
    )

    @Test
    fun aConfidentSuggestionIsRead() {
        val read = ManualJson.suggestedPartFromJson(part())

        assertEquals(
            SuggestedPart("part-7", "Drain pump", 3850, "http://x/p.jpg", PartCompatibility.FITS, 0.92),
            read
        )
    }

    @Test
    fun belowTheConfidenceThresholdThereIsNoBlock() {
        assertNull(ManualJson.suggestedPartFromJson(part(confidence = "0.69")))
        assertEquals(0.7, ManualJson.suggestedPartFromJson(part(confidence = "0.7"))?.confidence ?: 0.0, 0.0)
    }

    @Test
    fun withoutConfidenceItIsHidden() {
        assertNull(ManualJson.suggestedPartFromJson(JSONObject("""{"id":"p","name":"Pump"}""")))
    }

    @Test
    fun missingOrIncompleteSuggestionGivesNothing() {
        assertNull(ManualJson.suggestedPartFromJson(null))
        assertNull(ManualJson.suggestedPartFromJson(JSONObject("""{"name":"Pump","confidence":0.9}""")))
        assertNull(ManualJson.suggestedPartFromJson(JSONObject("""{"id":"p","confidence":0.9}""")))
    }

    @Test
    fun priceInEurosIsConvertedAndCompatibilityDefaultsToUnknown() {
        val read = ManualJson.suggestedPartFromJson(
            JSONObject("""{"id":"p","name":"Pump","price":12.34,"compatible":null,"confidence":0.8}""")
        )

        assertEquals(1234, read?.priceCents)
        assertEquals(PartCompatibility.UNKNOWN, read?.compatibility)
        assertEquals(
            PartCompatibility.DOES_NOT_FIT,
            ManualJson.suggestedPartFromJson(
                JSONObject("""{"id":"p","name":"Pump","compatible":false,"confidence":0.8}""")
            )?.compatibility
        )
    }

    @Test
    fun theGuideKeepsItsSuggestedPartWhenSavedAndRead() {
        val guide = ManualGuide(
            applianceName = "Bosch washer", issueTitle = "E18", overview = "Drain error",
            suggestedPart = ManualJson.suggestedPartFromJson(part())
        )
        val read = ManualJson.guideFromJson(ManualJson.guideToJson(guide))

        assertEquals(guide.suggestedPart, read?.suggestedPart)
        assertNull(ManualJson.guideFromJson(ManualJson.guideToJson(guide.copy(suggestedPart = null)))?.suggestedPart)
    }

    @Test
    fun buyPartOpensThePartDetail() {
        val product = ManualJson.suggestedPartFromJson(part())!!.toMarketplaceProduct()
        assertEquals("part-7", product.id)
        assertEquals(38.5, product.price, 0.001)

        val detail = product.toProductDetail() as ProductDetail.Part
        assertEquals("Drain pump", detail.title)
        assertEquals(PartCompatibility.FITS, detail.compatibility)
    }
}
