package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.ActivityContext
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.usecase.scorer.ActivityScorer
import javax.inject.Inject

/**
 * Evaluates and ranks the activities that make sense for a location on a single day.
 *
 * Uses the Strategy pattern by injecting a set of [ActivityScorer]s. Each scorer decides whether
 * it applies to the location's geography and, if so, scores the given day independently. This
 * satisfies the Open/Closed Principle: adding a new activity is a new [ActivityScorer]
 * added to the injected set; this use case is not edited.
 */
class GetRankedActivitiesUseCase @Inject constructor(
    private val scorers: Set<@JvmSuppressWildcards ActivityScorer>
) {

    /**
     * Ranks the applicable activities for the day at [dayIndex] of the [forecast].
     *
     * @param forecast The forecast whose location and days are being evaluated.
     * @param dayIndex Index into [WeatherForecast.dailyForecasts]; defaults to the first day.
     * @return Applicable [RankedActivity]s sorted by score descending, or empty if the day is missing.
     */
    operator fun invoke(forecast: WeatherForecast, dayIndex: Int = 0): List<RankedActivity> {
        val day = forecast.dailyForecasts.getOrNull(dayIndex) ?: return emptyList()
        val context = ActivityContext(location = forecast.location, day = day)

        return scorers
            .filter { it.isApplicable(context) }
            .map { it.score(context) }
            .sortedByDescending { it.score }
    }
}
