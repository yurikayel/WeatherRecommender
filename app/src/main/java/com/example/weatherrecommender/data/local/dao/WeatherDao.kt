package com.example.weatherrecommender.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for handling local SQLite persistence using Room.
 * Enables Offline-First architecture by storing Locations and their Forecasts.
 */
@Suppress("TooManyFunctions")
@Dao
interface WeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyForecasts(forecasts: List<DailyForecastEntity>)

    @Query("SELECT * FROM location_entity WHERE id = :locationId")
    fun getLocationFlow(locationId: Long): Flow<LocationEntity?>

    @Query("SELECT * FROM daily_forecast_entity WHERE locationId = :locationId ORDER BY date ASC")
    fun getDailyForecastsFlow(locationId: Long): Flow<List<DailyForecastEntity>>

    @Query("SELECT * FROM location_entity WHERE id = :locationId")
    suspend fun getLocation(locationId: Long): LocationEntity?

    @Query("SELECT * FROM location_entity ORDER BY lastUpdated DESC")
    suspend fun getAllLocations(): List<LocationEntity>
    
    @Query("SELECT * FROM location_entity ORDER BY lastUpdated DESC")
    fun getAllLocationsFlow(): Flow<List<LocationEntity>>

    @Query("DELETE FROM daily_forecast_entity WHERE locationId = :locationId")
    suspend fun deleteForecastsForLocation(locationId: Long)

    @Query("DELETE FROM location_entity WHERE id = :locationId")
    suspend fun deleteLocation(locationId: Long)

    @Query("SELECT COUNT(*) FROM location_entity")
    suspend fun getLocationCount(): Int

    @Query("SELECT id FROM location_entity ORDER BY lastUpdated ASC LIMIT :count")
    suspend fun getOldestLocationIds(count: Int): List<Long>

    @Transaction
    suspend fun deleteLocationWithForecasts(locationId: Long) {
        deleteForecastsForLocation(locationId)
        deleteLocation(locationId)
    }

    @Transaction
    suspend fun insertLocationWithForecast(
        location: LocationEntity,
        forecasts: List<DailyForecastEntity>
    ) {
        insertLocation(location)
        deleteForecastsForLocation(location.id)
        insertDailyForecasts(forecasts)
    }
}
