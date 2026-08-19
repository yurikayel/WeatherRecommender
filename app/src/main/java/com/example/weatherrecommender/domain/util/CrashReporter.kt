package com.example.weatherrecommender.domain.util

/**
 * Abstraction for reporting uncaught errors in production.
 * Swap the implementation for Firebase Crashlytics or similar when configured.
 */
interface CrashReporter {
    /** Records [throwable], optionally with a caller [message] describing the failure site. */
    fun recordException(throwable: Throwable, message: String? = null)
}
