package com.example.weatherrecommender.domain.model

/**
 * Offline cache windows aligned with how often each kind of data actually changes.
 *
 * Weather: Open-Meteo stitches national models as they land. Global runs are typically every
 * **6 hours**; some regional models update every 1–3 hours. This app stores **daily** summaries
 * (max/min, precip, wind), which do not need hourly refreshes, so 6 hours matches both the
 * global cadence and [com.example.weatherrecommender.data.worker.SyncWorker].
 *
 * Place metadata: city name, coordinates, and Wikipedia thumbnail are effectively static.
 */
object CachePolicy {
    /** Skip Open-Meteo forecast/marine when Room is newer than this. */
    const val WEATHER_TTL_MS = 6L * 60L * 60L * 1000L

    /** Skip Wikipedia / name re-fetch when a thumbnail is already stored. */
    const val PLACE_METADATA_TTL_MS = 30L * 24L * 60L * 60L * 1000L

    const val NEARBY_MIN_KM = 25.0
    const val NEARBY_RADIUS_KM = 280.0
    const val NEARBY_LIMIT = 4
    const val NEARBY_MIN_POPULATION = 80_000L
}
