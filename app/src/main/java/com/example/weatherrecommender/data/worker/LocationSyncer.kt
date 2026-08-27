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
 * Refreshes forecasts for locations currently stored in Room.
 * Extracted from [SyncWorker] so sync behaviour can be unit tested without WorkManager.
 *
 * Viewed cities are always eligible (TTL skip happens inside [WeatherRepository.refreshForecast]).
 * Never-opened country-warm rows are capped per run so a 100-city cache cannot fan out ~200
 * Open-Meteo calls every 6 hours.
 *
 * Refreshes run in small concurrent chunks with a short delay between batches to reduce
 * Open-Meteo rate-limit pressure.
 */
class LocationSyncer @Inject constructor(
    private val repository: WeatherRepository,
    private val weatherDao: WeatherDao
) {

    /**
     * Refreshes viewed cities plus a bounded set of oldest unviewed prefetch rows,
     * in chunks of [CHUNK_SIZE].
     * @return true only when every selected city's refresh succeeded.
     */
    suspend fun syncAllLocations(): Boolean {
        val cached = weatherDao.getAllLocations()
        if (cached.isEmpty()) {
            return true
        }
        val viewed = cached.filter { it.lastViewedAt > 0L }
        val unviewed = cached
            .filter { it.lastViewedAt == 0L }
            .sortedBy { it.lastUpdated }
            .take(MAX_UNVIEWED_SYNC)
        val locations = viewed + unviewed

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
        const val BATCH_STAGGER_MS = 400
        const val MAX_UNVIEWED_SYNC = 16
    }
}
