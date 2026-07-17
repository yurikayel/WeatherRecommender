package com.example.weatherrecommender.data.mapper

import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.local.entity.LocationEntity
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location

/**
 * Maps the Room [LocationEntity] to the Domain [Location] model.
 * Reconstructs the location representation for business logic and UI presentation.
 *
 * @return The domain representation of this location.
 */
fun LocationEntity.toDomain(): Location {
    return Location(
        id = this.id,
        name = this.name,
        latitude = this.latitude,
        longitude = this.longitude,
        country = this.country,
        admin1 = this.admin1
    )
}

/**
 * Maps the Domain [Location] to the Room [LocationEntity] model.
 * Prepares the location for persistent offline storage.
 *
 * @param lastUpdated Timestamp of the last successful network synchronization. Defaults to current system time.
 * @return The database entity representation of this location.
 */
fun Location.toEntity(lastUpdated: Long = System.currentTimeMillis()): LocationEntity {
    return LocationEntity(
        id = this.id,
        name = this.name,
        latitude = this.latitude,
        longitude = this.longitude,
        country = this.country ?: "",
        admin1 = this.admin1,
        lastUpdated = lastUpdated
    )
}

/**
 * Maps the Room [DailyForecastEntity] to the Domain [DailyForecast] model.
 *
 * @return The domain representation of this daily forecast.
 */
fun DailyForecastEntity.toDomain(): DailyForecast {
    return DailyForecast(
        date = this.date,
        weatherCode = this.weatherCode,
        maxTemp = this.maxTemp,
        minTemp = this.minTemp,
        precipitationSum = this.precipitationSum,
        snowfallSum = this.snowfallSum,
        maxWindSpeed = this.maxWindSpeed
    )
}
