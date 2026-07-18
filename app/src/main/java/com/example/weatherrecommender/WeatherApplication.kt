package com.example.weatherrecommender

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.weatherrecommender.data.worker.SyncWorker
import com.example.weatherrecommender.domain.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Base Application class for WeatherRecommender.
 * Initializes Hilt and schedules background synchronization workers.
 */
@HiltAndroidApp
class WeatherApplication : Application() {

    @Inject
    lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            crashReporter.recordException(throwable, "Uncaught exception")
            defaultHandler?.uncaughtException(thread, throwable)
        }
        scheduleSyncWorker()
    }

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weather_sync_work",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }
}
