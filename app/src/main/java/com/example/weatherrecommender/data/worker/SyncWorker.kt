package com.example.weatherrecommender.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.weatherrecommender.domain.util.CrashReporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Background worker responsible for keeping cached weather forecasts up to date.
 * Executes periodically via WorkManager to fetch the latest data for all saved locations.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun locationSyncer(): LocationSyncer
        fun crashReporter(): CrashReporter
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )
            if (entryPoint.locationSyncer().syncAllLocations()) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            ).crashReporter().recordException(e, "SyncWorker failed")
            Result.retry()
        }
    }
}
