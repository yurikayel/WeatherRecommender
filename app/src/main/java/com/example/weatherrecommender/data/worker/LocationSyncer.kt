package com.example.weatherrecommender.data.worker

import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.mapper.toDomain
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.repository.WeatherRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Refreshes forecasts for every location currently stored in Room.
 * Extracted from [SyncWorker] so sync behaviour can be unit tested without WorkManager.
 *
 * Refreshes run in small concurrent chunks (not a full parallel fan-out) with a short delay
 * between batches, matching the stagger approach used by home top-picks fetching, to reduce
 * Open-Meteo rate-limit pressure when many cities are cached.
 */
class LocationSyncer @Inject constructor(
    private val repository: WeatherRepository,
    private val weatherDao: WeatherDao
) {

    suspend fun syncAllLocations(): Boolean {
        val locations = weatherDao.getAllLocations()
        if (locations.isEmpty()) {
            return true
        }

        val results = mutableListOf<Result<Unit, AppError>>()
        locations.chunked(CHUNK_SIZE).forEachIndexed { batchIndex, chunk ->
            if (batchIndex > 0) {
                delay(BATCH_STAGGER_MS.milliseconds)
            }
            coroutineScope {
                val batchResults = chunk.map { locationEntity ->
                    async { repository.refreshForecast(locationEntity.toDomain()) }
                }.awaitAll()
                results += batchResults
            }
        }

        return results.all { it is Result.Success }
    }

    private companion object {
        const val CHUNK_SIZE = 3
        const val BATCH_STAGGER_MS = 150
    }
}
