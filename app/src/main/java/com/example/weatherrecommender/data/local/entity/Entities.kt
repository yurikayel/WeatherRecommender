package com.example.weatherrecommender.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity representing a searched location.
 * Acts as the primary table for storing geocoding results locally.
 */
@Entity(tableName = "location_entity")
data class LocationEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String,
    val admin1: String?,
    val lastUpdated: Long,
    val elevation: Double? = null,
    val population: Long? = null,
    val featureCode: String? = null,
    val hasSeaAccess: Boolean = false,
    /** Epoch millis when the user last opened this location; 0 means never viewed. */
    val lastViewedAt: Long = 0L,
    /** URL to a background image for this city, usually fetched from Wikipedia. */
    val imageUrl: String? = null,
    /**
     * When [imageUrl] (and stable name) were last confirmed. 0 = unknown (treat existing
     * thumbnail as still valid until the next successful persist).
     */
    val placeMetadataUpdatedAt: Long = 0L
)

/**
 * Room database entity representing a single day's weather forecast for a specific location.
 * Links to [LocationEntity] via [locationId].
 */
@Entity(
    tableName = "daily_forecast_entity",
    primaryKeys = ["locationId", "date"]
)
data class DailyForecastEntity(
    val locationId: Long,
    val date: String,
    val maxTemp: Double,
    val minTemp: Double,
    val weatherCode: Int,
    val precipitationSum: Double,
    val maxWindSpeed: Double,
    val snowfallSum: Double,
    val waveHeightMax: Double? = null
)
