package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reverse-geocode payload from the Nominatim (OpenStreetMap) API.
 *
 * Open-Meteo Geocoding is forward-only (name → coordinates), so map taps use Nominatim.
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

@Serializable
data class NominatimAddress(
    @SerialName("city") val city: String? = null,
    @SerialName("town") val town: String? = null,
    @SerialName("village") val village: String? = null,
    @SerialName("municipality") val municipality: String? = null,
    @SerialName("county") val county: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("country") val country: String? = null
)
