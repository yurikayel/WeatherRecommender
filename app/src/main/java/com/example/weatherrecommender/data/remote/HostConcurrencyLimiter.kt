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
 * gate they can still stack past Open-Meteo's courtesy limit. Wikipedia and Nominatim
 * are other hosts and are not gated here.
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
            throw IOException("Interrupted waiting for an Open-Meteo slot", e)
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
    }
}
