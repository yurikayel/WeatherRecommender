package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.usecase.scorer.IndoorSightseeingScorer
import com.example.weatherrecommender.domain.usecase.scorer.OutdoorSightseeingScorer
import com.example.weatherrecommender.domain.usecase.scorer.SkiScorer
import com.example.weatherrecommender.domain.usecase.scorer.SurfScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetRankedActivitiesUseCaseTest {

    private val useCase = GetRankedActivitiesUseCase(
        setOf(SurfScorer(), SkiScorer(), OutdoorSightseeingScorer(), IndoorSightseeingScorer())
    )

    private fun warmDay(wave: Double? = null) = DailyForecast(
        date = "2026-07-16",
        weatherCode = 0,
        maxTemp = 26.0,
        minTemp = 18.0,
        precipitationSum = 0.0,
        snowfallSum = 0.0,
        maxWindSpeed = 8.0,
        waveHeightMax = wave
    )

    @Test
    fun `returns empty list when the requested day is missing`() {
        val forecast = WeatherForecast(
            location = Location(1, "Nowhere", 0.0, 0.0, "X", null),
            dailyForecasts = emptyList()
        )
        assertTrue(useCase(forecast, 0).isEmpty())
    }

    @Test
    fun `landlocked lowland city excludes surfing and skiing`() {
        val forecast = WeatherForecast(
            location = Location(
                id = 1, name = "Madrid", latitude = 40.4, longitude = -3.7,
                country = "Spain", admin1 = null, elevation = 650.0, hasSeaAccess = false
            ),
            dailyForecasts = listOf(warmDay())
        )

        val result = useCase(forecast, 0)
        val activities = result.map { it.activity }

        assertFalse(activities.contains(RecommendedActivity.SURFING))
        assertFalse(activities.contains(RecommendedActivity.SKIING))
        assertTrue(activities.contains(RecommendedActivity.OUTDOOR_SIGHTSEEING))
        assertTrue(activities.contains(RecommendedActivity.INDOOR_SIGHTSEEING))
    }

    @Test
    fun `coastal city includes surfing and ranks it highly on a clean warm day`() {
        val forecast = WeatherForecast(
            location = Location(
                id = 2, name = "Lisbon", latitude = 38.7, longitude = -9.1,
                country = "Portugal", admin1 = null, elevation = 68.0, hasSeaAccess = true
            ),
            dailyForecasts = listOf(warmDay(wave = 1.2))
        )

        val result = useCase(forecast, 0)

        assertTrue(result.map { it.activity }.contains(RecommendedActivity.SURFING))
        assertEquals(RecommendedActivity.SURFING, result.first().activity)
    }

    @Test
    fun `results are sorted by score descending`() {
        val forecast = WeatherForecast(
            location = Location(3, "Nice", 43.7, 7.2, "France", null, elevation = 15.0, hasSeaAccess = true),
            dailyForecasts = listOf(warmDay(wave = 1.0))
        )

        val result = useCase(forecast, 0)

        assertTrue(result.zipWithNext().all { (higher, lower) -> higher.score >= lower.score })
    }

    @Test
    fun `different days can produce different rankings`() {
        val sunny = warmDay()
        val stormy = DailyForecast(
            date = "2026-07-17", weatherCode = 95, maxTemp = 10.0, minTemp = 6.0,
            precipitationSum = 25.0, snowfallSum = 0.0, maxWindSpeed = 40.0
        )
        val forecast = WeatherForecast(
            location = Location(4, "Paris", 48.8, 2.3, "France", null, elevation = 42.0, hasSeaAccess = false),
            dailyForecasts = listOf(sunny, stormy)
        )

        val day0Top = useCase(forecast, 0).first().activity
        val day1Top = useCase(forecast, 1).first().activity

        assertEquals(RecommendedActivity.OUTDOOR_SIGHTSEEING, day0Top)
        assertEquals(RecommendedActivity.INDOOR_SIGHTSEEING, day1Top)
    }
}
