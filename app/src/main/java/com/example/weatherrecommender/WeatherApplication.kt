package com.example.weatherrecommender

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.weatherrecommender.data.worker.SyncWorker
import com.example.weatherrecommender.domain.util.CrashReporter
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Base Application class for WeatherRecommender.
 * Initializes Hilt and schedules background synchronization workers.
 */
@HiltAndroidApp
class WeatherApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var crashReporter: CrashReporter
    
    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            // Wikipedia returns max-age=0. Setting this to false forces Coil to use 
            // the cached image instead of re-fetching/re-validating it every time.
            .respectCacheHeaders(false)
            .build()
    }

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
