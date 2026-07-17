package com.example.weatherrecommender.ui.map

/**
 * Camera position for the in-screen map.
 * Held in [com.example.weatherrecommender.ui.WeatherUiState] so it survives home↔detail navigation.
 */
data class MapCameraPosition(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
) {
    companion object {
        /**
         * Home default: centered on London with zoom out enough that Paris sits near the
         * SE corner of a square, phone-width MapLibre viewport (~980 px).
         *
         * London ≈ 51.51°N, 0.13°W; Paris ≈ 48.85°N, 2.35°E (Δlat ≈ 2.65°, Δlng ≈ 2.48°).
         * At MapLibre zoom 7 on a ~980 px square (512 px tiles), half-span ≈ 2.7° — Paris
         * lands near the edge. Zoom 5–6 would leave Paris mid-quadrant on a full-width square.
         */
        val DEFAULT = MapCameraPosition(
            latitude = LONDON_LAT,
            longitude = LONDON_LNG,
            zoom = HOME_DEFAULT_ZOOM
        )

        /** City overview — neighboring cities stay visible (not street-level). */
        const val CITY_ZOOM = 8.5

        /** Slightly closer when a city is selected, still regional overview. */
        const val DETAIL_ZOOM = 9.0

        const val LONDON_LAT = 51.5074
        const val LONDON_LNG = -0.1278
        const val HOME_DEFAULT_ZOOM = 7.0
    }
}
