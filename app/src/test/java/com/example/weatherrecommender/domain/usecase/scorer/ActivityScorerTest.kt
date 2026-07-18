package com.example.weatherrecommender.domain.usecase.scorer

import com.example.weatherrecommender.domain.model.ActivityContext
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityScorerTest {

    private fun location(
        elevation: Double? = 20.0,
        hasSeaAccess: Boolean = false
    ) = Location(
        id = 1,
        name = "Test",
        latitude = 0.0,
        longitude = 0.0,
        country = "Testland",
        admin1 = null,
        elevation = elevation,
        hasSeaAccess = hasSeaAccess
    )

    private fun day(
        maxTemp: Double = 20.0,
        minTemp: Double = 12.0,
        precipitationSum: Double = 0.0,
        snowfallSum: Double = 0.0,
        maxWindSpeed: Double = 10.0,
        waveHeightMax: Double? = null
    ) = DailyForecast(
        date = "2026-07-16",
        weatherCode = 1,
        maxTemp = maxTemp,
        minTemp = minTemp,
        precipitationSum = precipitationSum,
        snowfallSum = snowfallSum,
        maxWindSpeed = maxWindSpeed,
        waveHeightMax = waveHeightMax
    )

    // --- Surf ---

    @Test
    fun `surf is not applicable without sea access`() {
        val ctx = ActivityContext(location(hasSeaAccess = false), day())
        assertFalse(SurfScorer().isApplicable(ctx))
    }

    @Test
    fun `surf is applicable with sea access`() {
        val ctx = ActivityContext(location(hasSeaAccess = true), day(waveHeightMax = 1.0))
        assertTrue(SurfScorer().isApplicable(ctx))
    }

    @Test
    fun `surf scores high for rideable waves, light wind, warm air`() {
        val ctx = ActivityContext(
            location(hasSeaAccess = true),
            day(maxTemp = 25.0, maxWindSpeed = 10.0, waveHeightMax = 1.0)
        )
        val result = SurfScorer().score(ctx)
        assertEquals(RecommendedActivity.SURFING, result.activity)
        assertEquals(100, result.score) // 45 + 35 + 15 + 10 -> coerced to 100
        assertEquals(ReasonKey.SURF_IDEAL, result.reasonKey)
    }

    @Test
    fun `surf scores low for flat seas and strong wind`() {
        val ctx = ActivityContext(
            location(hasSeaAccess = true),
            day(maxTemp = 15.0, maxWindSpeed = 40.0, waveHeightMax = 0.1)
        )
        val result = SurfScorer().score(ctx)
        assertEquals(0, result.score) // 45 - 25 - 30 -> coerced to 0
    }

    // --- Ski ---

    @Test
    fun `ski is applicable in the mountains even without snow yet`() {
        val ctx = ActivityContext(location(elevation = 1600.0), day(snowfallSum = 0.0))
        assertTrue(SkiScorer().isApplicable(ctx))
    }

    @Test
    fun `ski is applicable at low elevation only when snow is falling`() {
        val noSnow = ActivityContext(location(elevation = 100.0), day(snowfallSum = 0.0))
        val snowing = ActivityContext(location(elevation = 100.0), day(snowfallSum = 4.0))
        assertFalse(SkiScorer().isApplicable(noSnow))
        assertTrue(SkiScorer().isApplicable(snowing))
    }

    @Test
    fun `ski scores high for fresh snow and freezing temps`() {
        val ctx = ActivityContext(
            location(elevation = 1600.0),
            day(maxTemp = -2.0, minTemp = -8.0, snowfallSum = 5.0)
        )
        val result = SkiScorer().score(ctx)
        assertEquals(RecommendedActivity.SKIING, result.activity)
        assertEquals(100, result.score) // 20 + 50 + 30
        assertEquals(ReasonKey.SKI_IDEAL, result.reasonKey)
    }

    @Test
    fun `ski scores zero when warm with no snow`() {
        val ctx = ActivityContext(
            location(elevation = 1600.0),
            day(maxTemp = 15.0, minTemp = 5.0, snowfallSum = 0.0)
        )
        val result = SkiScorer().score(ctx)
        assertEquals(0, result.score) // 20 - 40 -> coerced to 0
    }

    // --- Outdoor sightseeing ---

    @Test
    fun `outdoor sightseeing scores high for mild dry day`() {
        val ctx = ActivityContext(location(), day(maxTemp = 22.0, minTemp = 16.0, precipitationSum = 0.0))
        val result = OutdoorSightseeingScorer().score(ctx)
        assertEquals(RecommendedActivity.OUTDOOR_SIGHTSEEING, result.activity)
        assertEquals(90, result.score) // 55 + 35
        assertEquals(ReasonKey.OUTDOOR_MILD, result.reasonKey)
    }

    @Test
    fun `outdoor sightseeing is penalised by rain`() {
        val ctx = ActivityContext(location(), day(maxTemp = 22.0, minTemp = 16.0, precipitationSum = 10.0))
        val result = OutdoorSightseeingScorer().score(ctx)
        assertEquals(45, result.score) // 55 - 45 + 35
    }

    // --- Indoor sightseeing ---

    @Test
    fun `indoor sightseeing scores high in bad weather`() {
        val ctx = ActivityContext(location(), day(maxTemp = 2.0, minTemp = -2.0, precipitationSum = 10.0))
        val result = IndoorSightseeingScorer().score(ctx)
        assertEquals(RecommendedActivity.INDOOR_SIGHTSEEING, result.activity)
        assertEquals(95, result.score) // 45 + 35 + 15
        assertEquals(ReasonKey.INDOOR_BAD_WEATHER, result.reasonKey)
    }

    @Test
    fun `outdoor and indoor are always applicable`() {
        val ctx = ActivityContext(location(), day())
        assertTrue(OutdoorSightseeingScorer().isApplicable(ctx))
        assertTrue(IndoorSightseeingScorer().isApplicable(ctx))
    }
}
