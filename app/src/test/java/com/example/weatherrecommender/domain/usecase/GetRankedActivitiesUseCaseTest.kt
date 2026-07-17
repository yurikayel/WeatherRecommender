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
import org.junit.Assert.assertTrue
import org.junit.Test

class GetRankedActivitiesUseCaseTest {

    private val useCase = GetRankedActivitiesUseCase(
        setOf(SurfScorer(), SkiScorer(), OutdoorSightseeingScorer(), IndoorSightseeingScorer())
    )

    private val sunnyWeek = List(7) {
        DailyForecast("2026-07-16", weatherCode = 0, 26.0, 18.0, 0.0, 0.0, 8.0)
    }

    private val forecast = WeatherForecast(
        location = Location(1, "Nice", 43.7, 7.2, "France", "Alpes-Maritimes"),
        dailyForecasts = sunnyWeek
    )

    @Test
    fun `returns empty list when forecast has no daily data`() {
        val emptyForecast = forecast.copy(dailyForecasts = emptyList())

        assertTrue(useCase(emptyForecast).isEmpty())
    }

    @Test
    fun `returns four ranked activities sorted by score descending`() {
        val result = useCase(forecast)

        assertEquals(4, result.size)
        assertTrue(result.zipWithNext().all { (higher, lower) -> higher.score >= lower.score })
    }

    @Test
    fun `surfing ranks highest for warm calm week`() {
        val result = useCase(forecast)

        assertEquals(RecommendedActivity.SURFING, result.first().activity)
    }
}
