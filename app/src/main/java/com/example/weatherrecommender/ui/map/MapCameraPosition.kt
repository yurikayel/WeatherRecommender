package com.example.weatherrecommender.ui.map

/**
 * Camera position for the persistent header map.
 * Held in [com.example.weatherrecommender.ui.WeatherUiState] so it survives home↔detail navigation.
 */
data class MapCameraPosition(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
) {
    companion object {
        /** Wide world view shown before the user picks a city. */
        val DEFAULT = MapCameraPosition(latitude = 20.0, longitude = 0.0, zoom = 1.4)

        const val CITY_ZOOM = 10.5
        const val DETAIL_ZOOM = 11.0
    }
}
