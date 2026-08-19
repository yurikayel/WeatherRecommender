package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.Location
import javax.inject.Inject
import kotlin.random.Random

/**
 * Provides a curated seed list of well-known cities for the home screen's "top picks".
 *
 * The Open-Meteo Geocoding API only supports *searching by name* — there is no discovery or
 * "browse popular cities" endpoint — so a hand-picked list is the pragmatic way to power a
 * suggestions feed. Each entry carries the geographic metadata the scorers need (elevation for
 * skiing, population for weighted selection). Coastal access is resolved at fetch time from the
 * Marine API rather than hard-coded here.
 *
 * Seed [Location.id] values are **negative** (-1..-14) so they never collide with positive
 * Open-Meteo / GeoNames IDs returned by search.
 */
class FeaturedCities @Inject constructor() {

    // Negative synthetic IDs avoid colliding with Open-Meteo / GeoNames place IDs (always positive).
    val all: List<Location> = listOf(
        city(-1, "Rio de Janeiro", -22.9068, -43.1729, "Brazil", "Rio de Janeiro", 6_747_815, 5.0),
        city(-2, "Sydney", -33.8688, 151.2093, "Australia", "New South Wales", 5_231_147, 58.0),
        city(-3, "Cape Town", -33.9249, 18.4241, "South Africa", "Western Cape", 3_433_441, 25.0),
        city(-4, "Lisbon", 38.7223, -9.1393, "Portugal", "Lisbon", 517_802, 68.0),
        city(-5, "Honolulu", 21.3069, -157.8583, "United States", "Hawaii", 371_657, 12.0),
        city(-6, "Barcelona", 41.3874, 2.1686, "Spain", "Catalonia", 1_620_343, 12.0),
        city(-7, "Tokyo", 35.6895, 139.6917, "Japan", "Tokyo", 8_336_599, 40.0),
        city(-8, "Reykjavik", 64.1466, -21.9426, "Iceland", "Capital Region", 118_918, 61.0),
        city(-9, "Zermatt", 46.0207, 7.7491, "Switzerland", "Valais", 5_643, 1608.0),
        city(-10, "Aspen", 39.1911, -106.8175, "United States", "Colorado", 7_401, 2438.0),
        city(-11, "Chamonix", 45.9237, 6.8694, "France", "Auvergne-Rhône-Alpes", 8_611, 1035.0),
        city(-12, "Denver", 39.7392, -104.9903, "United States", "Colorado", 715_522, 1609.0),
        city(-13, "Paris", 48.8566, 2.3522, "France", "Île-de-France", 2_138_551, 42.0),
        city(-14, "London", 51.5074, -0.1278, "United Kingdom", "England", 8_961_989, 25.0)
    )

    /**
     * Returns up to [count] distinct featured cities, chosen randomly but weighted by population so
     * larger cities are more likely to surface.
     */
    fun randomWeightedByPopulation(count: Int, random: Random = Random.Default): List<Location> {
        val pool = all.toMutableList()
        val selected = mutableListOf<Location>()
        repeat(minOf(count, pool.size)) {
            val totalWeight = pool.sumOf { (it.population ?: 1L).toDouble() }
            var target = random.nextDouble(totalWeight)
            val pick = pool.first { candidate ->
                target -= (candidate.population ?: 1L).toDouble()
                target <= 0.0
            }
            selected += pick
            pool -= pick
        }
        return selected
    }

    /** Builds a seed [Location] with synthetic negative [id] and coastal access resolved later. */
    private fun city(
        id: Long,
        name: String,
        lat: Double,
        lon: Double,
        country: String,
        admin1: String,
        population: Long,
        elevation: Double
    ) = Location(
        id = id,
        name = name,
        latitude = lat,
        longitude = lon,
        country = country,
        admin1 = admin1,
        elevation = elevation,
        population = population,
        hasSeaAccess = false
    )
}
