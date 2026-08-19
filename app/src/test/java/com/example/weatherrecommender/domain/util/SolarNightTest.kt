package com.example.weatherrecommender.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SolarNightTest {

    private val londonLat = 51.5074
    private val londonLng = -0.1278

    @Test
    fun `clock fallback treats 19 through 5 as night`() {
        assertTrue(SolarNight.isNightByLocalClock(atHour(19)))
        assertTrue(SolarNight.isNightByLocalClock(atHour(23)))
        assertTrue(SolarNight.isNightByLocalClock(atHour(0)))
        assertTrue(SolarNight.isNightByLocalClock(atHour(5)))
        assertFalse(SolarNight.isNightByLocalClock(atHour(6)))
        assertFalse(SolarNight.isNightByLocalClock(atHour(12)))
        assertFalse(SolarNight.isNightByLocalClock(atHour(18)))
    }

    @Test
    fun `london summer noon utc is day`() {
        val noon = Instant.parse("2024-06-21T12:00:00Z")
        assertFalse(SolarNight.isNightAt(londonLat, londonLng, noon))
        assertTrue(solarElevationDegrees(londonLat, londonLng, noon) > 0.0)
    }

    @Test
    fun `london summer midnight utc is night`() {
        val midnight = Instant.parse("2024-06-21T00:00:00Z")
        assertTrue(SolarNight.isNightAt(londonLat, londonLng, midnight))
        assertTrue(solarElevationDegrees(londonLat, londonLng, midnight) < 0.0)
    }

    @Test
    fun `sydney winter afternoon local is day`() {
        // Sydney 33.87°S 151.21°E — 2024-07-01 14:00 AEST = 04:00 UTC
        val afternoon = Instant.parse("2024-07-01T04:00:00Z")
        assertFalse(SolarNight.isNightAt(-33.87, 151.21, afternoon))
    }

    private fun atHour(hour: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(2026, 8, 19, hour, 0), ZoneOffset.UTC)
}
