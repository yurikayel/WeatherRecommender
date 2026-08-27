package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.Location
import javax.inject.Inject
import kotlin.random.Random

/**
 * Home-screen "top picks" subset of [HubCities].
 *
 * The Open-Meteo Geocoding API only supports *searching by name* — there is no discovery or
 * "browse popular cities" endpoint — so a hand-picked list is the pragmatic way to power a
 * suggestions feed. Overlapping cities reuse the hub synthetic id so a tap and a nearby
 * prefetch share one Room row. Coastal access is resolved at fetch time from the Marine API.
 */
class FeaturedCities @Inject constructor() {

    /** Featured subset of [HubCities]; ids are negative and unique by [Location.placeKey]. */
    val all: List<Location> get() = HubCities.featured

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
}
