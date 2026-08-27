package com.example.weatherrecommender.data.repository

import com.example.weatherrecommender.data.local.entity.LocationEntity
import kotlin.math.abs

/**
 * Collapses recent-history rows that represent the same city under different IDs
 * (e.g. GeoNames search id vs Nominatim reverse-geocode synthetic id).
 *
 * Input is expected newest-first ([LocationEntity.lastViewedAt] DESC). The first
 * (most recently viewed) row in each duplicate group is kept.
 */
internal object LocationHistoryDeduper {

    const val PROXIMITY_DEGREES = 0.05

    /** Drops later rows that match an earlier city by id, proximity, or name+country. */
    fun collapse(entities: List<LocationEntity>): List<LocationEntity> {
        if (entities.size <= 1) return entities
        val kept = mutableListOf<LocationEntity>()
        for (entity in entities) {
            if (kept.none { isSameCity(it, entity) }) {
                kept.add(entity)
            }
        }
        return kept
    }

    /** Same Room id, nearby coordinates, or matching normalized name and country. */
    fun isSameCity(a: LocationEntity, b: LocationEntity): Boolean {
        return a.id == b.id ||
            withinProximity(a.latitude, a.longitude, b.latitude, b.longitude) ||
            sameNormalizedNameCountry(a, b)
    }

    /**
     * Closest [candidates] within [PROXIMITY_DEGREES] of [latitude]/[longitude], or null.
     * Used to replace a Nominatim synthetic id with an Open-Meteo / GeoNames search id.
     */
    fun <T> nearestWithinProximity(
        latitude: Double,
        longitude: Double,
        candidates: List<T>,
        latOf: (T) -> Double,
        lngOf: (T) -> Double
    ): T? {
        return candidates
            .filter { withinProximity(latitude, longitude, latOf(it), lngOf(it)) }
            .minByOrNull { candidate ->
                val dLat = abs(latitude - latOf(candidate))
                val dLng = abs(longitude - lngOf(candidate))
                dLat + dLng
            }
    }

    /** True when both points sit within [delta] degrees on both axes. */
    fun withinProximity(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
        delta: Double = PROXIMITY_DEGREES
    ): Boolean {
        return abs(lat1 - lat2) <= delta && abs(lng1 - lng2) <= delta
    }

    /** Case-insensitive name+country match, ignoring blank names. */
    fun sameNormalizedNameCountry(a: LocationEntity, b: LocationEntity): Boolean {
        val nameA = a.name.trim()
        val nameB = b.name.trim()
        if (nameA.isEmpty() || !nameA.equals(nameB, ignoreCase = true)) return false
        return a.country.trim().equals(b.country.trim(), ignoreCase = true)
    }
}
