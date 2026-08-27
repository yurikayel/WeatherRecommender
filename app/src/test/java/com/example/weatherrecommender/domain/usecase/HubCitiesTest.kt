package com.example.weatherrecommender.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HubCitiesTest {

    @Test
    fun all_is_unique_by_placeKey() {
        val keys = HubCities.all.map { it.placeKey }
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(HubCities.all.all { it.id < 0L })
    }

    @Test
    fun featured_reuses_hub_ids_for_overlapping_cities() {
        assertEquals(14, HubCities.featured.size)
        val lisbon = HubCities.featured.first { it.name == "Lisbon" }
        assertEquals(-114L, lisbon.id)
        assertEquals(
            HubCities.all.first { it.name == "Lisbon" }.id,
            lisbon.id
        )
        assertTrue(HubCities.featured.all { it.id < 0L })
        assertEquals(
            HubCities.featured.size,
            HubCities.featured.distinctBy { it.placeKey }.size
        )
    }

    @Test
    fun major_cities_alias_matches_hub_all() {
        assertEquals(HubCities.all, MajorCities.all)
    }

    @Test
    fun all_hubs_have_usable_seed_fields() {
        HubCities.all.forEach { loc ->
            assertTrue(loc.name.isNotBlank())
            assertTrue(loc.country.orEmpty().isNotBlank())
            assertTrue((loc.population ?: 0L) > 0L)
            assertEquals(false, loc.hasSeaAccess)
            assertTrue(loc.placeKey.contains("|"))
        }
    }

    @Test
    fun featured_cities_facade_matches_hub_subset() {
        val featured = FeaturedCities()
        assertEquals(HubCities.featured, featured.all)
        assertTrue(featured.randomWeightedByPopulation(0, Random(0)).isEmpty())
        val sample = featured.randomWeightedByPopulation(14, Random(1))
        assertEquals(14, sample.size)
        assertEquals(14, sample.distinctBy { it.id }.size)
    }
}

