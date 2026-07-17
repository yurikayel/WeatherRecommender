package com.example.weatherrecommender.data.remote

import com.example.weatherrecommender.data.remote.dto.ForecastResponse
import com.example.weatherrecommender.data.remote.dto.GeocodingResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the Open-Meteo Geocoding API.
 * Used to resolve city names into geographic coordinates.
 */
interface GeocodingApi {
    /**
     * Searches for a location by name.
     *
     * @param name The name of the city to search for.
     * @param count Maximum number of results to return.
     * @param language Language of the results.
     * @param format Response format.
     * @return The geocoding response containing matching locations.
     */
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}

/**
 * Retrofit interface for the Open-Meteo Forecast API.
 * Used to fetch daily weather metrics for a specific geographic coordinate.
 */
interface ForecastApi {
    /**
     * Retrieves the 7-day weather forecast for a given location.
     *
     * @param latitude The geographic latitude.
     * @param longitude The geographic longitude.
     * @param daily Comma-separated list of daily weather variables to query.
     * @param timezone Timezone for the forecast data.
     * @return The forecast response containing daily weather metrics.
     */
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_sum,snowfall_sum,windspeed_10m_max,weathercode",
        @Query("timezone") timezone: String = "auto"
    ): ForecastResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}
