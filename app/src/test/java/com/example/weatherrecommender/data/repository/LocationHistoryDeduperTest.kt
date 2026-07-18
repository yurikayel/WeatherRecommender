package com.example.weatherrecommender.data.repository

import com.example.weatherrecommender.data.local.entity.LocationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationHistoryDeduperTest {

    private fun entity(
        id: Long,
        name: String = "London",
        lat: Double = 51.5,
        lng: Double = -0.1,
        country: String = "UK",
        lastViewedAt: Long = 1L
    ) = LocationEntity(
        id = id,
        name = name,
        latitude = lat,
        longitude = lng,
        country = country,
        admin1 = null,
        lastUpdated = lastViewedAt,
        lastViewedAt = lastViewedAt
    )

    @Test
    fun `collapse keeps first of proximity duplicates`() {
        val geoNames = entity(id = 2643743, lastViewedAt = 200)
        val nominatim = entity(id = -1_000_042, lat = 51.52, lng = -0.12, lastViewedAt = 100)

        val result = LocationHistoryDeduper.collapse(listOf(geoNames, nominatim))

        assertEquals(listOf(geoNames), result)
    }

    @Test
    fun `collapse keeps first of name-country duplicates`() {
        val newer = entity(id = 1, lastViewedAt = 300)
        val older = entity(id = -99, lat = 60.0, lng = 10.0, lastViewedAt = 100)

        val result = LocationHistoryDeduper.collapse(listOf(newer, older))

        assertEquals(1, result.size)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `collapse retains distinct cities`() {
        val london = entity(id = 1, name = "London", lat = 51.5, lng = -0.1)
        val paris = entity(id = 2, name = "Paris", lat = 48.85, lng = 2.35, country = "France")

        val result = LocationHistoryDeduper.collapse(listOf(london, paris))

        assertEquals(2, result.size)
    }

    @Test
    fun `withinProximity respects 0_05 degree threshold`() {
        assertTrue(LocationHistoryDeduper.withinProximity(51.5, -0.1, 51.54, -0.12))
        assertFalse(LocationHistoryDeduper.withinProximity(51.5, -0.1, 51.6, -0.1))
    }

    @Test
    fun `sameNormalizedNameCountry is case insensitive`() {
        val a = entity(id = 1, name = " London ", country = "uk")
        val b = entity(id = 2, name = "london", country = "UK", lat = 0.0, lng = 0.0)
        assertTrue(LocationHistoryDeduper.sameNormalizedNameCountry(a, b))
    }
}
