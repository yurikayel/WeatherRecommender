package com.example.weatherrecommender.data.repository

import com.example.weatherrecommender.data.image.PlaceImagePrefetcher
import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.local.entity.LocationEntity
import com.example.weatherrecommender.data.mapper.toEntity
import com.example.weatherrecommender.domain.model.CachePolicy
import com.example.weatherrecommender.data.remote.ForecastApi
import com.example.weatherrecommender.data.remote.GeocodingApi
import com.example.weatherrecommender.data.remote.MarineApi
import com.example.weatherrecommender.data.remote.NominatimApi
import com.example.weatherrecommender.data.remote.WikipediaPlaceImageResolver
import com.example.weatherrecommender.data.remote.dto.DailyForecastDto
import com.example.weatherrecommender.data.remote.dto.ForecastResponse
import com.example.weatherrecommender.data.remote.dto.GeocodingLocationDto
import com.example.weatherrecommender.data.remote.dto.GeocodingResponse
import com.example.weatherrecommender.data.remote.dto.MarineDailyDto
import com.example.weatherrecommender.data.remote.dto.MarineResponse
import com.example.weatherrecommender.data.remote.dto.NominatimAddress
import com.example.weatherrecommender.data.remote.dto.NominatimResponse
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.CountryPrefetchResult
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.usecase.CountryCityCatalog
import com.example.weatherrecommender.domain.usecase.CountryCityEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

@Suppress("LargeClass")
class WeatherRepositoryImplTest {

    private val geocodingApi: GeocodingApi = mockk()
    private val forecastApi: ForecastApi = mockk()
    private val marineApi: MarineApi = mockk()
    private val nominatimApi: NominatimApi = mockk()
    private val placeImageResolver: WikipediaPlaceImageResolver = mockk()
    private val placeImagePrefetcher: PlaceImagePrefetcher = mockk(relaxed = true)
    private val weatherDao: WeatherDao = mockk(relaxed = true)

    private lateinit var repository: WeatherRepositoryImpl

    private val location = Location(1, "London", 51.5, -0.1, "UK", "England")

    private fun forecastResponse() = ForecastResponse(
        latitude = 51.5,
        longitude = -0.1,
        timezone = "Europe/London",
        daily = DailyForecastDto(
            time = listOf("2026-07-16"),
            weatherCode = listOf(0),
            temperature2mMax = listOf(22.0),
            temperature2mMin = listOf(12.0),
            precipitationSum = listOf(0.0),
            snowfallSum = listOf(0.0),
            windSpeed10mMax = listOf(10.0)
        )
    )

    @Before
    fun setup() {
        repository = WeatherRepositoryImpl(
            geocodingApi,
            forecastApi,
            marineApi,
            nominatimApi,
            placeImageResolver,
            placeImagePrefetcher,
            weatherDao,
            CountryCityCatalog(emptyList())
        )
        // Default: inland (no marine data) unless a test overrides it.
        coEvery { marineApi.getMarine(any(), any()) } returns MarineResponse(51.5, -0.1, null)
        coEvery { placeImageResolver.resolve(any(), any()) } returns null
    }

    @Test
    fun `searchCity returns mapped locations on success`() = runTest {
        coEvery { geocodingApi.searchCity("Lon") } returns GeocodingResponse(
            results = listOf(
                GeocodingLocationDto(
                    id = 1, name = "London", latitude = 51.5, longitude = -0.1,
                    country = "UK", admin1 = "England", elevation = 25.0, population = 8_961_989,
                    countryCode = "gb"
                )
            )
        )

        val result = repository.searchCity("Lon")

        assertTrue(result is Result.Success)
        val locations = (result as Result.Success).data
        assertEquals(1, locations.size)
        assertEquals("London", locations.first().name)
        assertEquals(8_961_989L, locations.first().population)
        assertEquals(25.0, locations.first().elevation!!, 0.0)
        assertEquals("GB", locations.first().countryCode)
    }

    @Test
    fun `searchCity returns empty list when API returns null results`() = runTest {
        coEvery { geocodingApi.searchCity("xyz") } returns GeocodingResponse(results = null)

        val result = repository.searchCity("xyz")

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun `searchCity maps UnknownHostException to NoConnectivity`() = runTest {
        coEvery { geocodingApi.searchCity(any()) } throws UnknownHostException("offline")

        val result = repository.searchCity("Lon")

        assertTrue(result is Result.Error)
        assertEquals(AppError.NetworkError.NoConnectivity, (result as Result.Error).error)
    }

    @Test
    fun `searchCity maps generic IOException to NetworkError Unknown`() = runTest {
        coEvery { geocodingApi.searchCity(any()) } throws IOException("broken stream")

        val result = repository.searchCity("Lon")

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.NetworkError.Unknown)
    }

    @Test
    fun `searchCity maps ConnectException to NoConnectivity`() = runTest {
        coEvery { geocodingApi.searchCity(any()) } throws java.net.ConnectException("failed to connect")

        val result = repository.searchCity("Lon")

        assertTrue(result is Result.Error)
        assertEquals(AppError.NetworkError.NoConnectivity, (result as Result.Error).error)
    }

    @Test
    fun `searchCity maps NoRouteToHostException to NoConnectivity`() = runTest {
        coEvery { geocodingApi.searchCity(any()) } throws java.net.NoRouteToHostException("no route")

        val result = repository.searchCity("Lon")

        assertTrue(result is Result.Error)
        assertEquals(AppError.NetworkError.NoConnectivity, (result as Result.Error).error)
    }

    @Test
    fun `searchCity maps SocketTimeoutException to Timeout`() = runTest {
        coEvery { geocodingApi.searchCity(any()) } throws java.net.SocketTimeoutException("read timed out")

        val result = repository.searchCity("Lon")

        assertTrue(result is Result.Error)
        assertEquals(AppError.NetworkError.Timeout, (result as Result.Error).error)
    }

    @Test
    fun `searchCity rethrows CancellationException instead of mapping it to an error`() = runTest {
        coEvery { geocodingApi.searchCity(any()) } throws kotlinx.coroutines.CancellationException("cancelled")

        try {
            repository.searchCity("Lon")
            org.junit.Assert.fail("expected CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun `searchCity maps 429 to RateLimitExceeded`() = runTest {
        coEvery { geocodingApi.searchCity(any()) } throws HttpException(
            Response.error<String>(429, "".toResponseBody())
        )

        val result = repository.searchCity("Lon")

        assertTrue(result is Result.Error)
        assertEquals(AppError.ApiError.RateLimitExceeded, (result as Result.Error).error)
    }

    @Test
    fun `reverseGeocode maps Nominatim city to Location`() = runTest {
        coEvery { nominatimApi.reverseGeocode(51.5, -0.1) } returns NominatimResponse(
            placeId = 42,
            lat = "51.5074",
            lon = "-0.1278",
            displayName = "Westminster, London, England, UK",
            name = "Westminster",
            address = NominatimAddress(
                city = "London",
                state = "England",
                country = "United Kingdom",
                countryCode = "gb"
            )
        )
        coEvery { geocodingApi.searchCity("London") } returns GeocodingResponse(results = null)

        val result = repository.reverseGeocode(51.5, -0.1)

        assertTrue(result is Result.Success)
        val mapped = (result as Result.Success).data
        assertEquals("London", mapped.name)
        assertEquals("England", mapped.admin1)
        assertEquals("United Kingdom", mapped.country)
        assertEquals("GB", mapped.countryCode)
        assertEquals(51.5074, mapped.latitude, 0.0001)
        assertEquals(-0.1278, mapped.longitude, 0.0001)
        assertTrue(mapped.id < -1_000_000L)
    }

    @Test
    fun `reverseGeocode reuses a nearby Open-Meteo GeoNames id`() = runTest {
        coEvery { nominatimApi.reverseGeocode(51.5, -0.1) } returns NominatimResponse(
            placeId = 42,
            lat = "51.5074",
            lon = "-0.1278",
            displayName = "London, England, UK",
            name = "London",
            address = NominatimAddress(
                city = "London",
                state = "England",
                country = "United Kingdom",
                countryCode = "gb"
            )
        )
        coEvery { geocodingApi.searchCity("London") } returns GeocodingResponse(
            results = listOf(
                GeocodingLocationDto(
                    id = 2643743, name = "London", latitude = 51.508, longitude = -0.128,
                    country = "United Kingdom", admin1 = "England"
                )
            )
        )

        val result = repository.reverseGeocode(51.5, -0.1)

        assertTrue(result is Result.Success)
        val mapped = (result as Result.Success).data
        assertEquals(2643743L, mapped.id)
        assertEquals("GB", mapped.countryCode)
    }

    @Test
    fun `reverseGeocode maps UnknownHostException to NoConnectivity`() = runTest {
        coEvery { nominatimApi.reverseGeocode(any(), any()) } throws UnknownHostException("offline")

        val result = repository.reverseGeocode(1.0, 2.0)

        assertTrue(result is Result.Error)
        assertEquals(AppError.NetworkError.NoConnectivity, (result as Result.Error).error)
    }

    @Test
    fun `getForecastFlow returns null when dao is empty`() = runTest {
        every { weatherDao.getLocationFlow(location.id) } returns flowOf(null)
        every { weatherDao.getDailyForecastsFlow(location.id) } returns flowOf(emptyList())

        val forecast = repository.getForecastFlow(location).first()

        assertNull(forecast)
    }

    @Test
    fun `getForecastFlow follows a rekey onto a new location id`() = runTest {
        val oldId = -1_000_042L
        val newId = 2643743L
        val nominatim = location.copy(id = oldId)
        val oldLoc = MutableStateFlow<LocationEntity?>(
            nominatim.toEntity(lastUpdated = 50L, lastViewedAt = 1L)
        )
        val newLoc = MutableStateFlow<LocationEntity?>(null)
        val oldDays = MutableStateFlow(
            listOf(
                DailyForecastEntity(
                    locationId = oldId,
                    date = "2026-07-16",
                    maxTemp = 22.0,
                    minTemp = 12.0,
                    weatherCode = 0,
                    precipitationSum = 0.0,
                    maxWindSpeed = 10.0,
                    snowfallSum = 0.0
                )
            )
        )
        val newDays = MutableStateFlow<List<DailyForecastEntity>>(emptyList())

        coEvery { weatherDao.getLocation(any()) } answers {
            when (invocation.args[0] as Long) {
                oldId -> oldLoc.value
                newId -> newLoc.value
                else -> null
            }
        }
        every { weatherDao.getLocationFlow(any()) } answers {
            when (invocation.args[0] as Long) {
                oldId -> oldLoc
                newId -> newLoc
                else -> MutableStateFlow(null)
            }
        }
        every { weatherDao.getDailyForecastsFlow(any()) } answers {
            when (invocation.args[0] as Long) {
                oldId -> oldDays
                newId -> newDays
                else -> MutableStateFlow(emptyList())
            }
        }
        coEvery { weatherDao.findLocationsNear(any(), any(), any()) } answers {
            listOfNotNull(newLoc.value)
        }

        val emissions = mutableListOf<WeatherForecast?>()
        val job = launch {
            repository.getForecastFlow(nominatim).collect { emissions.add(it) }
        }
        runCurrent()

        assertEquals(oldId, emissions.last()?.location?.id)

        val geoEntity = location.copy(id = newId).toEntity(lastUpdated = 50L, lastViewedAt = 2L)
        newLoc.value = geoEntity
        newDays.value = oldDays.value.map { it.copy(locationId = newId) }
        oldDays.value = emptyList()
        oldLoc.value = null
        runCurrent()

        assertEquals(newId, emissions.last()?.location?.id)
        assertEquals(1, emissions.last()?.dailyForecasts?.size)
        job.cancel()
    }

    @Test
    fun `refreshForecast persists forecast to dao`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify {
            weatherDao.insertLocationWithForecast(
                match { it.id == location.id },
                match { it.size == 1 && it.first().weatherCode == 0 }
            )
        }
    }

    @Test
    fun `refreshForecast evicts oldest locations when cache exceeds cap`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()
        coEvery { weatherDao.getLocationCount() } returnsMany listOf(101, 100)
        coEvery { weatherDao.getUnviewedOldestIds(1) } returns listOf(99L)

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify { weatherDao.deleteLocationWithForecasts(99L) }
    }

    @Test
    fun `refreshForecast keeps existing countryCode when incoming location has none`() = runTest {
        val cached = location.toEntity(lastUpdated = 1L, lastViewedAt = 5L).copy(countryCode = "GB")
        coEvery { weatherDao.getLocation(location.id) } returns cached
        coEvery { weatherDao.getDailyForecasts(location.id) } returns emptyList()
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify {
            weatherDao.insertLocationWithForecast(
                match { it.id == location.id && it.countryCode == "GB" && it.lastViewedAt == 5L },
                any()
            )
        }
    }

    @Test
    fun `refreshForecast evicts least recently viewed history after unviewed rows are gone`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()
        coEvery { weatherDao.getLocationCount() } returnsMany listOf(101, 101)
        coEvery { weatherDao.getUnviewedOldestIds(1) } returns emptyList()
        coEvery { weatherDao.getLeastRecentlyViewedIds(1) } returns listOf(42L)

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify { weatherDao.deleteLocationWithForecasts(42L) }
    }

    @Test
    fun `refreshForecast skips network when weather cache is within Open-Meteo TTL`() = runTest {
        val fresh = location.toEntity(
            lastUpdated = System.currentTimeMillis(),
            lastViewedAt = 1L
        )
        coEvery { weatherDao.getLocation(location.id) } returns fresh
        coEvery { weatherDao.getDailyForecasts(location.id) } returns listOf(
            DailyForecastEntity(
                locationId = 1,
                date = "2026-07-16",
                maxTemp = 22.0,
                minTemp = 12.0,
                weatherCode = 0,
                precipitationSum = 0.0,
                maxWindSpeed = 10.0,
                snowfallSum = 0.0
            )
        )

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { forecastApi.getForecast(any(), any()) }
        coVerify(exactly = 0) { weatherDao.insertLocationWithForecast(any(), any()) }
        coVerify(exactly = 0) { weatherDao.updateCountryCode(any(), any()) }
    }

    @Test
    fun `refreshForecast backfills countryCode when weather TTL skips persist`() = runTest {
        val fresh = location.toEntity(
            lastUpdated = System.currentTimeMillis(),
            lastViewedAt = 1L
        ).copy(countryCode = null)
        coEvery { weatherDao.getLocation(location.id) } returns fresh
        coEvery { weatherDao.getDailyForecasts(location.id) } returns listOf(
            DailyForecastEntity(
                locationId = 1,
                date = "2026-07-16",
                maxTemp = 22.0,
                minTemp = 12.0,
                weatherCode = 0,
                precipitationSum = 0.0,
                maxWindSpeed = 10.0,
                snowfallSum = 0.0
            )
        )

        val result = repository.refreshForecast(location.copy(countryCode = "gb"))

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { weatherDao.insertLocationWithForecast(any(), any()) }
        coVerify { weatherDao.updateCountryCode(location.id, "GB") }
    }

    @Test
    fun `hasFreshForecast is true when Room weather is within TTL`() = runTest {
        coEvery { weatherDao.getLocation(location.id) } returns location.toEntity(
            lastUpdated = System.currentTimeMillis(),
            lastViewedAt = 1L
        )
        coEvery { weatherDao.getDailyForecasts(location.id) } returns listOf(
            DailyForecastEntity(
                locationId = 1,
                date = "2026-07-16",
                maxTemp = 22.0,
                minTemp = 12.0,
                weatherCode = 0,
                precipitationSum = 0.0,
                maxWindSpeed = 10.0,
                snowfallSum = 0.0
            )
        )

        assertTrue(repository.hasFreshForecast(location))
    }

    @Test
    fun `hasFreshForecast is false when Room is empty or stale`() = runTest {
        coEvery { weatherDao.getLocation(location.id) } returns null
        coEvery { weatherDao.getDailyForecasts(location.id) } returns emptyList()
        assertTrue(!repository.hasFreshForecast(location))

        coEvery { weatherDao.getLocation(location.id) } returns location.toEntity(
            lastUpdated = System.currentTimeMillis() - CachePolicy.WEATHER_TTL_MS - 1
        )
        coEvery { weatherDao.getDailyForecasts(location.id) } returns listOf(
            DailyForecastEntity(
                locationId = 1,
                date = "2026-07-16",
                maxTemp = 22.0,
                minTemp = 12.0,
                weatherCode = 0,
                precipitationSum = 0.0,
                maxWindSpeed = 10.0,
                snowfallSum = 0.0
            )
        )
        assertTrue(!repository.hasFreshForecast(location))
    }

    @Test
    fun `refreshForecast does not refetch Wikipedia when place metadata is fresh`() = runTest {
        val staleWeather = location.copy(imageUrl = "https://example.com/london.jpg").toEntity(
            lastUpdated = System.currentTimeMillis() - CachePolicy.WEATHER_TTL_MS - 1,
            lastViewedAt = 1L,
            placeMetadataUpdatedAt = System.currentTimeMillis()
        )
        coEvery { weatherDao.getLocation(location.id) } returns staleWeather
        coEvery { weatherDao.getDailyForecasts(location.id) } returns emptyList()
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()

        val result = repository.refreshForecast(location.copy(imageUrl = "https://example.com/london.jpg"))

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { placeImageResolver.resolve(any(), any()) }
        verify { placeImagePrefetcher.prefetch("https://example.com/london.jpg") }
    }

    @Test
    fun `refreshForecast fetches Wikipedia when placeMetadataUpdatedAt is 0`() = runTest {
        val migrated = location.copy(imageUrl = "https://example.com/london.jpg").toEntity(
            lastUpdated = System.currentTimeMillis() - CachePolicy.WEATHER_TTL_MS - 1,
            lastViewedAt = 1L,
            placeMetadataUpdatedAt = 0L
        )
        coEvery { weatherDao.getLocation(location.id) } returns migrated
        coEvery { weatherDao.getDailyForecasts(location.id) } returns emptyList()
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()
        coEvery { placeImageResolver.resolve("London", "UK") } returns "https://example.com/london-new.jpg"

        val result = repository.refreshForecast(location.copy(imageUrl = "https://example.com/london.jpg"))

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { placeImageResolver.resolve("London", "UK") }
        coVerify {
            weatherDao.updatePlaceImage(
                location.id,
                "https://example.com/london-new.jpg",
                any()
            )
        }
    }

    @Test
    fun `refreshForecast retries Wikipedia when weather is fresh but image is missing`() = runTest {
        val freshNoImage = location.toEntity(
            lastUpdated = System.currentTimeMillis(),
            lastViewedAt = 1L,
            placeMetadataUpdatedAt = 0L
        )
        coEvery { weatherDao.getLocation(location.id) } returns freshNoImage
        coEvery { weatherDao.getDailyForecasts(location.id) } returns listOf(
            DailyForecastEntity(
                locationId = 1,
                date = "2026-07-16",
                maxTemp = 22.0,
                minTemp = 12.0,
                weatherCode = 0,
                precipitationSum = 0.0,
                maxWindSpeed = 10.0,
                snowfallSum = 0.0
            )
        )
        coEvery { placeImageResolver.resolve("London", "UK") } returns "https://example.com/london.jpg"

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { forecastApi.getForecast(any(), any()) }
        coVerify(exactly = 1) { placeImageResolver.resolve("London", "UK") }
        coVerify {
            weatherDao.updatePlaceImage(location.id, "https://example.com/london.jpg", any())
        }
    }

    @Test
    fun `refreshForecast skips Wikipedia during miss TTL`() = runTest {
        val recentMiss = location.toEntity(
            lastUpdated = System.currentTimeMillis(),
            lastViewedAt = 1L,
            placeMetadataUpdatedAt = System.currentTimeMillis()
        )
        coEvery { weatherDao.getLocation(location.id) } returns recentMiss
        coEvery { weatherDao.getDailyForecasts(location.id) } returns listOf(
            DailyForecastEntity(
                locationId = 1,
                date = "2026-07-16",
                maxTemp = 22.0,
                minTemp = 12.0,
                weatherCode = 0,
                precipitationSum = 0.0,
                maxWindSpeed = 10.0,
                snowfallSum = 0.0
            )
        )

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { placeImageResolver.resolve(any(), any()) }
        coVerify(exactly = 0) { forecastApi.getForecast(any(), any()) }
    }

    @Test
    fun `refreshForecast writes under an existing GeoNames id for a Nominatim tap`() = runTest {
        val nominatim = Location(-1_000_042, "London", 51.52, -0.12, "UK", "England")
        val geoNames = location.toEntity(lastViewedAt = 10L)
        coEvery { weatherDao.getLocation(nominatim.id) } returns null
        coEvery { weatherDao.getLocation(location.id) } returns geoNames
        coEvery {
            weatherDao.findLocationsNear(nominatim.latitude, nominatim.longitude, 0.05)
        } returns listOf(geoNames)
        coEvery { weatherDao.getDailyForecasts(location.id) } returns emptyList()
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()

        val result = repository.refreshForecast(nominatim)

        assertTrue(result is Result.Success)
        coVerify {
            weatherDao.insertLocationWithForecast(
                match { it.id == location.id },
                match { days -> days.all { it.locationId == location.id } }
            )
        }
    }

    @Test
    fun `refreshForecast marks location coastal when marine returns waves`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()
        coEvery { marineApi.getMarine(any(), any()) } returns MarineResponse(
            latitude = 51.5,
            longitude = -0.1,
            daily = MarineDailyDto(
                time = listOf("2026-07-16"),
                waveHeightMax = listOf(0.9)
            )
        )

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify {
            weatherDao.insertLocationWithForecast(
                match { it.hasSeaAccess && it.id == location.id },
                match { it.first().waveHeightMax == 0.9 }
            )
        }
    }

    @Test
    fun `refreshForecast still succeeds when marine api fails`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()
        coEvery { marineApi.getMarine(any(), any()) } throws IOException("marine down")

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
    }

    @Test
    fun `getForecastRemote returns forecast without persisting`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()

        val result = repository.getForecastRemote(location)

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.dailyForecasts.size)
        coVerify(exactly = 0) { weatherDao.insertLocationWithForecast(any(), any()) }
    }

    @Test
    fun `markLocationViewed updates timestamp when location already cached`() = runTest {
        coEvery { weatherDao.getLocation(location.id) } returns location.toEntity(lastViewedAt = 1L)

        repository.markLocationViewed(location)

        coVerify { weatherDao.updateLastViewedAt(location.id, any()) }
        coVerify(exactly = 0) { weatherDao.insertLocation(any()) }
        coVerify(exactly = 0) { weatherDao.updateCountryCode(any(), any()) }
    }

    @Test
    fun `markLocationViewed backfills countryCode on an existing row`() = runTest {
        coEvery { weatherDao.getLocation(location.id) } returns
            location.toEntity(lastViewedAt = 1L).copy(countryCode = null)

        repository.markLocationViewed(location.copy(countryCode = "gb"))

        coVerify { weatherDao.updateLastViewedAt(location.id, any()) }
        coVerify { weatherDao.updateCountryCode(location.id, "GB") }
    }

    @Test
    fun `markLocationViewed inserts stub when location is new`() = runTest {
        coEvery { weatherDao.getLocation(location.id) } returns null
        coEvery { weatherDao.findLocationsNear(any(), any(), any()) } returns emptyList()
        coEvery { weatherDao.findLocationsByNameAndCountry(any(), any()) } returns emptyList()

        repository.markLocationViewed(location)

        coVerify {
            weatherDao.insertLocation(
                match { it.id == location.id && it.lastViewedAt > 0L }
            )
        }
        coVerify(exactly = 0) { weatherDao.updateLastViewedAt(any(), any()) }
    }

    @Test
    fun `markLocationViewed merges map tap onto existing GeoNames city by proximity`() = runTest {
        val geoNames = location.toEntity(lastViewedAt = 10L)
        val nominatim = Location(-1_000_042, "London", 51.52, -0.12, "UK", "England")
        coEvery { weatherDao.getLocation(nominatim.id) } returns null
        coEvery {
            weatherDao.findLocationsNear(nominatim.latitude, nominatim.longitude, 0.05)
        } returns listOf(geoNames)

        repository.markLocationViewed(nominatim)

        coVerify { weatherDao.updateLastViewedAt(geoNames.id, any()) }
        coVerify(exactly = 0) { weatherDao.insertLocation(any()) }
    }

    @Test
    fun `markLocationViewed rekeys Nominatim stub onto GeoNames id`() = runTest {
        val nominatimStub = location.copy(id = -1_000_042).toEntity(
            lastUpdated = 50L,
            lastViewedAt = 10L,
            placeMetadataUpdatedAt = 20L
        ).copy(imageUrl = "https://example.com/london.jpg", countryCode = "GB")
        coEvery { weatherDao.getLocation(location.id) } returns null
        coEvery {
            weatherDao.findLocationsNear(location.latitude, location.longitude, 0.05)
        } returns listOf(nominatimStub)

        repository.markLocationViewed(location)

        coVerify {
            weatherDao.rekeyLocation(
                nominatimStub.id,
                match {
                    it.id == location.id &&
                        it.lastViewedAt > 0L &&
                        it.lastUpdated == 50L &&
                        it.placeMetadataUpdatedAt == 20L &&
                        it.imageUrl == "https://example.com/london.jpg" &&
                        it.countryCode == "GB"
                }
            )
        }
        coVerify(exactly = 0) { weatherDao.deleteLocationWithForecasts(any()) }
        coVerify(exactly = 0) { weatherDao.insertLocation(any()) }
    }

    @Test
    fun `observeRecentLocations collapses duplicate cities`() = runTest {
        val geoNames = location.toEntity(lastViewedAt = 200L)
        val nominatim = location.copy(id = -1_000_042, latitude = 51.52, longitude = -0.12)
            .toEntity(lastViewedAt = 100L)
        every { weatherDao.getRecentLocationsFlow(30) } returns flowOf(
            listOf(geoNames, nominatim)
        )

        val recent = repository.observeRecentLocations(10).first()

        assertEquals(1, recent.size)
        assertEquals(location.id, recent.first().id)
    }

    @Test
    fun `refreshForecast preserves existing lastViewedAt`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()
        coEvery { weatherDao.getLocation(location.id) } returns location.toEntity(lastViewedAt = 42L)

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify {
            weatherDao.insertLocationWithForecast(
                match { it.id == location.id && it.lastViewedAt == 42L },
                any()
            )
        }
    }

    @Test
    fun `observeRecentLocations maps dao entities to domain`() = runTest {
        every { weatherDao.getRecentLocationsFlow(30) } returns flowOf(
            listOf(location.toEntity(lastViewedAt = 99L))
        )

        val recent = repository.observeRecentLocations(10).first()

        assertEquals(1, recent.size)
        assertEquals("London", recent.first().name)
    }

    @Test
    fun `refreshForecast maps 404 to NotFound`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } throws HttpException(
            Response.error<String>(404, "".toResponseBody())
        )

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Error)
        assertEquals(AppError.ApiError.NotFound, (result as Result.Error).error)
    }

    @Test
    fun `prefetchCountryCities respects limit and skips already cached cities`() = runTest {
        val catalog = CountryCityCatalog(
            listOf(
                CountryCityEntry("CU", "Havana", "Havana", 23.13, -82.38, 2_000_000, isCapital = true),
                CountryCityEntry("CU", "Camagüey", "Camagüey", 21.38, -77.92, 300_000, isCapital = true),
                CountryCityEntry("CU", "Holguín", "Holguín", 20.89, -76.26, 250_000, isCapital = true)
            )
        )
        repository = WeatherRepositoryImpl(
            geocodingApi,
            forecastApi,
            marineApi,
            nominatimApi,
            placeImageResolver,
            placeImagePrefetcher,
            weatherDao,
            catalog
        )
        val havanaId = -200_000L
        val havanaCached = location.copy(
            id = havanaId,
            name = "Havana",
            imageUrl = "https://example.com/havana.jpg"
        ).toEntity(lastUpdated = System.currentTimeMillis())
        val havanaDay = DailyForecastEntity(
            locationId = havanaId,
            date = "2026-07-16",
            maxTemp = 30.0,
            minTemp = 22.0,
            weatherCode = 0,
            precipitationSum = 0.0,
            maxWindSpeed = 10.0,
            snowfallSum = 0.0
        )
        coEvery { weatherDao.getLocation(any()) } answers {
            if (invocation.args[0] as Long == havanaId) havanaCached else null
        }
        coEvery { weatherDao.findLocationsNear(any(), any(), any()) } returns emptyList()
        coEvery { weatherDao.findLocationsByNameAndCountry(any(), any()) } returns emptyList()
        coEvery { weatherDao.getDailyForecasts(any()) } answers {
            if (invocation.args[0] as Long == havanaId) listOf(havanaDay) else emptyList()
        }
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()

        val result = repository.prefetchCountryCities("cu", limit = 2)

        assertEquals(2, result.warmed)
        assertEquals(0, result.remaining)
        coVerify(exactly = 0) { forecastApi.getForecast(23.13, -82.38) }
        coVerify(exactly = 1) { forecastApi.getForecast(21.38, -77.92) }
        coVerify(exactly = 1) { forecastApi.getForecast(20.89, -76.26) }
    }

    @Test
    fun `prefetchCountryCities does not skip image-only rows without fresh weather`() = runTest {
        val catalog = CountryCityCatalog(
            listOf(
                CountryCityEntry("CU", "Havana", "Havana", 23.13, -82.38, 2_000_000, isCapital = true)
            )
        )
        repository = WeatherRepositoryImpl(
            geocodingApi,
            forecastApi,
            marineApi,
            nominatimApi,
            placeImageResolver,
            placeImagePrefetcher,
            weatherDao,
            catalog
        )
        val havanaId = -200_000L
        val imageOnly = location.copy(
            id = havanaId,
            name = "Havana",
            imageUrl = "https://example.com/havana.jpg"
        ).toEntity(lastUpdated = 1L)
        coEvery { weatherDao.getLocation(any()) } answers {
            if (invocation.args[0] as Long == havanaId) imageOnly else null
        }
        coEvery { weatherDao.findLocationsNear(any(), any(), any()) } returns emptyList()
        coEvery { weatherDao.findLocationsByNameAndCountry(any(), any()) } returns emptyList()
        coEvery { weatherDao.getDailyForecasts(any()) } returns emptyList()
        coEvery { forecastApi.getForecast(any(), any()) } returns forecastResponse()

        val result = repository.prefetchCountryCities("CU", limit = 1)

        assertEquals(1, result.warmed)
        coVerify(exactly = 1) { forecastApi.getForecast(23.13, -82.38) }
    }

    @Test
    fun `prefetchCountryCities returns 0 for blank code or empty catalog`() = runTest {
        assertEquals(CountryPrefetchResult(0, 0), repository.prefetchCountryCities("  ", 8))
        assertEquals(CountryPrefetchResult(0, 0), repository.prefetchCountryCities("CU", 3))
        coVerify(exactly = 0) { forecastApi.getForecast(any(), any()) }
    }
}
