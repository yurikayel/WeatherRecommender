package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (DTO) for the Open-Meteo geocoding API response.
 *
 * @property results List of matching location search results, or null if no matches were found.
 */
@Serializable
data class GeocodingResponse(
    @SerialName("results")
    val results: List<GeocodingLocationDto>? = null
)

/**
 * Represents a single location match from the geocoding search.
 *
 * @property id Unique GeoNames identifier for the place.
 * @property name Primary name of the location.
 * @property latitude Geographic latitude coordinate.
 * @property longitude Geographic longitude coordinate.
 * @property country Country name (optional).
 * @property admin1 Primary administrative division or state/region (optional).
 * @property elevation Ground elevation in meters (optional).
 * @property population Estimated population count (optional).
 * @property featureCode GeoNames feature classification code (optional).
 */
@Serializable
data class GeocodingLocationDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("country") val country: String? = null,
    @SerialName("admin1") val admin1: String? = null, // State/Region
    @SerialName("elevation") val elevation: Double? = null,
    @SerialName("population") val population: Long? = null,
    @SerialName("feature_code") val featureCode: String? = null
)
