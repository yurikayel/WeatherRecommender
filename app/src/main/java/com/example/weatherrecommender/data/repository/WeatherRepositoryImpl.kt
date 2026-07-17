package com.example.weatherrecommender.data.repository

import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.mapper.toDomain
import com.example.weatherrecommender.data.mapper.toEntity
import com.example.weatherrecommender.data.remote.ForecastApi
import com.example.weatherrecommender.data.remote.GeocodingApi
import com.example.weatherrecommender.data.remote.MarineApi
import com.example.weatherrecommender.data.remote.NominatimApi
import com.example.weatherrecommender.data.remote.dto.NominatimResponse
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.AppResult
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs

/**
 * Concrete implementation of the [WeatherRepository].
 *
 * Coordinates data fetching between the [GeocodingApi], [ForecastApi], [MarineApi], and local
 * [WeatherDao]. Implements the Offline-First architecture by storing responses in Room as the
 * Single Source of Truth (SSOT).
 *
 * The Marine API is queried alongside the forecast: its wave data enriches surf scoring and, since
 * it only returns values near open water, doubles as a "has sea access" detector.
 */
@Suppress("TooManyFunctions")
class WeatherRepositoryImpl @Inject constructor(
    private val geocodingApi: GeocodingApi,
    private val forecastApi: ForecastApi,
    private val marineApi: MarineApi,
    private val nominatimApi: NominatimApi,
    private val weatherDao: WeatherDao
) : WeatherRepository {

    override suspend fun searchCity(query: String): AppResult<List<Location>> {
        return try {
            val response = geocodingApi.searchCity(query)
            val locations = response.results?.map { dto ->
                Location(
                    id = dto.id,
                    name = dto.name,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    country = dto.country,
                    admin1 = dto.admin1,
                    elevation = dto.elevation,
                    population = dto.population,
                    featureCode = dto.featureCode
                )
            } ?: emptyList()
            Result.Success(locations)
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): AppResult<Location> {
        return try {
            val response = nominatimApi.reverseGeocode(latitude, longitude)
            Result.Success(response.toLocation(fallbackLat = latitude, fallbackLng = longitude))
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    override fun getForecastFlow(location: Location): Flow<WeatherForecast?> {
        return combine(
            weatherDao.getLocationFlow(location.id),
            weatherDao.getDailyForecastsFlow(location.id)
        ) { locationEntity, forecastEntities ->
            if (forecastEntities.isEmpty()) {
                null
            } else {
                // Prefer the persisted location (it carries geography resolved during refresh);
                // fall back to the caller-supplied location before the first successful refresh.
                val resolvedLocation = locationEntity?.toDomain() ?: location
                WeatherForecast(resolvedLocation, forecastEntities.map { it.toDomain() })
            }
        }
    }

    override suspend fun refreshForecast(location: Location): AppResult<Unit> {
        return try {
            val forecast = fetchRemoteForecast(location)
            // Preserve view history across forecast REPLACE upserts (sync must not reset it).
            val existingViewedAt = weatherDao.getLocation(location.id)?.lastViewedAt ?: 0L
            weatherDao.insertLocationWithForecast(
                location = forecast.location.toEntity(lastViewedAt = existingViewedAt),
                forecasts = forecast.dailyForecasts.map { it.toEntity(location.id) }
            )
            evictStaleLocationsIfNeeded()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    /**
     * Keeps the Room cache bounded by evicting the least-recently-updated locations once the
     * cap is exceeded. SyncWorker only iterates stored locations, so this also bounds background sync.
     */
    private suspend fun evictStaleLocationsIfNeeded() {
        val overflow = weatherDao.getLocationCount() - MAX_CACHED_LOCATIONS
        if (overflow <= 0) return

        weatherDao.getOldestLocationIds(overflow).forEach { locationId ->
            weatherDao.deleteLocationWithForecasts(locationId)
        }
    }

    override suspend fun getForecastRemote(location: Location): AppResult<WeatherForecast> {
        return try {
            Result.Success(fetchRemoteForecast(location))
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    override fun observeRecentLocations(limit: Int): Flow<List<Location>> {
        return weatherDao.getRecentLocationsFlow(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun markLocationViewed(location: Location) {
        val now = System.currentTimeMillis()
        val existing = weatherDao.getLocation(location.id)
        if (existing != null) {
            weatherDao.updateLastViewedAt(location.id, now)
        } else {
            // Insert a stub so history appears even if the subsequent forecast refresh fails.
            weatherDao.insertLocation(
                location.toEntity(lastUpdated = now, lastViewedAt = now)
            )
        }
    }

    /**
     * Fetches the forecast (and best-effort marine data) for a location and assembles the domain
     * model, resolving sea access from wave data. Does not persist anything.
     */
    private suspend fun fetchRemoteForecast(location: Location): WeatherForecast {
        val forecastResponse = forecastApi.getForecast(
            latitude = location.latitude,
            longitude = location.longitude
        )
        val daily = forecastResponse.daily

        val waveByDate = fetchWaveHeightsByDate(location)
        val hasSeaAccess = waveByDate?.values?.any { it != null } ?: location.hasSeaAccess

        val dailyForecasts = daily.time.indices.map { i ->
            val date = daily.time.getOrNull(i) ?: ""
            DailyForecast(
                date = date,
                weatherCode = daily.weatherCode.getOrNull(i) ?: 0,
                maxTemp = daily.temperature2mMax.getOrNull(i) ?: 0.0,
                minTemp = daily.temperature2mMin.getOrNull(i) ?: 0.0,
                precipitationSum = daily.precipitationSum.getOrNull(i) ?: 0.0,
                snowfallSum = daily.snowfallSum.getOrNull(i) ?: 0.0,
                maxWindSpeed = daily.windSpeed10mMax.getOrNull(i) ?: 0.0,
                waveHeightMax = waveByDate?.get(date)
            )
        }

        return WeatherForecast(
            location = location.copy(hasSeaAccess = hasSeaAccess),
            dailyForecasts = dailyForecasts
        )
    }

    /**
     * Best-effort fetch of daily max wave heights keyed by date. Returns null when the Marine API
     * is unreachable, so a marine outage never fails the primary forecast.
     */
    private suspend fun fetchWaveHeightsByDate(location: Location): Map<String, Double?>? {
        return try {
            val marine = marineApi.getMarine(
                latitude = location.latitude,
                longitude = location.longitude
            )
            val marineDaily = marine.daily ?: return null
            marineDaily.time.indices.associateBy({ marineDaily.time[it] }) { i ->
                marineDaily.waveHeightMax.getOrNull(i)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun DailyForecast.toEntity(locationId: Long) = DailyForecastEntity(
        locationId = locationId,
        date = date,
        maxTemp = maxTemp,
        minTemp = minTemp,
        weatherCode = weatherCode,
        precipitationSum = precipitationSum,
        maxWindSpeed = maxWindSpeed,
        snowfallSum = snowfallSum,
        waveHeightMax = waveHeightMax
    )

    private companion object {
        const val MAX_CACHED_LOCATIONS = 20
    }

    /**
     * Maps Nominatim reverse results into our [Location] model.
     * Uses a negative synthetic id derived from place_id so it cannot collide with
     * Open-Meteo / GeoNames positive IDs (same convention as FeaturedCities).
     */
    private fun NominatimResponse.toLocation(fallbackLat: Double, fallbackLng: Double): Location {
        val address = address
        val placeName = listOfNotNull(
            address?.city,
            address?.town,
            address?.village,
            address?.municipality,
            name?.takeIf { it.isNotBlank() },
            address?.county
        ).firstOrNull()
            ?: displayName?.substringBefore(',')?.trim()
            ?: "Dropped pin"

        val lat = lat.toDoubleOrNull() ?: fallbackLat
        val lng = lon.toDoubleOrNull() ?: fallbackLng
        // Offset into a range far from FeaturedCities (−1…−14) and Open-Meteo positives.
        val syntheticId = -(1_000_000L + abs(placeId))

        return Location(
            id = syntheticId,
            name = placeName,
            latitude = lat,
            longitude = lng,
            country = address?.country,
            admin1 = address?.state
        )
    }

    private fun Exception.toAppError(): AppError {
        return when (this) {
            is IOException -> AppError.NetworkError.NoConnectivity
            is HttpException -> {
                when (code()) {
                    404 -> AppError.ApiError.NotFound
                    429 -> AppError.ApiError.RateLimitExceeded
                    408 -> AppError.NetworkError.Timeout
                    else -> AppError.NetworkError.ServerError(code())
                }
            }
            else -> AppError.Unknown(this)
        }
    }
}
