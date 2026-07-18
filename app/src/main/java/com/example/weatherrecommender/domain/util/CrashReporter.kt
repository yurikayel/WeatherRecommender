package com.example.weatherrecommender.domain.util

/**
 * Abstraction for reporting uncaught errors in production.
 * Swap the implementation for Firebase Crashlytics or similar when configured.
 */
interface CrashReporter {
    fun recordException(throwable: Throwable, message: String? = null)
}
