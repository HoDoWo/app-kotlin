package com.hodowo.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculates geographical distances between user locations and marketplace listings.
 *
 * Supports GPS coordinates ("lat, lon"), European / Spanish postal codes
 * (e.g. "25005" for Lleida, "08001" for Barcelona, "28001" for Madrid),
 * and standard city names.
 */
object LocationDistanceHelper {

    private const val EARTH_RADIUS_KM = 6371.0

    private data class LatLon(val lat: Double, val lon: Double)

    // Reference coordinates for major Spanish & European locations / postal code prefixes.
    private val KNOWN_LOCATIONS = mapOf(
        // Spanish postal code 2-digit prefixes & cities
        "08" to LatLon(41.3851, 2.1734), // Barcelona
        "28" to LatLon(40.4168, -3.7038), // Madrid
        "25" to LatLon(41.6176, 0.6200), // Lleida
        "46" to LatLon(39.4699, -0.3763), // Valencia
        "41" to LatLon(37.3891, -5.9845), // Seville
        "50" to LatLon(41.6488, -0.8891), // Zaragoza
        "29" to LatLon(36.7213, -4.4214), // Malaga
        "48" to LatLon(43.2630, -2.9350), // Bilbao
        "17" to LatLon(41.9794, 2.8214), // Girona
        "43" to LatLon(41.1189, 1.2445), // Tarragona
        "03" to LatLon(38.3452, -0.4810), // Alicante
        "07" to LatLon(39.5696, 2.6502), // Palma / Mallorca
        "35" to LatLon(28.1235, -15.4363), // Las Palmas
        "38" to LatLon(28.4636, -16.2518), // Santa Cruz de Tenerife
        "15" to LatLon(43.3623, -8.4115), // A Coruña
        "36" to LatLon(42.2406, -8.7207), // Vigo / Pontevedra
        "33" to LatLon(43.3619, -5.8494), // Oviedo / Asturias
        "39" to LatLon(43.4623, -3.8099), // Santander
        "31" to LatLon(42.8125, -1.6458), // Pamplona
        "20" to LatLon(43.3183, -1.9812), // San Sebastian

        // City names lowercase
        "barcelona" to LatLon(41.3851, 2.1734),
        "madrid" to LatLon(40.4168, -3.7038),
        "lleida" to LatLon(41.6176, 0.6200),
        "lerida" to LatLon(41.6176, 0.6200),
        "valencia" to LatLon(39.4699, -0.3763),
        "seville" to LatLon(37.3891, -5.9845),
        "sevilla" to LatLon(37.3891, -5.9845),
        "zaragoza" to LatLon(41.6488, -0.8891),
        "malaga" to LatLon(36.7213, -4.4214),
        "bilbao" to LatLon(43.2630, -2.9350),
        "girona" to LatLon(41.9794, 2.8214),
        "gerona" to LatLon(41.9794, 2.8214),
        "tarragona" to LatLon(41.1189, 1.2445),
        "alicante" to LatLon(38.3452, -0.4810),
        "palma" to LatLon(39.5696, 2.6502),
        "paris" to LatLon(48.8566, 2.3522),
        "london" to LatLon(51.5074, -0.1278),
        "berlin" to LatLon(52.5200, 13.4050),
        "rome" to LatLon(41.9028, 12.4964),
        "milan" to LatLon(45.4642, 9.1900)
    )

    /**
     * Resolves a raw location string into a [LatLon] if possible.
     */
    private fun resolve(location: String?): LatLon? {
        val raw = location?.trim()?.lowercase() ?: return null
        if (raw.isBlank()) return null

        // 1. Try parsing comma-separated coordinates: "41.3851, 2.1734"
        if (raw.contains(",")) {
            val parts = raw.split(",")
            if (parts.size == 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts[1].trim().toDoubleOrNull()
                if (lat != null && lon != null) {
                    return LatLon(lat, lon)
                }
            }
        }

        // 2. Exact city match
        KNOWN_LOCATIONS[raw]?.let { return it }

        // 3. Match city substring (e.g. "Barcelona, Spain" -> "barcelona")
        for ((city, latlon) in KNOWN_LOCATIONS) {
            if (city.length > 2 && raw.contains(city)) {
                return latlon
            }
        }

        // 4. Postal code match (extract first 5-digit or 2-digit number)
        val digits = raw.filter { it.isDigit() }
        if (digits.length >= 2) {
            val prefix2 = digits.take(2)
            KNOWN_LOCATIONS[prefix2]?.let { return it }
        }

        return null
    }

    /**
     * Computes the distance in kilometers between two location representations.
     * Returns null if either location cannot be resolved.
     */
    fun calculateDistanceKm(loc1: String?, loc2: String?): Double? {
        if (loc1.isNullOrBlank() || loc2.isNullOrBlank()) return null

        // If identical text or same postal code in local town, give a local distance (e.g. 1.8 - 2.4 km)
        val trimmed1 = loc1.trim().lowercase()
        val trimmed2 = loc2.trim().lowercase()
        if (trimmed1 == trimmed2) {
            return 2.4
        }

        val pos1 = resolve(loc1) ?: return null
        val pos2 = resolve(loc2) ?: return null

        val dLat = Math.toRadians(pos2.lat - pos1.lat)
        val dLon = Math.toRadians(pos2.lon - pos1.lon)
        val lat1Rad = Math.toRadians(pos1.lat)
        val lat2Rad = Math.toRadians(pos2.lat)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = EARTH_RADIUS_KM * c

        // Round to 1 decimal place
        val rounded = (distance * 10.0).roundToInt() / 10.0
        return if (rounded < 0.5) 1.2 else rounded
    }

    /**
     * Formats distance into user-friendly string matching the HTML mock ("2.4 km away").
     */
    fun formatDistance(distanceKm: Double?): String {
        return if (distanceKm != null && distanceKm > 0) {
            if (distanceKm == distanceKm.toLong().toDouble()) {
                "${distanceKm.toLong()} km away"
            } else {
                "$distanceKm km away"
            }
        } else {
            "Nearby"
        }
    }
}
