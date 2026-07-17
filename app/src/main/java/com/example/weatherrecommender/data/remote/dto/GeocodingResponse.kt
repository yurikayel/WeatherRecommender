package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (DTO) for the Open-Meteo geocoding API response.
 */
@Serializable
data class GeocodingResponse(
    @SerialName("results")
    val results: List<GeocodingLocationDto>? = null
)

/**
 * Represents a single location match from the geocoding search.
 */
@Serializable
data class GeocodingLocationDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("country") val country: String? = null,
    @SerialName("admin1") val admin1: String? = null // State/Region
)
