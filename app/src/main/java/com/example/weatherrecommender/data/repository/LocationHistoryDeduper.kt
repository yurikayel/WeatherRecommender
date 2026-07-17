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

    fun isSameCity(a: LocationEntity, b: LocationEntity): Boolean {
        return a.id == b.id ||
            withinProximity(a.latitude, a.longitude, b.latitude, b.longitude) ||
            sameNormalizedNameCountry(a, b)
    }

    fun withinProximity(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
        delta: Double = PROXIMITY_DEGREES
    ): Boolean {
        return abs(lat1 - lat2) <= delta && abs(lng1 - lng2) <= delta
    }

    fun sameNormalizedNameCountry(a: LocationEntity, b: LocationEntity): Boolean {
        val nameA = a.name.trim()
        val nameB = b.name.trim()
        if (nameA.isEmpty() || !nameA.equals(nameB, ignoreCase = true)) return false
        return a.country.trim().equals(b.country.trim(), ignoreCase = true)
    }
}
