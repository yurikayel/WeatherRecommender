package com.example.weatherrecommender.integration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.weatherrecommender.data.image.PlaceImagePrefetcher
import com.example.weatherrecommender.data.local.WeatherDatabase
import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.mapper.toEntity
import com.example.weatherrecommender.data.remote.ForecastApi
import com.example.weatherrecommender.data.remote.GeocodingApi
import com.example.weatherrecommender.data.remote.MarineApi
import com.example.weatherrecommender.data.remote.NominatimApi
import com.example.weatherrecommender.data.remote.WikipediaPlaceImageResolver
import com.example.weatherrecommender.data.remote.dto.DailyForecastDto
import com.example.weatherrecommender.data.remote.dto.ForecastResponse
import com.example.weatherrecommender.data.remote.dto.GeocodingLocationDto
import com.example.weatherrecommender.data.remote.dto.GeocodingResponse
import com.example.weatherrecommender.data.remote.dto.MarineResponse
import com.example.weatherrecommender.data.repository.WeatherRepositoryImpl
import com.example.weatherrecommender.data.preferences.FirstRunThemeSettler
import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import kotlinx.coroutines.flow.first
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.CountryCityCatalog
import com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase
import com.example.weatherrecommender.domain.usecase.GetTopPicksUseCase
import com.example.weatherrecommender.domain.usecase.scorer.IndoorSightseeingScorer
import com.example.weatherrecommender.domain.usecase.scorer.OutdoorSightseeingScorer
import com.example.weatherrecommender.domain.usecase.scorer.SkiScorer
import com.example.weatherrecommender.domain.usecase.scorer.SurfScorer
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import com.example.weatherrecommender.ui.WeatherDestination
import com.example.weatherrecommender.ui.WeatherViewModel
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WeatherIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var database: WeatherDatabase
    private lateinit var weatherDao: WeatherDao
    private lateinit var repository: WeatherRepositoryImpl
    private val geocodingApi: GeocodingApi = mockk()
    private val forecastApi: ForecastApi = mockk()
    private val marineApi: MarineApi = mockk()
    private val nominatimApi: NominatimApi = mockk()
    private val placeImageResolver: WikipediaPlaceImageResolver = mockk()
    private val placeImagePrefetcher: PlaceImagePrefetcher = mockk(relaxed = true)
    private val getTopPicksUseCase: GetTopPicksUseCase = mockk(relaxed = true)
    private val connectivityObserver: ConnectivityObserver = mockk()
    private val deviceLocationProvider: DeviceLocationProvider = mockk(relaxed = true)
    private val firstRunThemeSettler: FirstRunThemeSettler = mockk(relaxed = true)

    private val getRankedActivities = GetRankedActivitiesUseCase(
        setOf(SurfScorer(), SkiScorer(), OutdoorSightseeingScorer(), IndoorSightseeingScorer())
    )

    private val london = Location(1, "London", 51.5, -0.1, "UK", "England")

    private fun sevenDayForecastResponse() = ForecastResponse(
        latitude = 51.5,
        longitude = -0.1,
        timezone = "Europe/London",
        daily = DailyForecastDto(
            time = (16..22).map { "2026-07-$it" },
            weatherCode = List(7) { 0 },
            temperature2mMax = List(7) { 22.0 },
            temperature2mMin = List(7) { 12.0 },
            precipitationSum = List(7) { 0.0 },
            snowfallSum = List(7) { 0.0 },
            windSpeed10mMax = List(7) { 10.0 }
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        weatherDao = database.weatherDao()

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
        every { connectivityObserver.observe() } returns flowOf(ConnectivityStatus.Available)
        coEvery { marineApi.getMarine(any(), any()) } returns MarineResponse(51.5, -0.1, null)
        coEvery { placeImageResolver.resolve(any(), any()) } returns null
        coEvery { forecastApi.getForecast(any(), any()) } returns sevenDayForecastResponse()
        coEvery { getTopPicksUseCase(any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::database.isInitialized) database.close()
    }

    private fun createViewModel(repo: WeatherRepository = repository): WeatherViewModel = WeatherViewModel(
        repo,
        getRankedActivities,
        getTopPicksUseCase,
        connectivityObserver,
        deviceLocationProvider,
        firstRunThemeSettler,
        CountryCityCatalog(emptyList())
    )

    @Test
    fun `repository refresh persists seven day forecast to room`() = runTest {
        val result = repository.refreshForecast(london)
        assertTrue(result is Result.Success)
        val days = weatherDao.getDailyForecastsFlow(london.id).first()
        assertEquals(7, days.size)
    }

    @Test
    fun `search then select location loads forecast and ranks activities`() = runTest {
        coEvery { geocodingApi.searchCity("Lon") } returns GeocodingResponse(
            results = listOf(
                GeocodingLocationDto(
                    id = 1, name = "London", latitude = 51.5, longitude = -0.1,
                    country = "UK", admin1 = "England", elevation = 25.0, population = 8_961_989
                )
            )
        )

        val viewModel = createViewModel()
        repository.refreshForecast(london)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("Lon")
            advanceTimeBy(600)
            viewModel.onLocationSelected(london)
            advanceUntilIdle()
            val loaded = expectMostRecentItem()
            assertNotNull(loaded.forecast)
            assertTrue(loaded.destination is WeatherDestination.Detail)
            assertTrue(loaded.rankedActivities.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `day selection re-ranks without network call`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onLocationSelected(london)
            advanceUntilIdle()
            val day0 = expectMostRecentItem()
            assertNotNull(day0.forecast)
            val firstDayTop = day0.rankedActivities.first()

            io.mockk.clearMocks(forecastApi, answers = false, recordedCalls = true)
            viewModel.onDaySelected(1)
            advanceUntilIdle()
            val afterDaySwitch = expectMostRecentItem()
            assertEquals(1, afterDaySwitch.selectedDayIndex)
            assertTrue(afterDaySwitch.rankedActivities.isNotEmpty())
            coVerify(exactly = 0) { forecastApi.getForecast(any(), any()) }
            if (firstDayTop.activity != afterDaySwitch.rankedActivities.first().activity) {
                assertTrue(afterDaySwitch.rankedActivities.first().score >= 0)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offline mode shows cached forecast`() = runTest {
        val days = (16..22).map { day ->
            DailyForecast("2026-07-$day", 0, 22.0, 12.0, 0.0, 0.0, 10.0)
        }
        weatherDao.insertLocationWithForecast(
            location = london.toEntity(),
            forecasts = days.map { it.toEntity(london.id) }
        )
        coEvery { forecastApi.getForecast(any(), any()) } throws IOException("offline")

        val cachedBeforeSelect = repository.getForecastFlow(london).first()
        assertNotNull(cachedBeforeSelect)

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(london)
        advanceUntilIdle()

        val loaded = viewModel.uiState.value
        assertEquals(7, loaded.forecast?.dailyForecasts?.size)
        assertTrue(loaded.destination is WeatherDestination.Detail)
    }

    @Test
    fun `empty forecast handles gracefully`() = runTest {
        val mockRepo: WeatherRepository = mockk(relaxed = true)
        every { mockRepo.observeRecentLocations(any()) } returns flowOf(emptyList())
        every { mockRepo.getForecastFlow(london) } returns flowOf(
            WeatherForecast(london, dailyForecasts = emptyList())
        )
        coEvery { mockRepo.markLocationViewed(london) } returns Unit
        coEvery { mockRepo.refreshForecast(london) } returns com.example.weatherrecommender.domain.model.Result.Success(Unit)

        val viewModel = createViewModel(mockRepo)
        viewModel.uiState.test {
            awaitItem()
            viewModel.onLocationSelected(london)
            advanceUntilIdle()
            viewModel.onDaySelected(0)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals(0, state.selectedDayIndex)
            assertTrue(state.rankedActivities.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun DailyForecast.toEntity(locationId: Long) = com.example.weatherrecommender.data.local.entity.DailyForecastEntity(
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
}
