package com.example.weatherrecommender.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.Semaphore

/**
 * Caps concurrent HTTP calls whose host ends with [hostSuffix].
 *
 * Forecast, geocoding, and marine all live under `open-meteo.com`. Top picks, nearby
 * prefetch, country-warm, and WorkManager are separate Kotlin lanes — without a shared
 * gate they can still stack past Open-Meteo's courtesy limit. Wikipedia is another host
 * and is not gated here. Nominatim uses a second limiter (1 in-flight) on the same client.
 *
 * Placed outside [RateLimitRetryInterceptor] so a 429 backoff keeps occupying a slot
 * instead of letting another lane fire during the sleep.
 */
class HostConcurrencyLimiter(
    private val maxInFlight: Int,
    private val hostSuffix: String
) : Interceptor {

    private val gate = Semaphore(maxInFlight, true)

    init {
        require(maxInFlight > 0) { "maxInFlight must be positive" }
        require(hostSuffix.isNotBlank()) { "hostSuffix must be non-blank" }
    }

    /** Acquires a slot for matching hosts, then proceeds; non-matching hosts skip the gate. */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.endsWith(hostSuffix, ignoreCase = true)) {
            return chain.proceed(request)
        }
        try {
            gate.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted waiting for a $hostSuffix slot", e)
        }
        try {
            return chain.proceed(request)
        } finally {
            gate.release()
        }
    }

    companion object {
        /** Shared suffix for forecast, geocoding, and marine hosts. */
        const val OPEN_METEO_HOST_SUFFIX = "open-meteo.com"

        /** Matches [com.example.weatherrecommender.data.worker.LocationSyncer] chunk size. */
        const val OPEN_METEO_MAX_IN_FLIGHT = 3

        /** Nominatim lives under this OSM suffix (`nominatim.openstreetmap.org`). */
        const val NOMINATIM_HOST_SUFFIX = "openstreetmap.org"

        /** Nominatim usage policy is ~1 request per second; one in-flight is the hard cap. */
        const val NOMINATIM_MAX_IN_FLIGHT = 1
    }
}
