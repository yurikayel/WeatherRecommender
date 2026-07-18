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
         * Default home framing (London), also used by Compose previews / snapshot defaults.
         * [com.example.weatherrecommender.ui.WeatherViewModel] re-centers the home camera
         * on the device location when one is available.
         *
         * Zoom values are ~30% further out than the previous 7.0 / 8.5 / 9.0 set
         * (× 0.7) so the visible area grows roughly 30% at each level.
         */
        val DEFAULT = MapCameraPosition(
            latitude = LONDON_LAT,
            longitude = LONDON_LNG,
            zoom = HOME_DEFAULT_ZOOM
        )

        /** City overview — neighboring cities stay visible (not street-level). */
        const val CITY_ZOOM = 6.0

        /** Slightly closer when a city is selected, still regional overview. */
        const val DETAIL_ZOOM = 6.3

        const val LONDON_LAT = 51.5074
        const val LONDON_LNG = -0.1278

        /** Home / overview zoom (~7.0 × 0.7). */
        const val HOME_DEFAULT_ZOOM = 4.9
    }
}
