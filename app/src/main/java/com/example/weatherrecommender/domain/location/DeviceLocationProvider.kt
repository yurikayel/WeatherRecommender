package com.example.weatherrecommender.domain.location

/**
 * Abstraction over the device's last-known GPS / network fix.
 *
 * Implemented with Android [android.location.LocationManager] (not Play Services
 * FusedLocationProvider) so the app stays free of a Google Play Services location
 * dependency — consistent with MapLibre / OSM rather than Google Maps.
 */
interface DeviceLocationProvider {

    /** True when ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION is granted. */
    fun hasLocationPermission(): Boolean

    /**
     * Returns the best last-known fix, or null when permission is missing,
     * providers are off, or no cached location exists (common on fresh emulators).
     */
    suspend fun getLastKnownLocation(): GeoCoordinates?
}

/** WGS84 coordinates from the device location stack. */
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double
)
