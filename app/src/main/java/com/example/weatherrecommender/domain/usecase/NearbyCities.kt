package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.CachePolicy
import com.example.weatherrecommender.domain.model.Location
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Picks major cities in the same map region as [origin] for background prefetch.
 */
object NearbyCities {

    /** Nearby populated hubs within [CachePolicy] distance, sorted by population then distance. */
    fun select(
        origin: Location,
        candidates: List<Location>,
        limit: Int = CachePolicy.NEARBY_LIMIT
    ): List<Location> {
        return candidates
            .asSequence()
            .filter { candidate -> candidate.id != origin.id }
            .filter { candidate ->
                !candidate.name.equals(origin.name, ignoreCase = true) ||
                    !candidate.country.equals(origin.country, ignoreCase = true)
            }
            .map { candidate -> candidate to haversineKm(origin, candidate) }
            .filter { (_, km) -> km in CachePolicy.NEARBY_MIN_KM..CachePolicy.NEARBY_RADIUS_KM }
            .filter { (candidate, _) ->
                (candidate.population ?: 0L) >= CachePolicy.NEARBY_MIN_POPULATION
            }
            .sortedWith(
                compareByDescending<Pair<Location, Double>> { it.first.population ?: 0L }
                    .thenBy { it.second }
            )
            .map { it.first }
            .distinctBy { it.id }
            .take(limit)
            .toList()
    }

    /** Great-circle distance in kilometres between two locations. */
    fun haversineKm(a: Location, b: Location): Double =
        haversineKm(a.latitude, a.longitude, b.latitude, b.longitude)

    /** Great-circle distance in kilometres between two WGS84 points. */
    fun haversineKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val destLat = Math.toRadians(lat2)
        val chord = sin(dLat / 2) * sin(dLat / 2) +
            cos(originLat) * cos(destLat) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(chord), sqrt(1 - chord))
    }

    private const val EARTH_RADIUS_KM = 6371.0
}
