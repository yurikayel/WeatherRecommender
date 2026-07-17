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
     * Resolves a map tap (lat/lng) to a named [Location] via reverse geocoding.
     *
     * Open-Meteo Geocoding is forward-only; reverse uses Nominatim (OSM).
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): AppResult<Location>

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

    /**
     * Fetches a forecast for a location directly from the network without touching the cache.
     *
     * Used for transient, read-only needs such as building the home screen's "top picks", where
     * persisting every previewed city would pollute the offline cache.
     *
     * @param location The target location.
     * @return An [AppResult] containing the freshly fetched [WeatherForecast].
     */
    suspend fun getForecastRemote(location: Location): AppResult<WeatherForecast>

    /**
     * Streams the most recently viewed locations for the home History section.
     * Only locations the user explicitly selected are included (not top-picks previews).
     *
     * @param limit Maximum number of history entries (newest first). Defaults to 10.
     */
    fun observeRecentLocations(limit: Int = 10): Flow<List<Location>>

    /**
     * Records that the user opened [location] (search result or top pick tap).
     * Updates [lastViewedAt] without requiring a successful forecast refresh.
     */
    suspend fun markLocationViewed(location: Location)
}
