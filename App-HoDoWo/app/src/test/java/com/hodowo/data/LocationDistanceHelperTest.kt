package com.hodowo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationDistanceHelperTest {

    @Test
    fun calculateDistance_sameLocation_returnsLocalDistance() {
        val dist = LocationDistanceHelper.calculateDistanceKm("Barcelona", "Barcelona")
        assertNotNull(dist)
        assertEquals(2.4, dist!!, 0.1)
    }

    @Test
    fun calculateDistance_betweenBarcelonaAndMadrid_returnsCorrectKm() {
        val dist = LocationDistanceHelper.calculateDistanceKm("Barcelona", "Madrid")
        assertNotNull(dist)
        // Distance Barcelona to Madrid is ~500 km
        assertTrue("Expected ~505 km, was $dist", dist!! in 490.0..520.0)
    }

    @Test
    fun calculateDistance_betweenBarcelonaAndLleidaPostalCodes() {
        // 08001 (Barcelona) and 25005 (Lleida)
        val dist = LocationDistanceHelper.calculateDistanceKm("08001", "25005")
        assertNotNull(dist)
        // Distance Barcelona to Lleida is ~130 km
        assertTrue("Expected ~130 km, was $dist", dist!! in 120.0..145.0)
    }

    @Test
    fun calculateDistance_withCoordinates() {
        val dist = LocationDistanceHelper.calculateDistanceKm("41.3851, 2.1734", "41.6176, 0.6200")
        assertNotNull(dist)
        assertTrue("Expected ~132 km, was $dist", dist!! in 125.0..140.0)
    }

    @Test
    fun calculateDistance_invalidOrBlank_returnsNull() {
        assertNull(LocationDistanceHelper.calculateDistanceKm(null, "Madrid"))
        assertNull(LocationDistanceHelper.calculateDistanceKm("Barcelona", ""))
        assertNull(LocationDistanceHelper.calculateDistanceKm("xyz123abc", "unknownPlace456"))
    }

    @Test
    fun formatDistance_formatsNicely() {
        assertEquals("2.4 km away", LocationDistanceHelper.formatDistance(2.4))
        assertEquals("12 km away", LocationDistanceHelper.formatDistance(12.0))
        assertEquals("Nearby", LocationDistanceHelper.formatDistance(null))
    }
}
