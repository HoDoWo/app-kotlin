package com.hodowo.data

import com.hodowo.viewmodel.ListingStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** [DemoListingsService]'s seeded in-memory data (issue #146). */
class DemoListingsServiceTest {

    private val service = DemoListingsService()

    @Test
    fun myListingsReturnsTheSeededListings() = runTest {
        val listings = service.myListings()

        assertEquals(2, listings.size)
        assertTrue(listings.all { it.status == ListingStatus.ACTIVE })
    }

    @Test
    fun updateListingMutatesTheFieldsAndPersists() = runTest {
        val id = service.myListings().first().id

        val updated = service.updateListing(id, "New title", "New description", "Like new", "$99.00")

        assertEquals("New title", updated.title)
        assertEquals("$99.00", updated.priceLabel)
        assertEquals("New title", service.myListings().first { it.id == id }.title)
    }

    @Test
    fun markAsSoldFlipsTheStatusAndPersists() = runTest {
        val id = service.myListings().first().id

        val updated = service.markAsSold(id)

        assertEquals(ListingStatus.SOLD, updated.status)
        assertEquals(ListingStatus.SOLD, service.myListings().first { it.id == id }.status)
    }

    @Test
    fun deleteListingRemovesItFromTheList() = runTest {
        val id = service.myListings().first().id

        service.deleteListing(id)

        assertTrue(service.myListings().none { it.id == id })
    }

    @Test(expected = IOException::class)
    fun updatingAnUnknownIdThrows() = runTest {
        service.updateListing("does-not-exist", "t", "d", "c", "$1.00")
    }

    @Test(expected = IOException::class)
    fun markingAnUnknownIdAsSoldThrows() = runTest {
        service.markAsSold("does-not-exist")
    }

    @Test(expected = IOException::class)
    fun deletingAnUnknownIdThrows() = runTest {
        service.deleteListing("does-not-exist")
    }
}
