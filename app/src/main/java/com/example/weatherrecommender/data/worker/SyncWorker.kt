package com.example.weatherrecommender.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.mapper.toDomain
import com.example.weatherrecommender.domain.model.Result as AppResultModel
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.util.CrashReporter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
        fun weatherRepository(): WeatherRepository
        fun weatherDao(): WeatherDao
        fun crashReporter(): CrashReporter
    }

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )
            val repository = entryPoint.weatherRepository()
            val dao = entryPoint.weatherDao()
            val crashReporter = entryPoint.crashReporter()

            val locations = dao.getAllLocations()

            val jobs = locations.map { locationEntity ->
                async {
                    repository.refreshForecast(locationEntity.toDomain())
                }
            }

            val results = jobs.awaitAll()

            if (results.all { it is AppResultModel.Success }) {
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
