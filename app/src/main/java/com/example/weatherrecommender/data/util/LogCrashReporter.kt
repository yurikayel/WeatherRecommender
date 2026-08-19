package com.example.weatherrecommender.data.util

import android.util.Log
import com.example.weatherrecommender.domain.util.CrashReporter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WeatherRecommender"

/**
 * Logs exceptions locally. Replace with Crashlytics in production builds when configured.
 */
@Singleton
class LogCrashReporter @Inject constructor() : CrashReporter {
    /** Writes [throwable] to logcat, prefixing [message] when the caller supplied one. */
    override fun recordException(throwable: Throwable, message: String?) {
        if (message != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, "Unhandled exception", throwable)
        }
    }
}
