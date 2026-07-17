package com.example.weatherrecommender.data.repository

import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.mapper.toDomain
import com.example.weatherrecommender.data.mapper.toEntity
import com.example.weatherrecommender.data.remote.ForecastApi
import com.example.weatherrecommender.data.remote.GeocodingApi
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.AppResult
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * Concrete implementation of the [WeatherRepository].
 *
 * Coordinates data fetching between the [GeocodingApi], [ForecastApi], and local [WeatherDao].
 * Implements the Offline-First architecture by storing responses in Room as the Single Source of Truth (SSOT).
 */
class WeatherRepositoryImpl @Inject constructor(
    private val geocodingApi: GeocodingApi,
    private val forecastApi: ForecastApi,
    private val weatherDao: WeatherDao
) : WeatherRepository {

    override suspend fun searchCity(query: String): AppResult<List<Location>> {
        return try {
            val response = geocodingApi.searchCity(query)
            val locations = response.results?.map { dto ->
                Location(
                    id = dto.id.toLong(),
                    name = dto.name,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    country = dto.country,
                    admin1 = dto.admin1
                )
            } ?: emptyList()
            Result.Success(locations)
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    override fun getForecastFlow(location: Location): Flow<WeatherForecast?> {
        return weatherDao.getDailyForecastsFlow(location.id).map { entities ->
            if (entities.isEmpty()) null
            else WeatherForecast(location, entities.map { it.toDomain() })
        }
    }

    override suspend fun refreshForecast(location: Location): AppResult<Unit> {
        return try {
            val response = forecastApi.getForecast(
                latitude = location.latitude,
                longitude = location.longitude
            )
            val dailyDto = response.daily
            val dailyForecasts = mutableListOf<DailyForecastEntity>()
            
            val size = dailyDto.time.size
            for (i in 0 until size) {
                dailyForecasts.add(
                    DailyForecastEntity(
                        locationId = location.id,
                        date = dailyDto.time.getOrNull(i) ?: "",
                        weatherCode = dailyDto.weatherCode.getOrNull(i) ?: 0,
                        maxTemp = dailyDto.temperature2mMax.getOrNull(i) ?: 0.0,
                        minTemp = dailyDto.temperature2mMin.getOrNull(i) ?: 0.0,
                        precipitationSum = dailyDto.precipitationSum.getOrNull(i) ?: 0.0,
                        snowfallSum = dailyDto.snowfallSum.getOrNull(i) ?: 0.0,
                        maxWindSpeed = dailyDto.windSpeed10mMax.getOrNull(i) ?: 0.0
                    )
                )
            }
            
            // Save to Room as SSOT
            weatherDao.insertLocationWithForecast(
                location = location.toEntity(),
                forecasts = dailyForecasts
            )
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
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
