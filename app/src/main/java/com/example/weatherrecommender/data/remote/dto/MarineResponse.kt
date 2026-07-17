package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (DTO) for the Open-Meteo Marine Weather API response.
 *
 * The Marine API only returns meaningful values for coordinates near open water; for inland
 * coordinates the [MarineDailyDto.waveHeightMax] entries come back as nulls. This makes the
 * presence of non-null wave heights a reliable "has sea access" signal.
 */
@Serializable
data class MarineResponse(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("daily") val daily: MarineDailyDto? = null
)

/**
 * Encapsulates the daily marine metrics array. Each list is aligned by date with the forecast.
 */
@Serializable
data class MarineDailyDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("wave_height_max") val waveHeightMax: List<Double?> = emptyList()
)
