package com.example.weatherrecommender.domain.repository

import com.example.weatherrecommender.domain.model.AppResult
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.WeatherForecast
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for weather and location data.
 * Handles fetching, caching, and streaming of [WeatherForecast]s.
 */
interface WeatherRepository {
    /**
     * Searches for a city by name via geocoding service.
     *
     * @param query The city name to search for.
     * @return An [AppResult] containing a list of matched [Location]s.
     */
    suspend fun searchCity(query: String): AppResult<List<Location>>

    /**
     * Streams the weather forecast for a specific location.
     * Emits new data when the local cache is updated.
     *
     * @param location The target location.
     * @return A [Flow] of [WeatherForecast], or null if not yet cached.
     */
    fun getForecastFlow(location: Location): Flow<WeatherForecast?>

    /**
     * Forces a network refresh of the forecast for a specific location.
     * Updates the local cache upon success, triggering [getForecastFlow].
     *
     * @param location The target location to refresh.
     * @return An [AppResult] indicating success or failure.
     */
    suspend fun refreshForecast(location: Location): AppResult<Unit>
}
