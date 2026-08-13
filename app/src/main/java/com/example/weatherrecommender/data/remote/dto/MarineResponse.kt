package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (DTO) for the Open-Meteo Marine Weather API response.
 *
 * The Marine API only returns meaningful values for coordinates near open water; for inland
 * coordinates the [MarineDailyDto.waveHeightMax] entries come back as nulls. This makes the
 * presence of non-null wave heights a reliable "has sea access" signal.
 *
 * @property latitude Geographic latitude of the query point.
 * @property longitude Geographic longitude of the query point.
 * @property daily Encapsulated daily marine metrics, or null for inland coordinates.
 */
@Serializable
data class MarineResponse(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("daily") val daily: MarineDailyDto? = null
)

/**
 * Encapsulates the daily marine metrics array. Each list is aligned by date with the forecast.
 *
 * @property time List of ISO-8601 date strings.
 * @property waveHeightMax List of daily maximum wave heights in meters (null for days without marine data).
 */
@Serializable
data class MarineDailyDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("wave_height_max") val waveHeightMax: List<Double?> = emptyList()
)
