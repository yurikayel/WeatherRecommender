package com.example.weatherrecommender.data.repository

import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.local.entity.LocationEntity
import com.example.weatherrecommender.data.mapper.toDomain
import com.example.weatherrecommender.data.mapper.toEntity
import com.example.weatherrecommender.data.remote.ForecastApi
import com.example.weatherrecommender.data.remote.GeocodingApi
import com.example.weatherrecommender.data.remote.MarineApi
import com.example.weatherrecommender.data.remote.NominatimApi
import com.example.weatherrecommender.data.remote.WikipediaApi
import com.example.weatherrecommender.data.remote.dto.NominatimResponse
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.AppResult
import com.example.weatherrecommender.domain.model.CachePolicy
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.MajorCities
import com.example.weatherrecommender.domain.usecase.NearbyCities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs

/**
 * Concrete implementation of the [WeatherRepository].
 *
 * Coordinates data fetching between the [GeocodingApi], [ForecastApi], [MarineApi],
 * [NominatimApi], and local [WeatherDao]. Implements the Offline-First architecture by
 * storing responses in Room as the Single Source of Truth (SSOT).
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
    private val wikipediaApi: WikipediaApi,
    private val weatherDao: WeatherDao
) : WeatherRepository {

    /** Geocodes [query] via Open-Meteo; empty results become an empty list, not an error. */
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

    /** Reverse-geocodes a map tap, then prefers a Room or Open-Meteo GeoNames id. */
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): AppResult<Location> {
        return try {
            val nominatim = nominatimApi.reverseGeocode(latitude, longitude)
                .toLocation(fallbackLat = latitude, fallbackLng = longitude)
            Result.Success(stabilizeReverseGeocode(nominatim))
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    /**
     * Reuses a cached city under another id, else the nearest Open-Meteo search hit,
     * else the Nominatim synthetic id.
     */
    private suspend fun stabilizeReverseGeocode(nominatim: Location): Location {
        val cached = resolveCanonicalLocation(nominatim)
        if (cached.id != nominatim.id) return cached
        return matchForwardGeocode(nominatim) ?: nominatim
    }

    /** Nearest Open-Meteo hit within [LocationHistoryDeduper.PROXIMITY_DEGREES], or null. */
    private suspend fun matchForwardGeocode(nominatim: Location): Location? {
        val hits = when (val result = searchCity(nominatim.name)) {
            is Result.Success -> result.data
            is Result.Error -> return null
        }
        return LocationHistoryDeduper.nearestWithinProximity(
            latitude = nominatim.latitude,
            longitude = nominatim.longitude,
            candidates = hits,
            latOf = { it.latitude },
            lngOf = { it.longitude }
        )
    }

    /**
     * Room row for [location] or a proximity/name duplicate. Prefers a stable positive
     * (GeoNames) id over a synthetic Nominatim negative.
     */
    private suspend fun resolveCanonicalLocation(location: Location): Location {
        val duplicate = if (weatherDao.getLocation(location.id) != null) {
            null
        } else {
            findDuplicateLocation(location)
        }
        val preferIncoming = duplicate != null && location.id > 0L && duplicate.id < 0L
        return when {
            duplicate == null || preferIncoming -> location
            else -> location.copy(id = duplicate.id)
        }
    }

    /** Room SSOT: emits null until daily rows exist for the canonical [location] id. */
    override fun getForecastFlow(location: Location): Flow<WeatherForecast?> {
        return flow {
            val canonical = resolveCanonicalLocation(location)
            emitAll(forecastFlowForId(canonical))
        }
    }

    /** Combines location + daily rows for a resolved Room id. */
    private fun forecastFlowForId(location: Location): Flow<WeatherForecast?> {
        return combine(
            weatherDao.getLocationFlow(location.id),
            weatherDao.getDailyForecastsFlow(location.id)
        ) { locationEntity, forecastEntities ->
            if (forecastEntities.isEmpty()) {
                null
            } else {
                val resolvedLocation = locationEntity?.toDomain() ?: location
                WeatherForecast(resolvedLocation, forecastEntities.map { it.toDomain() })
            }
        }
    }

    /** True when Room already has days and [CachePolicy.WEATHER_TTL_MS] has not elapsed. */
    override suspend fun hasFreshForecast(location: Location): Boolean {
        val canonical = resolveCanonicalLocation(location)
        val existing = weatherDao.getLocation(canonical.id)
        val existingDays = weatherDao.getDailyForecasts(canonical.id)
        return isWeatherFresh(existing, existingDays, System.currentTimeMillis())
    }

    /** Skips Open-Meteo when fresh unless [force]; otherwise fetches, persists, and evicts overflow. */
    override suspend fun refreshForecast(location: Location, force: Boolean): AppResult<Unit> {
        return try {
            val canonical = resolveCanonicalLocation(location)
            val existing = weatherDao.getLocation(canonical.id)
            val existingDays = weatherDao.getDailyForecasts(canonical.id)
            val now = System.currentTimeMillis()
            if (!force && isWeatherFresh(existing, existingDays, now)) {
                return Result.Success(Unit)
            }

            val forecast = fetchRemoteForecast(canonical, existing, now)
            persistRefreshedForecast(forecast, existing, now)
            evictStaleLocationsIfNeeded()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    /** Writes the refreshed location + days, keeping [LocationEntity.lastViewedAt] when present. */
    private suspend fun persistRefreshedForecast(
        forecast: WeatherForecast,
        existing: LocationEntity?,
        now: Long
    ) {
        val reusedMetadata = existing
            ?.takeIf { it.imageUrl == forecast.location.imageUrl && it.placeMetadataUpdatedAt != 0L }
            ?.placeMetadataUpdatedAt
        weatherDao.insertLocationWithForecast(
            location = forecast.location.toEntity(
                lastViewedAt = existing?.lastViewedAt ?: 0L,
                placeMetadataUpdatedAt = reusedMetadata ?: now
            ),
            forecasts = forecast.dailyForecasts.map { it.toEntity(forecast.location.id) }
        )
    }

    /** Best-effort TTL refresh of nearby hubs, staggered to stay under Open-Meteo rate limits. */
    override suspend fun prefetchNearbyCities(origin: Location) {
        val neighbors = NearbyCities.select(origin, MajorCities.all)
        neighbors.forEachIndexed { index, nearby ->
            if (index > 0) delay(PREFETCH_STAGGER_MS)
            try {
                refreshForecast(nearby, force = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort warm of the map neighborhood.
            }
        }
    }

    /** True when a location row exists, has days, and lastUpdated is within the weather TTL. */
    private fun isWeatherFresh(
        existing: LocationEntity?,
        existingDays: List<DailyForecastEntity>,
        now: Long
    ): Boolean {
        if (existing == null || existingDays.isEmpty()) return false
        return now - existing.lastUpdated < CachePolicy.WEATHER_TTL_MS
    }

    /**
     * Keeps the Room cache bounded. Prefetched (never viewed) cities are evicted first so
     * history survives. SyncWorker only iterates stored locations, so this also bounds background sync.
     */
    private suspend fun evictStaleLocationsIfNeeded() {
        val overflow = weatherDao.getLocationCount() - MAX_CACHED_LOCATIONS
        if (overflow <= 0) return

        val unviewed = weatherDao.getUnviewedOldestIds(overflow)
        unviewed.forEach { locationId ->
            weatherDao.deleteLocationWithForecasts(locationId)
        }
        val remaining = weatherDao.getLocationCount() - MAX_CACHED_LOCATIONS
        if (remaining <= 0) return
        weatherDao.getOldestLocationIds(remaining).forEach { locationId ->
            weatherDao.deleteLocationWithForecasts(locationId)
        }
    }

    /** Fetches forecast+marine without writing Room — used by Top Picks. */
    override suspend fun getForecastRemote(location: Location): AppResult<WeatherForecast> {
        return try {
            Result.Success(fetchRemoteForecast(location, existing = null, now = System.currentTimeMillis()))
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    /** Over-fetches recent rows then collapses Nominatim/GeoNames duplicates to [limit] cities. */
    override fun observeRecentLocations(limit: Int): Flow<List<Location>> {
        // Over-fetch so proximity/name collapse can still fill [limit] unique cities.
        val fetchLimit = (limit * DEDUPE_FETCH_MULTIPLIER).coerceAtLeast(limit)
        return weatherDao.getRecentLocationsFlow(fetchLimit).map { entities ->
            LocationHistoryDeduper.collapse(entities)
                .take(limit)
                .map { it.toDomain() }
        }
    }

    /** Upserts lastViewedAt, merging a synthetic Nominatim id into an existing GeoNames row. */
    override suspend fun markLocationViewed(location: Location) {
        val now = System.currentTimeMillis()
        val existingById = weatherDao.getLocation(location.id)
        if (existingById != null) {
            weatherDao.updateLastViewedAt(location.id, now)
            return
        }

        val duplicate = findDuplicateLocation(location)
        if (duplicate != null) {
            mergeViewedLocation(incoming = location, existing = duplicate, now = now)
            return
        }

        // Insert a stub so history appears even if the subsequent forecast refresh fails.
        weatherDao.insertLocation(
            location.toEntity(lastUpdated = now, lastViewedAt = now)
        )
    }

    /**
     * Finds an already-cached row for the same city under a different id
     * (coordinate proximity first, then normalized name+country).
     */
    private suspend fun findDuplicateLocation(location: Location): LocationEntity? {
        val near = weatherDao.findLocationsNear(
            latitude = location.latitude,
            longitude = location.longitude,
            delta = LocationHistoryDeduper.PROXIMITY_DEGREES
        )
        val byProximity = near.maxByOrNull { it.lastViewedAt }
        if (byProximity != null) return byProximity

        val country = location.country?.trim().orEmpty()
        val name = location.name.trim()
        return if (name.isEmpty()) {
            null
        } else {
            weatherDao.findLocationsByNameAndCountry(name = name, country = country)
                .maxByOrNull { it.lastViewedAt }
        }
    }

    /**
     * Upserts history for a city that already exists under another id.
     * Prefers stable positive (GeoNames / Open-Meteo) ids over synthetic Nominatim negatives.
     */
    private suspend fun mergeViewedLocation(
        incoming: Location,
        existing: LocationEntity,
        now: Long
    ) {
        val preferIncomingId = incoming.id > 0L && existing.id < 0L
        if (preferIncomingId) {
            weatherDao.deleteLocationWithForecasts(existing.id)
            weatherDao.insertLocation(
                incoming.toEntity(lastUpdated = now, lastViewedAt = now)
            )
        } else {
            weatherDao.updateLastViewedAt(existing.id, now)
        }
    }

    /**
     * Fetches the forecast (and best-effort marine data) for a location and assembles the domain
     * model, resolving sea access from wave data. Does not persist anything.
     */
    private suspend fun fetchRemoteForecast(
        location: Location,
        existing: LocationEntity?,
        now: Long
    ): WeatherForecast {
        val forecastResponse = forecastApi.getForecast(
            latitude = location.latitude,
            longitude = location.longitude
        )
        val daily = forecastResponse.daily

        val waveByDate = fetchWaveHeightsByDate(location)
        val hasSeaAccess = waveByDate?.values?.any { it != null } ?: location.hasSeaAccess

        val imageUrl = resolveImageUrl(location, existing, now)

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
            location = location.copy(hasSeaAccess = hasSeaAccess, imageUrl = imageUrl),
            dailyForecasts = dailyForecasts
        )
    }

    /**
     * City pictures and names change rarely. Reuse a stored Wikipedia URL for
     * [CachePolicy.PLACE_METADATA_TTL_MS]. A missing timestamp (`0`) is stale.
     */
    private suspend fun resolveImageUrl(
        location: Location,
        existing: LocationEntity?,
        now: Long
    ): String? {
        val cachedUrl = location.imageUrl ?: existing?.imageUrl
        val metadataAt = existing?.placeMetadataUpdatedAt ?: 0L
        if (CachePolicy.isPlaceMetadataFresh(cachedUrl, metadataAt, now)) return cachedUrl
        return fetchWikipediaImageUrl(location.name) ?: cachedUrl
    }

    /** Wikipedia thumbnail URL, or null if the page has no image or the call fails. */
    private suspend fun fetchWikipediaImageUrl(cityName: String): String? {
        return try {
            val response = wikipediaApi.getPageImage(titles = cityName)
            val pages = response.query?.pages
            // Prefer the optimized thumbnail size; fallback to original if absent.
            pages?.values?.firstOrNull()?.let { page ->
                page.thumbnail?.source ?: page.original?.source
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // Best-effort fetching
        }
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Maps a domain day onto the Room forecast row keyed by [locationId]. */
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
        const val MAX_CACHED_LOCATIONS = 36
        const val DEDUPE_FETCH_MULTIPLIER = 3
        const val PREFETCH_STAGGER_MS = 150L
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

    /** Maps transport failures onto [AppError], rethrowing cancellation so jobs stay cooperative. */
    private fun Exception.toAppError(): AppError {
        // catch (Exception) would otherwise turn Job cancellation into a UI error.
        if (this is CancellationException) throw this
        return when (this) {
            is java.net.SocketTimeoutException -> AppError.NetworkError.Timeout
            is javax.net.ssl.SSLException -> AppError.NetworkError.Unknown(this)
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
