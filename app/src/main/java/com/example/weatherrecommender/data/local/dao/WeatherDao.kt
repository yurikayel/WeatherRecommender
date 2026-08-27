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
    /**
     * Inserts or replaces a [LocationEntity] into the database.
     * This is typically called after fetching fresh geocoding data.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    /**
     * Inserts or replaces a list of [DailyForecastEntity] for a location.
     * Replaces existing forecasts for the same date and location.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyForecasts(forecasts: List<DailyForecastEntity>)

    /**
     * Observes a specific location by its [locationId], emitting updates whenever
     * the underlying database record changes.
     */
    @Query("SELECT * FROM location_entity WHERE id = :locationId")
    fun getLocationFlow(locationId: Long): Flow<LocationEntity?>

    /**
     * Observes the daily forecasts for a specific location, ordered chronologically.
     * Emits a new list whenever the forecasts for this location are updated.
     */
    @Query("SELECT * FROM daily_forecast_entity WHERE locationId = :locationId ORDER BY date ASC")
    fun getDailyForecastsFlow(locationId: Long): Flow<List<DailyForecastEntity>>

    /**
     * One-shot read of daily forecasts for [locationId], oldest date first.
     * Used by workers and hop logic that cannot wait on a Flow emission.
     */
    @Query("SELECT * FROM daily_forecast_entity WHERE locationId = :locationId ORDER BY date ASC")
    suspend fun getDailyForecasts(locationId: Long): List<DailyForecastEntity>

    /**
     * Retrieves a single location by its [locationId] for one-shot reads (e.g., workers).
     */
    @Query("SELECT * FROM location_entity WHERE id = :locationId")
    suspend fun getLocation(locationId: Long): LocationEntity?

    /**
     * Retrieves all cached locations, ordered by when they were last updated (newest first).
     * Used mainly for cache synchronization and maintenance workers.
     */
    @Query("SELECT * FROM location_entity ORDER BY lastUpdated DESC")
    suspend fun getAllLocations(): List<LocationEntity>
    
    /**
     * Observes all cached locations, emitting updates whenever any location is added,
     * updated, or removed. Ordered by last update time (newest first).
     */
    @Query("SELECT * FROM location_entity ORDER BY lastUpdated DESC")
    fun getAllLocationsFlow(): Flow<List<LocationEntity>>

    /**
     * Deletes all forecasts associated with the specified [locationId].
     * Typically called before inserting a fresh set of forecasts for the location.
     */
    @Query("DELETE FROM daily_forecast_entity WHERE locationId = :locationId")
    suspend fun deleteForecastsForLocation(locationId: Long)

    /**
     * Deletes a specific location from the database by its [locationId].
     */
    @Query("DELETE FROM location_entity WHERE id = :locationId")
    suspend fun deleteLocation(locationId: Long)

    /**
     * Counts the total number of distinct locations currently stored in the database.
     * Useful for enforcing local cache limits.
     */
    @Query("SELECT COUNT(*) FROM location_entity")
    suspend fun getLocationCount(): Int

    /**
     * IDs of never-opened prefetch rows (`lastViewedAt = 0`), oldest forecast first, capped at [count].
     * Eviction prefers these over cities the user actually viewed.
     */
    @Query("SELECT id FROM location_entity WHERE lastViewedAt = 0 ORDER BY lastUpdated ASC LIMIT :count")
    suspend fun getUnviewedOldestIds(count: Int): List<Long>

    /**
     * Retrieves the IDs of the oldest (least recently updated) locations, limited by [count].
     * Used by cache eviction policies to identify stale data.
     */
    @Query("SELECT id FROM location_entity ORDER BY lastUpdated ASC LIMIT :count")
    suspend fun getOldestLocationIds(count: Int): List<Long>

    /**
     * Viewed history IDs, least-recently opened first, capped at [count].
     * Overflow eviction uses this so a 6h weather sync does not look “newer” than a city the user
     * opened today.
     */
    @Query(
        """
        SELECT id FROM location_entity
        WHERE lastViewedAt > 0
        ORDER BY lastViewedAt ASC
        LIMIT :count
        """
    )
    suspend fun getLeastRecentlyViewedIds(count: Int): List<Long>

    /**
     * Streams the most recently viewed locations for the home History section.
     * Rows with [LocationEntity.lastViewedAt] == 0 were never explicitly opened by the user.
     * Callers that dedupe should request a larger [limit] so collapse still fills the UI cap.
     */
    @Query(
        """
        SELECT * FROM location_entity
        WHERE lastViewedAt > 0
        ORDER BY lastViewedAt DESC
        LIMIT :limit
        """
    )
    fun getRecentLocationsFlow(limit: Int): Flow<List<LocationEntity>>

    /**
     * Updates the timestamp for when a location was last viewed by the user.
     * This ensures the location bubbles to the top of the Home History section.
     */
    @Query("UPDATE location_entity SET lastViewedAt = :timestamp WHERE id = :locationId")
    suspend fun updateLastViewedAt(locationId: Long, timestamp: Long)

    /**
     * Patches ISO [countryCode] without rewriting weather. Used when a TTL skip would otherwise
     * leave a nearby/catalog row with a null country after Open-Meteo later supplied the code.
     */
    @Query("UPDATE location_entity SET countryCode = :countryCode WHERE id = :locationId")
    suspend fun updateCountryCode(locationId: Long, countryCode: String)

    /**
     * Patches the Wikipedia postcard URL (or a confirmed miss) without rewriting daily forecasts.
     * Emits on [getLocationFlow] so the detail hero can fill in after weather already painted.
     */
    @Query(
        """
        UPDATE location_entity
        SET imageUrl = :imageUrl, placeMetadataUpdatedAt = :timestamp
        WHERE id = :locationId
        """
    )
    suspend fun updatePlaceImage(locationId: Long, imageUrl: String?, timestamp: Long)

    /**
     * Finds cached locations near a coordinate (≈ [delta] degrees ≈ a few km).
     * Used to merge Nominatim reverse-geocode ids with existing GeoNames rows.
     */
    @Query(
        """
        SELECT * FROM location_entity
        WHERE ABS(latitude - :latitude) <= :delta
          AND ABS(longitude - :longitude) <= :delta
        """
    )
    suspend fun findLocationsNear(
        latitude: Double,
        longitude: Double,
        delta: Double
    ): List<LocationEntity>

    /**
     * Finds cached locations with the same normalized name and country
     * (case-insensitive trim handled by the repository before calling).
     */
    @Query(
        """
        SELECT * FROM location_entity
        WHERE LOWER(name) = LOWER(:name)
          AND LOWER(country) = LOWER(:country)
        """
    )
    suspend fun findLocationsByNameAndCountry(
        name: String,
        country: String
    ): List<LocationEntity>

    /**
     * Atomically deletes a location and its associated daily forecasts.
     * Guaranteed to execute within a single SQLite transaction.
     */
    @Transaction
    suspend fun deleteLocationWithForecasts(locationId: Long) {
        deleteForecastsForLocation(locationId)
        deleteLocation(locationId)
    }

    /**
     * Atomically inserts a location and its associated daily forecasts, clearing out
     * any existing forecasts for that location to avoid stale overlaps.
     */
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
