package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (DTO) for the Open-Meteo forecast API response.
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
