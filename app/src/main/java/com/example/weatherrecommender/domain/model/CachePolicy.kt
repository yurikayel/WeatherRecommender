package com.example.weatherrecommender.domain.model

/**
 * Offline cache windows aligned with how often each kind of data actually changes.
 *
 * Weather: Open-Meteo stitches national models as they land. Global runs are typically every
 * **6 hours**; some regional models update every 1–3 hours. This app stores **daily** summaries
 * (max/min, precip, wind), which do not need hourly refreshes, so 6 hours matches both the
 * global cadence and [com.example.weatherrecommender.data.worker.SyncWorker].
 *
 * Place metadata: city name, coordinates, and Wikipedia thumbnail are effectively static when
 * a URL was found. A confirmed miss uses a shorter window so a later hop can retry without
 * hammering Wikimedia on every recomposition.
 */
object CachePolicy {
    /** Skip Open-Meteo forecast/marine when Room is newer than this. */
    const val WEATHER_TTL_MS = 6L * 60L * 60L * 1000L

    /** Skip Wikipedia / name re-fetch when a thumbnail is already stored. */
    const val PLACE_METADATA_TTL_MS = 30L * 24L * 60L * 60L * 1000L

    /**
     * After a confirmed miss (`imageUrl` null + non-zero [metadataAt]), wait this long before
     * asking Wikipedia again. Much shorter than [PLACE_METADATA_TTL_MS] so La Habana-style
     * failures recover within a session without spamming the API.
     */
    const val PLACE_METADATA_MISS_TTL_MS = 30L * 60L * 1000L

    /**
     * True when a stored thumbnail may be reused.
     * [metadataAt] `0` means never confirmed (Room v8 default) — not forever-fresh.
     */
    fun isPlaceMetadataFresh(cachedUrl: String?, metadataAt: Long, now: Long): Boolean {
        if (cachedUrl.isNullOrBlank() || metadataAt <= 0L) return false
        return now - metadataAt < PLACE_METADATA_TTL_MS
    }

    /**
     * True when Wikipedia should be contacted for a place image.
     *
     * - Never confirmed (`metadataAt` ≤ 0) → fetch
     * - Has a URL within [PLACE_METADATA_TTL_MS] → skip
     * - Has a URL past hit TTL → fetch
     * - Confirmed miss within [PLACE_METADATA_MISS_TTL_MS] → skip
     * - Confirmed miss past miss TTL → fetch
     */
    fun shouldFetchPlaceImage(cachedUrl: String?, metadataAt: Long, now: Long): Boolean {
        if (metadataAt <= 0L) return true
        return if (!cachedUrl.isNullOrBlank()) {
            now - metadataAt >= PLACE_METADATA_TTL_MS
        } else {
            now - metadataAt >= PLACE_METADATA_MISS_TTL_MS
        }
    }

    const val NEARBY_MIN_KM = 25.0
    const val NEARBY_RADIUS_KM = 280.0
    const val NEARBY_LIMIT = 4
    const val NEARBY_MIN_POPULATION = 80_000L
}
