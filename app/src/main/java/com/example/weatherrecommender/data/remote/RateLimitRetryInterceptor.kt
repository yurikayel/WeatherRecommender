package com.example.weatherrecommender.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Retries idempotent GET requests when the server responds with HTTP 429.
 * Honors [Retry-After] when present; otherwise uses exponential backoff.
 */
class RateLimitRetryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") {
            return chain.proceed(request)
        }

        var attempt = 0
        while (true) {
            val response = chain.proceed(request)
            if (response.code != HTTP_TOO_MANY_REQUESTS || attempt >= MAX_RETRIES) {
                return response
            }
            response.close()

            val delayMs = retryDelayMs(response.header(RETRY_AFTER_HEADER), attempt)
            Thread.sleep(delayMs)
            attempt++
        }
    }

    private fun retryDelayMs(retryAfterHeader: String?, attempt: Int): Long {
        retryAfterHeader?.toLongOrNull()?.let { seconds ->
            return TimeUnit.SECONDS.toMillis(seconds.coerceAtMost(MAX_RETRY_AFTER_SECONDS))
        }
        return INITIAL_BACKOFF_MS shl attempt
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val RETRY_AFTER_HEADER = "Retry-After"
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 500L
        const val MAX_RETRY_AFTER_SECONDS = 60L
    }
}
