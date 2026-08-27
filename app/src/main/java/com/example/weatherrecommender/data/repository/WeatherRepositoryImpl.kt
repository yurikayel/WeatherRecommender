package com.example.weatherrecommender.data.repository

import com.example.weatherrecommender.data.image.PlaceImagePrefetcher
import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.local.entity.LocationEntity
import com.example.weatherrecommender.data.mapper.toDomain
import com.example.weatherrecommender.data.mapper.toEntity
import com.example.weatherrecommender.data.remote.ForecastApi
import com.example.weatherrecommender.data.remote.GeocodingApi
import com.example.weatherrecommender.data.remote.MarineApi
import com.example.weatherrecommender.data.remote.NominatimApi
import com.example.weatherrecommender.data.remote.WikipediaPlaceImageResolver
import com.example.weatherrecommender.data.remote.dto.NominatimResponse
import com.example.weatherrecommender.data.remote.dto.DailyForecastDto
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.AppResult
import com.example.weatherrecommender.domain.model.CachePolicy
import com.example.weatherrecommender.domain.model.CountryPrefetchResult
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.CountryCityCatalog
import com.example.weatherrecommender.domain.usecase.HubCities
import com.example.weatherrecommender.domain.usecase.NearbyCities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * Wikipedia place images are resolved after weather persists so chips paint without waiting on
 * Wikimedia; a follow-up [WeatherDao.updatePlaceImage] fills the hero via the Room flow.
 */
@Suppress("TooManyFunctions")
class WeatherRepositoryImpl @Inject constructor(
    private val geocodingApi: GeocodingApi,
    private val forecastApi: ForecastApi,
    private val marineApi: MarineApi,
    private val nominatimApi: NominatimApi,
    private val placeImageResolver: WikipediaPlaceImageResolver,
    private val placeImagePrefetcher: PlaceImagePrefetcher,
    private val weatherDao: WeatherDao,
    private val countryCityCatalog: CountryCityCatalog
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
                    featureCode = dto.featureCode,
                    countryCode = dto.countryCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
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
        if (cached.id != nominatim.id) {
            return cached.copy(countryCode = cached.countryCode ?: nominatim.countryCode)
        }
        val matched = matchForwardGeocode(nominatim)
        return matched?.copy(countryCode = matched.countryCode ?: nominatim.countryCode) ?: nominatim
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

    /**
     * Skips Open-Meteo when weather is fresh unless [force]; always retries place images when
     * [CachePolicy.shouldFetchPlaceImage] says so (so a grey hero is not stuck for 6h).
     */
    override suspend fun refreshForecast(location: Location, force: Boolean): AppResult<Unit> {
        return try {
            val canonical = resolveCanonicalLocation(location)
            var existing = weatherDao.getLocation(canonical.id)
            val existingDays = weatherDao.getDailyForecasts(canonical.id)
            val now = System.currentTimeMillis()
            if (force || !isWeatherFresh(existing, existingDays, now)) {
                val forecast = fetchRemoteForecast(canonical, existing)
                persistRefreshedForecast(forecast, existing, now)
                evictStaleLocationsIfNeeded()
                existing = weatherDao.getLocation(canonical.id)
            } else {
                backfillCountryCode(canonical.id, canonical.countryCode, existing?.countryCode)
            }
            refreshPlaceImageIfNeeded(canonical, existing, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.toAppError())
        }
    }

    /** Writes the refreshed location + days, keeping viewed/metadata stamps when present. */
    private suspend fun persistRefreshedForecast(
        forecast: WeatherForecast,
        existing: LocationEntity?,
        now: Long
    ) {
        // Keep placeMetadataUpdatedAt from Room; Wikipedia confirmation is
        // [refreshPlaceImageIfNeeded]. Do not stamp [now] here or a miss looks "fresh".
        weatherDao.insertLocationWithForecast(
            location = forecast.location.copy(
                countryCode = forecast.location.countryCode ?: existing?.countryCode,
                imageUrl = forecast.location.imageUrl ?: existing?.imageUrl
            ).toEntity(
                lastUpdated = now,
                lastViewedAt = existing?.lastViewedAt ?: 0L,
                placeMetadataUpdatedAt = existing?.placeMetadataUpdatedAt ?: 0L
            ),
            forecasts = forecast.dailyForecasts.map { it.toEntity(forecast.location.id) }
        )
    }

    /**
     * Fetches Wikipedia when hit/miss TTL requires it, then patches Room so the hero flow updates.
     * A miss still stamps [LocationEntity.placeMetadataUpdatedAt] for [CachePolicy.PLACE_METADATA_MISS_TTL_MS].
     * On fetch failure, keeps any prior URL so a transient error does not wipe a good photo.
     */
    private suspend fun refreshPlaceImageIfNeeded(
        location: Location,
        existing: LocationEntity?,
        now: Long
    ) {
        val cachedUrl = location.imageUrl ?: existing?.imageUrl
        val metadataAt = existing?.placeMetadataUpdatedAt ?: 0L
        if (!CachePolicy.shouldFetchPlaceImage(cachedUrl, metadataAt, now)) {
            placeImagePrefetcher.prefetch(cachedUrl)
            return
        }
        val resolved = placeImageResolver.resolve(location.name, location.country)
        val finalUrl = resolved ?: cachedUrl
        weatherDao.updatePlaceImage(
            locationId = location.id,
            imageUrl = finalUrl,
            timestamp = now
        )
        placeImagePrefetcher.prefetch(finalUrl)
    }

    /** Best-effort TTL refresh of nearby hubs, staggered to stay under Open-Meteo rate limits. */
    override suspend fun prefetchNearbyCities(origin: Location) {
        val neighbors = NearbyCities.select(origin, HubCities.all)
        neighbors.forEachIndexed { index, nearby ->
            if (index > 0) delay(PREFETCH_STAGGER_MS)
            try {
                refreshForecast(nearby, force = false)
                val row = weatherDao.getLocation(nearby.id)
                    ?: weatherDao.findLocationsNear(
                        nearby.latitude,
                        nearby.longitude,
                        LocationHistoryDeduper.PROXIMITY_DEGREES
                    ).firstOrNull()
                placeImagePrefetcher.prefetch(row?.imageUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort warm of the map neighborhood.
            }
        }
    }

    /**
     * Warms up to [limit] not-yet-cached catalog cities for [countryCode], capitals first.
     * Staggers Open-Meteo calls so country-scale warming stays polite.
     */
    override suspend fun prefetchCountryCities(countryCode: String, limit: Int): CountryPrefetchResult {
        val code = countryCode.trim().uppercase()
        val pending = if (code.isEmpty() || limit <= 0) {
            emptyList()
        } else {
            countryCityCatalog.citiesFor(code).filter { city -> !isCountryCityWarmed(city) }
        }
        if (pending.isEmpty()) return CountryPrefetchResult(0, 0)
        var warmed = 0
        pending.take(limit).forEachIndexed { index, city ->
            if (index > 0) delay(COUNTRY_WARM_STAGGER_MS)
            if (warmCountryCity(city)) warmed += 1
        }
        return CountryPrefetchResult(warmed, (pending.size - warmed).coerceAtLeast(0))
    }

    /** Best-effort refresh + Coil prefetch for one catalog city. */
    private suspend fun warmCountryCity(city: Location): Boolean {
        return try {
            val ok = refreshForecast(city, force = false) is Result.Success
            if (ok) {
                val row = weatherDao.getLocation(city.id) ?: findDuplicateLocation(city)
                placeImagePrefetcher.prefetch(row?.imageUrl)
            }
            ok
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when Room already has this catalog city (by seed id or proximity/name) with a
     * still-fresh forecast. Image-only stubs are not warmed — weather is the refetchable layer.
     */
    private suspend fun isCountryCityWarmed(city: Location): Boolean {
        val existing = weatherDao.getLocation(city.id) ?: findDuplicateLocation(city)
        if (existing == null) return false
        val days = weatherDao.getDailyForecasts(existing.id)
        return isWeatherFresh(existing, days, System.currentTimeMillis())
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
        weatherDao.getLeastRecentlyViewedIds(remaining).forEach { locationId ->
            weatherDao.deleteLocationWithForecasts(locationId)
        }
    }

    /**
     * Fetches forecast+marine+Wikipedia without writing Room — used by Top Picks.
     * Weather and image resolve in parallel so postcard cards still get a photo.
     */
    override suspend fun getForecastRemote(location: Location): AppResult<WeatherForecast> {
        return try {
            coroutineScope {
                val weatherDeferred = async {
                    fetchRemoteForecast(location, existing = null)
                }
                val imageDeferred = async {
                    placeImageResolver.resolve(location.name, location.country)
                }
                val weather = weatherDeferred.await()
                val imageUrl = imageDeferred.await()
                Result.Success(
                    weather.copy(location = weather.location.copy(imageUrl = imageUrl))
                )
            }
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
            backfillCountryCode(location.id, location.countryCode, existingById.countryCode)
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
     * Rekeys the Room row so daily forecasts, image URL, ISO, and weather TTL survive the swap.
     */
    private suspend fun mergeViewedLocation(
        incoming: Location,
        existing: LocationEntity,
        now: Long
    ) {
        val preferIncomingId = incoming.id > 0L && existing.id < 0L
        if (preferIncomingId) {
            val merged = incoming.copy(
                imageUrl = incoming.imageUrl ?: existing.imageUrl,
                countryCode = incoming.countryCode ?: existing.countryCode
            )
            weatherDao.rekeyLocation(
                oldId = existing.id,
                newLocation = merged.toEntity(
                    lastUpdated = existing.lastUpdated,
                    lastViewedAt = now,
                    placeMetadataUpdatedAt = existing.placeMetadataUpdatedAt
                )
            )
        } else {
            weatherDao.updateLastViewedAt(existing.id, now)
            backfillCountryCode(existing.id, incoming.countryCode, existing.countryCode)
        }
    }

    /**
     * Stores [incoming] ISO on [locationId] when Room still has none.
     * Nearby/Featured seeds omit countryCode; a later search or GPS hit should not wait for a 6h persist.
     */
    private suspend fun backfillCountryCode(
        locationId: Long,
        incoming: String?,
        existing: String?
    ) {
        if (!existing.isNullOrBlank()) return
        val code = incoming?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return
        weatherDao.updateCountryCode(locationId, code)
    }

    /**
     * Fetches forecast and marine in parallel and assembles the domain model.
     * Keeps any existing [Location.imageUrl]; Wikipedia is applied later via
     * [refreshPlaceImageIfNeeded] so weather paint is not blocked.
     */
    private suspend fun fetchRemoteForecast(
        location: Location,
        existing: LocationEntity?
    ): WeatherForecast = coroutineScope {
        val forecastDeferred = async {
            forecastApi.getForecast(
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
        val waveDeferred = async { fetchWaveHeightsByDate(location) }
        val forecastResponse = forecastDeferred.await()
        val waveByDate = waveDeferred.await()
        val hasSeaAccess = waveByDate?.values?.any { it != null } ?: location.hasSeaAccess
        WeatherForecast(
            location = location.copy(
                hasSeaAccess = hasSeaAccess,
                imageUrl = location.imageUrl ?: existing?.imageUrl
            ),
            dailyForecasts = mapDailyForecasts(forecastResponse.daily, waveByDate)
        )
    }

    /** Maps Open-Meteo daily arrays (plus optional marine waves) into domain days. */
    private fun mapDailyForecasts(
        daily: DailyForecastDto,
        waveByDate: Map<String, Double?>?
    ): List<DailyForecast> {
        return daily.time.indices.map { i ->
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
        const val MAX_CACHED_LOCATIONS = 100
        const val DEDUPE_FETCH_MULTIPLIER = 3
        const val PREFETCH_STAGGER_MS = 150L
        const val COUNTRY_WARM_STAGGER_MS = 2_000L
    }

    /**
     * Maps Nominatim reverse results into our [Location] model.
     * Uses a negative synthetic id derived from place_id so it cannot collide with
     * Open-Meteo / GeoNames positive IDs (same convention as [HubCities]).
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
            admin1 = address?.state,
            countryCode = address?.countryCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        )
    }

    /** Maps transport failures onto [AppError], rethrowing cancellation so jobs stay cooperative.
     *
     * Host/connect failures are [AppError.NetworkError.NoConnectivity]; other [IOException]s
     * (limiter interrupts, broken streams) are [AppError.NetworkError.Unknown] so the UI
     * does not claim the device is offline.
     */
    private fun Exception.toAppError(): AppError {
        // catch (Exception) would otherwise turn Job cancellation into a UI error.
        if (this is CancellationException) throw this
        return when (this) {
            is java.net.SocketTimeoutException -> AppError.NetworkError.Timeout
            is javax.net.ssl.SSLException -> AppError.NetworkError.Unknown(this)
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.NoRouteToHostException -> AppError.NetworkError.NoConnectivity
            is IOException -> AppError.NetworkError.Unknown(this)
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
