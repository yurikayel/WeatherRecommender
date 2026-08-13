package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (DTO) for the Open-Meteo forecast API response.
 *
 * @property latitude Geographic latitude of the forecast point.
 * @property longitude Geographic longitude of the forecast point.
 * @property timezone Timezone identifier for the forecast (e.g., "America/New_York").
 * @property daily Encapsulated 7-day metric arrays.
 */
@Serializable
data class ForecastResponse(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("timezone") val timezone: String,
    @SerialName("daily") val daily: DailyForecastDto
)

/**
 * Encapsulates the daily metrics array returned by the API.
 * Each list contains 7 items, corresponding to the 7-day forecast.
 *
 * @property time List of ISO-8601 date strings (YYYY-MM-DD).
 * @property weatherCode List of WMO weather interpretation codes.
 * @property temperature2mMax List of maximum daily temperatures at 2m above ground in °C.
 * @property temperature2mMin List of minimum daily temperatures at 2m above ground in °C.
 * @property precipitationSum List of daily liquid precipitation sums in mm.
 * @property snowfallSum List of daily snowfall sums in cm.
 * @property windSpeed10mMax List of maximum wind speeds at 10m in km/h.
 */
@Serializable
data class DailyForecastDto(
    @SerialName("time") val time: List<String>,
    @SerialName("weathercode") val weatherCode: List<Int>,
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double>,
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double>,
    @SerialName("precipitation_sum") val precipitationSum: List<Double>,
    @SerialName("snowfall_sum") val snowfallSum: List<Double>,
    @SerialName("windspeed_10m_max") val windSpeed10mMax: List<Double>
)
