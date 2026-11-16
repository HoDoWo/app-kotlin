package com.hodowo.data

import com.hodowo.viewmodel.PartCompatibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartsCacheTest {

    @Test
    fun aSavedSearchReadsBackTheSame() {
        val part = MarketplaceProduct(
            id = "p1", title = "Drain pump", description = "OEM", type = "part", condition = "new",
            price = 35.5, imageUrl = "http://x/y.jpg", category = "Pumps", brand = "Bosch", model = "WAN24",
            partNumber = "0014", isPart = true, compatibility = PartCompatibility.DOES_NOT_FIT
        )
        val read = PartsCacheJson.parse(PartsCacheJson.toJson(PartsSearch("pump", listOf(part))))

        assertEquals("pump", read?.query)
        assertEquals(listOf(part), read?.parts)
    }

    @Test
    fun unreadableOrEmptyTextGivesNoSearch() {
        assertNull(PartsCacheJson.parse(null))
        assertNull(PartsCacheJson.parse(""))
        assertNull(PartsCacheJson.parse("not json"))
        assertNull(PartsCacheJson.parse("{}"))
    }

    @Test
    fun onlyTheFirstFiftyPartsAreKept() {
        val parts = (1..60).map { MarketplaceProduct(id = "p$it", title = "Part $it", description = "") }
        assertEquals(50, PartsCacheJson.parse(PartsCacheJson.toJson(PartsSearch("", parts)))?.parts?.size)
    }
}
