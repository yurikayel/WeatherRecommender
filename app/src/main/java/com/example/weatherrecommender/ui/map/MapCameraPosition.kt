package com.example.weatherrecommender.ui.map

/**
 * How long the map flies and when the sheet swaps, based on whether Room already has a fresh
 * forecast. Cached hops stay snappy; cache misses keep a longer fly so the network fetch can
 * land while the camera is still moving.
 */
enum class MapHopProfile {
    /** Stale or missing weather — slower fly; sheet 500 ms before the camera lands. */
    CACHE_MISS,

    /** Fresh weather in Room — shorter fly and delay; sheet 200 ms before land. */
    CACHED;

    val delayMs: Long
        get() = when (this) {
            CACHE_MISS -> MapCameraPosition.RELOCATE_DELAY_MISS_MS
            CACHED -> MapCameraPosition.RELOCATE_DELAY_CACHED_MS
        }

    val durationMs: Long
        get() = when (this) {
            CACHE_MISS -> MapCameraPosition.RELOCATE_DURATION_MISS_MS
            CACHED -> MapCameraPosition.RELOCATE_DURATION_CACHED_MS
        }

    val leadMs: Long
        get() = when (this) {
            CACHE_MISS -> MapCameraPosition.CONTENT_REVEAL_LEAD_MISS_MS
            CACHED -> MapCameraPosition.CONTENT_REVEAL_LEAD_CACHED_MS
        }

    /** Sheet reveal: delay + flight − lead so content appears before the camera settles. */
    val contentRevealMs: Long
        get() = delayMs + durationMs - leadMs
}

/**
 * Camera position for the in-screen map.
 * Held in [com.example.weatherrecommender.ui.WeatherUiState] so it survives home↔detail navigation.
 */
data class MapCameraPosition(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val hop: MapHopProfile = MapHopProfile.CACHE_MISS
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

        /**
         * Cache-miss pause before flyTo so the pin can land. Cached hops use a shorter pause so
         * the 600 ms flight is the dominant motion while the map still moves first.
         */
        const val RELOCATE_DELAY_MISS_MS = 350L
        const val RELOCATE_DELAY_CACHED_MS = 150L

        const val RELOCATE_DURATION_MISS_MS = 1200L
        const val RELOCATE_DURATION_CACHED_MS = 600L

        const val CONTENT_REVEAL_LEAD_MISS_MS = 500L
        const val CONTENT_REVEAL_LEAD_CACHED_MS = 200L
    }
}
