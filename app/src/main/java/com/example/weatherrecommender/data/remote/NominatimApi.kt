package com.example.weatherrecommender.data.remote

import com.example.weatherrecommender.data.remote.dto.NominatimResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Nominatim reverse geocoding (OpenStreetMap).
 *
 * Used when the user taps the map: Open-Meteo Geocoding has no reverse endpoint.
 * Callers must send a descriptive [User-Agent] per
 * [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/).
 */
interface NominatimApi {
    /**
     * Resolves a map tap (or GPS fix) to an OSM place at [latitude]/[longitude].
     * [zoom] 10 prefers city-scale results; [userAgent] must identify this app per Nominatim policy.
     */
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("zoom") zoom: Int = 10,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): NominatimResponse

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/"
        const val USER_AGENT =
            "WeatherRecommender/1.0 (https://github.com/yurikayel/WeatherRecommender)"
    }
}
