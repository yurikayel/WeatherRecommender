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

    @Test
    fun `isNightNow falls back to clock when coordinates are missing`() {
        val night = Instant.parse("2026-01-15T23:00:00Z")
        val day = Instant.parse("2026-01-15T12:00:00Z")
        assertTrue(SolarNight.isNightNow(null, null, night, ZoneOffset.UTC))
        assertFalse(SolarNight.isNightNow(null, -0.1, day, ZoneOffset.UTC))
        assertFalse(SolarNight.isNightNow(londonLat, londonLng, Instant.parse("2024-06-21T12:00:00Z"), ZoneOffset.UTC))
    }

    @Test
    fun isNightNow_usesSolarWhenCoordinatesPresent() {
        val noon = Instant.parse("2024-06-21T12:00:00Z")
        assertFalse(
            SolarNight.isNightNow(londonLat, londonLng, noon, ZoneOffset.UTC)
        )
        val midnight = Instant.parse("2024-06-21T00:00:00Z")
        assertTrue(
            SolarNight.isNightNow(londonLat, londonLng, midnight, ZoneOffset.UTC)
        )
    }

    @Test
    fun isNightNow_fallsBackToClockWhenCoordinatesMissing() {
        val evening = Instant.parse("2026-08-19T20:00:00Z")
        assertTrue(SolarNight.isNightNow(null, null, evening, ZoneOffset.UTC))
        assertTrue(SolarNight.isNightNow(londonLat, null, evening, ZoneOffset.UTC))
        val midday = Instant.parse("2026-08-19T12:00:00Z")
        assertFalse(SolarNight.isNightNow(null, londonLng, midday, ZoneOffset.UTC))
    }

    private fun atHour(hour: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(2026, 8, 19, hour, 0), ZoneOffset.UTC)
}
