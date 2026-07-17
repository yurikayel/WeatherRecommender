package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.usecase.scorer.ActivityScorer
import javax.inject.Inject

/**
 * Evaluates and ranks all supported activities based on a 7-day weather forecast.
 *
 * Utilizes the Strategy pattern by injecting a set of [ActivityScorer]s.
 * Each scorer evaluates its specific activity independently, satisfying the Open/Closed Principle.
 */
class GetRankedActivitiesUseCase @Inject constructor(
    private val scorers: Set<@JvmSuppressWildcards ActivityScorer>
) {

    /**
     * Executes the ranking algorithm across all injected scorers.
     *
     * @param forecast The target 7-day forecast.
     * @return A list of [RankedActivity] sorted by score in descending order.
     */
    operator fun invoke(forecast: WeatherForecast): List<RankedActivity> {
        val dailyForecasts = forecast.dailyForecasts
        if (dailyForecasts.isEmpty()) return emptyList()

        return scorers.map { scorer ->
            scorer.score(dailyForecasts)
        }.sortedByDescending { it.score }
    }
}
