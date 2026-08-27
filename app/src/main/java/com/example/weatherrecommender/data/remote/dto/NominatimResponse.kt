package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reverse-geocode payload from the Nominatim (OpenStreetMap) API.
 *
 * Open-Meteo Geocoding is forward-only (name → coordinates), so map taps use Nominatim.
 *
 * @property placeId Unique OpenStreetMap identifier for the place.
 * @property lat Latitude coordinate as a decimal string.
 * @property lon Longitude coordinate as a decimal string.
 * @property displayName Full formatted address string.
 * @property name Localized name of the feature or place.
 * @property address Breakdown of address components (city, state, country, etc.).
 */
@Serializable
data class NominatimResponse(
    @SerialName("place_id") val placeId: Long,
    @SerialName("lat") val lat: String,
    @SerialName("lon") val lon: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("address") val address: NominatimAddress? = null
)

/**
 * Detailed breakdown of address components returned by Nominatim reverse geocoding.
 *
 * @property city Name of the city (if applicable).
 * @property town Name of the town (if applicable).
 * @property village Name of the village (if applicable).
 * @property municipality Name of the municipality (if applicable).
 * @property county Name of the county or district (if applicable).
 * @property state Name of the state, province, or region.
 * @property country Name of the country.
 * @property countryCode ISO 3166-1 alpha-2 country code (often lowercase from Nominatim).
 */
@Serializable
data class NominatimAddress(
    @SerialName("city") val city: String? = null,
    @SerialName("town") val town: String? = null,
    @SerialName("village") val village: String? = null,
    @SerialName("municipality") val municipality: String? = null,
    @SerialName("county") val county: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null
)
