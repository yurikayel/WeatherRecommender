package com.example.weatherrecommender.domain.usecase.scorer

import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.ReasonKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityScorerTest {

    @Test
    fun `SurfScorer returns high score for ideal conditions`() {
        val scorer = SurfScorer()
        val forecasts = List(7) {
            DailyForecast("2026-07-16", weatherCode = 1, maxTemp = 25.0, minTemp = 18.0, precipitationSum = 0.0, snowfallSum = 0.0, maxWindSpeed = 10.0)
        }
        
        val result = scorer.score(forecasts)
        
        assertEquals(RecommendedActivity.SURFING, result.activity)
        assertEquals(100, result.score) // 50 + 30 (wind) + 20 (temp)
        assertEquals(ReasonKey.SURF_IDEAL, result.reasonKey)
    }

    @Test
    fun `SurfScorer returns low score for bad wind`() {
        val scorer = SurfScorer()
        val forecasts = List(7) {
            DailyForecast("2026-07-16", weatherCode = 1, maxTemp = 18.0, minTemp = 15.0, precipitationSum = 0.0, snowfallSum = 0.0, maxWindSpeed = 35.0)
        }
        
        val result = scorer.score(forecasts)
        
        assertEquals(10, result.score) // 50 - 40 (wind)
    }

    @Test
    fun `SkiScorer returns high score for snowy freezing conditions`() {
        val scorer = SkiScorer()
        val forecasts = List(7) {
            DailyForecast("2026-07-16", weatherCode = 1, maxTemp = -2.0, minTemp = -8.0, precipitationSum = 5.0, snowfallSum = 5.0, maxWindSpeed = 10.0)
        }
        
        val result = scorer.score(forecasts)
        
        assertEquals(RecommendedActivity.SKIING, result.activity)
        assertEquals(100, result.score) // 20 + 50 (snow) + 30 (freezing)
        assertEquals(ReasonKey.SKI_IDEAL, result.reasonKey)
    }

    @Test
    fun `SkiScorer returns low score for warm no snow conditions`() {
        val scorer = SkiScorer()
        val forecasts = List(7) {
            DailyForecast("2026-07-16", weatherCode = 1, maxTemp = 15.0, minTemp = 5.0, precipitationSum = 0.0, snowfallSum = 0.0, maxWindSpeed = 10.0)
        }
        
        val result = scorer.score(forecasts)
        
        assertEquals(0, result.score) // 20 - 40 (warm) = -20 -> coerced to 0
    }

    @Test
    fun `OutdoorSightseeingScorer returns high score for mild dry conditions`() {
        val scorer = OutdoorSightseeingScorer()
        val forecasts = List(7) {
            DailyForecast("2026-07-16", weatherCode = 1, maxTemp = 22.0, minTemp = 16.0, precipitationSum = 0.0, snowfallSum = 0.0, maxWindSpeed = 10.0)
        }
        
        val result = scorer.score(forecasts)
        
        assertEquals(RecommendedActivity.OUTDOOR_SIGHTSEEING, result.activity)
        assertEquals(100, result.score) // 60 + 40 (mild temp)
        assertEquals(ReasonKey.OUTDOOR_MILD, result.reasonKey)
    }

    @Test
    fun `OutdoorSightseeingScorer returns low score for heavy rain`() {
        val scorer = OutdoorSightseeingScorer()
        val forecasts = List(7) {
            DailyForecast("2026-07-16", weatherCode = 1, maxTemp = 22.0, minTemp = 16.0, precipitationSum = 5.0, snowfallSum = 0.0, maxWindSpeed = 10.0)
        }
        
        val result = scorer.score(forecasts)
        
        assertEquals(50, result.score)
    }

    @Test
    fun `IndoorSightseeingScorer returns high score for bad weather`() {
        val scorer = IndoorSightseeingScorer()
        val forecasts = List(7) {
            DailyForecast("2026-07-16", weatherCode = 1, maxTemp = 2.0, minTemp = -2.0, precipitationSum = 5.0, snowfallSum = 2.0, maxWindSpeed = 10.0)
        }
        
        val result = scorer.score(forecasts)
        
        assertEquals(RecommendedActivity.INDOOR_SIGHTSEEING, result.activity)
        // Precip = 35 (>15) -> +40. MinTemp = -2 (<5) -> +20. Base 40 + 40 + 20 = 100
        assertEquals(100, result.score)
        assertEquals(ReasonKey.INDOOR_BAD_WEATHER, result.reasonKey)
    }
}
