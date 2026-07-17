package com.example.weatherrecommender.domain.usecase.scorer

import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.RankedActivity

/**
 * Contract for evaluating the suitability of a specific activity based on weather conditions.
 *
 * Implementations should encapsulate the heuristic logic for a single [RecommendedActivity].
 */
interface ActivityScorer {
    /** The specific type of activity this scorer evaluates. */
    val activityType: RecommendedActivity

    /**
     * Calculates a ranking score for the activity given a week of weather forecasts.
     *
     * @param forecasts The 7-day weather forecast.
     * @return A [RankedActivity] containing the computed score (0-100) and reasoning.
     */
    fun score(forecasts: List<DailyForecast>): RankedActivity
}

class SurfScorer : ActivityScorer {
    override val activityType = RecommendedActivity.SURFING

    companion object {
        private const val BASE_SCORE = 50
        private const val WIND_IDEAL_MAX = 15.0
        private const val WIND_BAD_MIN = 30.0
        private const val TEMP_WARM_MIN = 20.0
    }

    override fun score(forecasts: List<DailyForecast>): RankedActivity {
        var score = BASE_SCORE
        val avgWind = forecasts.map { it.maxWindSpeed }.average()
        val maxTemp = forecasts.maxOf { it.maxTemp }
        
        if (avgWind < WIND_IDEAL_MAX) score += 30
        else if (avgWind > WIND_BAD_MIN) score -= 40
        
        if (maxTemp > TEMP_WARM_MIN) score += 20
        
        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = com.example.weatherrecommender.domain.model.ReasonKey.SURF_IDEAL,
            reasonArgs = listOf(avgWind.toInt())
        )
    }
}

class SkiScorer : ActivityScorer {
    override val activityType = RecommendedActivity.SKIING

    companion object {
        private const val BASE_SCORE = 20
        private const val SNOW_IDEAL_MIN = 10.0
        private const val TEMP_FREEZING_MAX = 0.0
        private const val TEMP_MELTING_MIN = 5.0
    }

    override fun score(forecasts: List<DailyForecast>): RankedActivity {
        var score = BASE_SCORE
        val totalSnow = forecasts.sumOf { it.snowfallSum }
        val avgTemp = forecasts.map { (it.maxTemp + it.minTemp) / 2 }.average()
        
        if (totalSnow > SNOW_IDEAL_MIN) score += 50
        if (avgTemp < TEMP_FREEZING_MAX) score += 30
        if (avgTemp > TEMP_MELTING_MIN) score -= 40
        
        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = com.example.weatherrecommender.domain.model.ReasonKey.SKI_IDEAL,
            reasonArgs = listOf(avgTemp.toInt(), totalSnow.toInt())
        )
    }
}

class OutdoorSightseeingScorer : ActivityScorer {
    override val activityType = RecommendedActivity.OUTDOOR_SIGHTSEEING

    companion object {
        private const val BASE_SCORE = 60
        private const val PRECIP_BAD_MIN = 20.0
        private const val TEMP_MILD_MIN = 15.0
        private const val TEMP_MILD_MAX = 25.0
        private const val TEMP_HOT_MIN = 30.0
    }

    override fun score(forecasts: List<DailyForecast>): RankedActivity {
        var score = BASE_SCORE
        val totalPrecip = forecasts.sumOf { it.precipitationSum }
        val avgTemp = forecasts.map { (it.maxTemp + it.minTemp) / 2 }.average()
        
        if (totalPrecip > PRECIP_BAD_MIN) score -= 50
        if (avgTemp in TEMP_MILD_MIN..TEMP_MILD_MAX) score += 40
        if (avgTemp > TEMP_HOT_MIN) score -= 30
        
        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = com.example.weatherrecommender.domain.model.ReasonKey.OUTDOOR_MILD,
            reasonArgs = listOf(avgTemp.toInt())
        )
    }
}

class IndoorSightseeingScorer : ActivityScorer {
    override val activityType = RecommendedActivity.INDOOR_SIGHTSEEING

    companion object {
        private const val BASE_SCORE = 40
        private const val PRECIP_BAD_MIN = 15.0
        private const val SNOW_BAD_MIN = 5.0
        private const val TEMP_COLD_MAX = 5.0
    }

    override fun score(forecasts: List<DailyForecast>): RankedActivity {
        var score = BASE_SCORE
        val totalPrecip = forecasts.sumOf { it.precipitationSum }
        val totalSnow = forecasts.sumOf { it.snowfallSum }
        val minTemp = forecasts.minOf { it.minTemp }
        
        if (totalPrecip > PRECIP_BAD_MIN || totalSnow > SNOW_BAD_MIN) score += 40
        if (minTemp < TEMP_COLD_MAX) score += 20
        
        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = com.example.weatherrecommender.domain.model.ReasonKey.INDOOR_BAD_WEATHER
        )
    }
}
