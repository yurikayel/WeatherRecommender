package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Builds the home screen's "top picks": a set of randomly chosen, population-weighted featured
 * cities, each paired with its single best activity for the nearest forecast day.
 *
 * Forecasts for the candidates are fetched concurrently and best-effort — a city that fails to load
 * (e.g. while offline) is simply dropped from the results rather than failing the whole feed.
 * Results are cached in-memory for [TopPicksCache] TTL to avoid repeated cold-start bursts.
 */
class GetTopPicksUseCase @Inject constructor(
    private val repository: WeatherRepository,
    private val getRankedActivities: GetRankedActivitiesUseCase,
    private val featuredCities: FeaturedCities,
    private val topPicksCache: TopPicksCache
) {

    /** Returns cached picks, or fetches a fresh weighted sample when forced or stale. */
    suspend operator fun invoke(count: Int = DEFAULT_COUNT, forceRefresh: Boolean = false): List<TopPick> {
        if (!forceRefresh) {
            topPicksCache.getIfFresh()?.let { return it }
        }

        val picks = fetchTopPicks(count)
        topPicksCache.put(picks)
        return picks
    }

    /** Loads [count] featured cities in parallel, dropping any that fail to forecast. */
    private suspend fun fetchTopPicks(count: Int): List<TopPick> = coroutineScope {
        featuredCities.randomWeightedByPopulation(count)
            .mapIndexed { index, location -> async { fetchOneTopPick(index, location) } }
            .awaitAll()
            .filterNotNull()
    }

    /** Staggers then fetches one city's remote forecast into a [TopPick], or null on error. */
    private suspend fun fetchOneTopPick(index: Int, location: Location): TopPick? {
        if (index > 0) delay(FETCH_STAGGER_MS.milliseconds * index)
        return when (val result = repository.getForecastRemote(location)) {
            is Result.Success -> topPickFrom(result.data)
            is Result.Error -> null
        }
    }

    /** Builds a [TopPick] when today's forecast and a ranked activity both exist. */
    private fun topPickFrom(forecast: WeatherForecast): TopPick? {
        val today = forecast.dailyForecasts.firstOrNull()
        val best = getRankedActivities(forecast, 0).firstOrNull()
        if (today == null || best == null) return null
        return TopPick(
            location = forecast.location,
            topActivity = best,
            weatherCode = today.weatherCode,
            maxTemp = today.maxTemp
        )
    }

    private companion object {
        const val DEFAULT_COUNT = 5
        const val FETCH_STAGGER_MS = 150
    }
}
