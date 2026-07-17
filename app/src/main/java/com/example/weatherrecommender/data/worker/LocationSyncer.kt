package com.example.weatherrecommender.data.worker

import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.mapper.toDomain
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.repository.WeatherRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Refreshes forecasts for every location currently stored in Room.
 * Extracted from [SyncWorker] so sync behaviour can be unit tested without WorkManager.
 */
class LocationSyncer @Inject constructor(
    private val repository: WeatherRepository,
    private val weatherDao: WeatherDao
) {

    suspend fun syncAllLocations(): Boolean = coroutineScope {
        val locations = weatherDao.getAllLocations()
        if (locations.isEmpty()) {
            return@coroutineScope true
        }

        val results = locations.map { locationEntity ->
            async { repository.refreshForecast(locationEntity.toDomain()) }
        }.awaitAll()

        results.all { it is Result.Success }
    }
}
