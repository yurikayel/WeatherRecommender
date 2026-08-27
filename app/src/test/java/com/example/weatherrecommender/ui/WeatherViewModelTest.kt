package com.example.weatherrecommender.ui

import app.cash.turbine.test
import com.example.weatherrecommender.data.preferences.FirstRunThemeSettler
import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.domain.location.GeoCoordinates
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.CountryPrefetchResult
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.CountryCityCatalog
import com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase
import com.example.weatherrecommender.domain.usecase.GetTopPicksUseCase
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import com.example.weatherrecommender.ui.map.MapCameraPosition
import com.example.weatherrecommender.ui.map.MapHopProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class WeatherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository: WeatherRepository = mockk()
    private val getRankedActivitiesUseCase: GetRankedActivitiesUseCase = mockk()
    private val getTopPicksUseCase: GetTopPicksUseCase = mockk()
    private val connectivityObserver: ConnectivityObserver = mockk()
    private val deviceLocationProvider: DeviceLocationProvider = mockk()
    private val firstRunThemeSettler: FirstRunThemeSettler = mockk(relaxed = true)

    private lateinit var viewModel: WeatherViewModel

    private val location = Location(1, "London", 51.5, -0.1, "UK", "England")
    private val forecast = WeatherForecast(
        location = location,
        dailyForecasts = listOf(
            DailyForecast("2026-07-16", 0, 22.0, 12.0, 0.0, 0.0, 10.0),
            DailyForecast("2026-07-17", 61, 14.0, 9.0, 20.0, 0.0, 12.0)
        )
    )
    private val day0Activities = listOf(
        RankedActivity(RecommendedActivity.OUTDOOR_SIGHTSEEING, 90, ReasonKey.OUTDOOR_MILD, listOf(22))
    )
    private val day1Activities = listOf(
        RankedActivity(RecommendedActivity.INDOOR_SIGHTSEEING, 95, ReasonKey.INDOOR_BAD_WEATHER)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { connectivityObserver.observe() } returns flowOf(ConnectivityStatus.Available)
        every { repository.observeRecentLocations(any()) } returns flowOf(emptyList())
        coEvery { repository.markLocationViewed(any()) } returns Unit
        coEvery { repository.prefetchNearbyCities(any()) } returns Unit
        coEvery { repository.prefetchCountryCities(any(), any()) } returns CountryPrefetchResult(0, 0)
        coEvery { repository.hasFreshForecast(any()) } returns false
        coEvery { getTopPicksUseCase(any(), any()) } returns emptyList()
        every { deviceLocationProvider.hasLocationPermission() } returns false
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns null
        every { getRankedActivitiesUseCase.invoke(any(), any()) } answers {
            when (invocation.args[1] as Int) {
                0 -> day0Activities
                1 -> day1Activities
                else -> emptyList()
            }
        }
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WeatherViewModel = WeatherViewModel(
        repository,
        getRankedActivitiesUseCase,
        getTopPicksUseCase,
        connectivityObserver,
        deviceLocationProvider,
        firstRunThemeSettler,
        CountryCityCatalog(emptyList())
    )

    @Test
    fun `when search fails with NoConnectivity emit error state`() = runTest {
        coEvery { repository.searchCity(any()) } returns Result.Error(AppError.NetworkError.NoConnectivity)

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("Lon")
            awaitItem()
            advanceTimeBy(600)
            advanceUntilIdle()

            val finalState = expectMostRecentItem()
            assertTrue(finalState.searchError is com.example.weatherrecommender.ui.util.UiText.StringResource)
            assertEquals(
                com.example.weatherrecommender.R.string.error_network_offline,
                (finalState.searchError as com.example.weatherrecommender.ui.util.UiText.StringResource).resId
            )
            assertTrue(finalState.search is SearchUiState.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search success returns locations after debounce`() = runTest {
        coEvery { repository.searchCity("Lon") } returns Result.Success(listOf(location))

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("Lon")
            awaitItem()
            advanceTimeBy(600)
            advanceUntilIdle()

            val finalState = expectMostRecentItem()
            assertEquals(listOf(location), finalState.searchResults)
            assertEquals(false, finalState.isSearching)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `location selection loads forecast and ranks the first day`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.destination is WeatherDestination.Detail)
        assertEquals(location, state.selectedLocation)
        assertNotNull(state.forecast)
        assertEquals(0, state.selectedDayIndex)
        assertEquals(day0Activities, state.rankedActivities)
    }

    @Test
    fun `selecting a different day re-ranks activities without network`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities
        every { getRankedActivitiesUseCase.invoke(forecast, 1) } returns day1Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        viewModel.onDaySelected(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.selectedDayIndex)
        assertEquals(day1Activities, state.rankedActivities)
    }

    @Test
    fun `onBack returns to the home screen`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()
        viewModel.onBack()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedLocation)
        assertNull(state.forecast)
        assertTrue(state.rankedActivities.isEmpty())
    }

    @Test
    fun `refresh failure with cached data fails the forecast lane not search`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Error(AppError.NetworkError.NoConnectivity)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.forecast)
        assertNull(state.searchError)
        assertTrue(state.forecastFetch is FetchStatus.Failed)
    }

    @Test
    fun `refresh when offline fails the forecast lane`() = runTest {
        every { connectivityObserver.observe() } returns flowOf(ConnectivityStatus.Unavailable)
        every { repository.observeRecentLocations(any()) } returns flowOf(emptyList())
        coEvery { repository.markLocationViewed(any()) } returns Unit
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.forecastFetch is FetchStatus.Failed)
    }

    @Test
    fun `top picks are loaded on init`() = runTest {
        val picks = listOf(
            com.example.weatherrecommender.domain.model.TopPick(
                location = Location(-4, "Lisbon", 38.7, -9.1, "Portugal", null, hasSeaAccess = true),
                topActivity = RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(90, 10)),
                weatherCode = 0,
                maxTemp = 26.0
            )
        )
        coEvery { getTopPicksUseCase(any(), any()) } returns picks
        every { repository.observeRecentLocations(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals(picks, viewModel.uiState.value.topPicks)
    }

    @Test
    fun `home refresh force-refreshes top picks`() = runTest {
        val initial = listOf(
            com.example.weatherrecommender.domain.model.TopPick(
                location = Location(-4, "Lisbon", 38.7, -9.1, "Portugal", null, hasSeaAccess = true),
                topActivity = RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(90, 10)),
                weatherCode = 0,
                maxTemp = 26.0
            )
        )
        val refreshed = listOf(
            com.example.weatherrecommender.domain.model.TopPick(
                location = Location(-2, "Sydney", -33.8, 151.2, "Australia", null, hasSeaAccess = true),
                topActivity = RankedActivity(RecommendedActivity.OUTDOOR_SIGHTSEEING, 80, ReasonKey.OUTDOOR_MILD, listOf(22)),
                weatherCode = 1,
                maxTemp = 24.0
            )
        )
        coEvery { getTopPicksUseCase(any(), forceRefresh = false) } returns initial
        coEvery { getTopPicksUseCase(any(), forceRefresh = true) } returns refreshed
        every { repository.observeRecentLocations(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(500)
        advanceUntilIdle()
        assertEquals(initial, viewModel.uiState.value.topPicks)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(refreshed, viewModel.uiState.value.topPicks)
        assertEquals(false, viewModel.uiState.value.isRefreshingTopPicks)
        io.mockk.coVerify { getTopPicksUseCase(any(), forceRefresh = true) }
    }

    @Test
    fun `history from repository is exposed on home state`() = runTest {
        val history = listOf(
            Location(-4, "Lisbon", 38.7, -9.1, "Portugal", "Lisbon"),
            Location(1, "London", 51.5, -0.1, "UK", "England")
        )
        every { repository.observeRecentLocations(10) } returns flowOf(history)
        viewModel = createViewModel()

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(history, viewModel.uiState.value.recentHistory)
    }

    @Test
    fun `location selection marks the city as viewed`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        io.mockk.coVerify { repository.markLocationViewed(location) }
        io.mockk.coVerify { repository.refreshForecast(location) }
    }

    @Test
    fun `search centers map camera on first result`() = runTest {
        coEvery { repository.searchCity("Lon") } returns Result.Success(listOf(location))

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChanged("Lon")
            awaitItem()
            advanceTimeBy(600)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(location.latitude, state.mapCamera.latitude, 0.0)
            assertEquals(location.longitude, state.mapCamera.longitude, 0.0)
            assertEquals(location, state.mapPin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `location selection updates map camera and pin`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        // Pin is synchronous; hop profile waits for the Room freshness check.
        assertEquals(location, viewModel.uiState.value.mapPin)
        assertTrue(viewModel.uiState.value.destination is WeatherDestination.Home)

        runCurrent()
        val flying = viewModel.uiState.value
        assertEquals(location.latitude, flying.mapCamera.latitude, 0.0)
        assertEquals(MapHopProfile.CACHE_MISS, flying.mapCamera.hop)
        assertTrue(flying.destination is WeatherDestination.Home)

        advanceTimeBy(MapHopProfile.CACHE_MISS.contentRevealMs - 1)
        assertTrue(viewModel.uiState.value.destination is WeatherDestination.Home)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(location.latitude, state.mapCamera.latitude, 0.0)
        assertEquals(location.longitude, state.mapCamera.longitude, 0.0)
        assertEquals(location, state.mapPin)
        assertTrue(state.destination is WeatherDestination.Detail)
    }

    @Test
    fun `cached location selection uses the snappy hop and reveals earlier`() = runTest {
        coEvery { repository.hasFreshForecast(location) } returns true
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        runCurrent()

        val flying = viewModel.uiState.value
        assertEquals(MapHopProfile.CACHED, flying.mapCamera.hop)
        assertTrue(flying.destination is WeatherDestination.Home)

        advanceTimeBy(MapHopProfile.CACHED.contentRevealMs - 1)
        assertTrue(viewModel.uiState.value.destination is WeatherDestination.Home)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.destination is WeatherDestination.Detail)
    }

    @Test
    fun `init frames home map with the static default camera`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MapCameraPosition.DEFAULT, state.mapCamera)
        assertEquals(MapCameraPosition.HOME_DEFAULT_ZOOM, state.mapCamera.zoom, 0.0)
        assertNull(state.mapPin)
        assertNull(state.deviceLocation)
    }

    @Test
    fun `onBack without a device location falls back to the static default camera`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()
        viewModel.onBack()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WeatherDestination.Home, state.destination)
        assertNull(state.selectedLocation)
        assertNull(state.mapPin)
        assertEquals(MapCameraPosition.DEFAULT, state.mapCamera)
        assertEquals(MapCameraPosition.HOME_DEFAULT_ZOOM, state.mapCamera.zoom, 0.0)
    }

    @Test
    fun `onBack with a device location centers home map on it at home zoom`() = runTest {
        val deviceCity = Location(-1_000_001, "Lisbon", 38.7, -9.1, "Portugal", "Lisbon")
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns GeoCoordinates(38.7, -9.1)
        coEvery { repository.reverseGeocode(38.7, -9.1) } returns Result.Success(deviceCity)
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedLocation)

        viewModel.onLocationSelected(location)
        advanceUntilIdle()
        viewModel.onBack()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WeatherDestination.Home, state.destination)
        assertNull(state.selectedLocation)
        assertNull(state.mapPin)
        assertEquals(deviceCity, state.deviceLocation)
        assertEquals(deviceCity.latitude, state.mapCamera.latitude, 0.0)
        assertEquals(deviceCity.longitude, state.mapCamera.longitude, 0.0)
        assertEquals(MapCameraPosition.HOME_DEFAULT_ZOOM, state.mapCamera.zoom, 0.0)
    }

    @Test
    fun `map tap reverse geocodes then selects location`() = runTest {
        val pinned = Location(-1_000_042, "London", 51.5, -0.1, "UK", "England")
        coEvery { repository.reverseGeocode(51.5, -0.1) } returns Result.Success(pinned)
        every { repository.getForecastFlow(pinned) } returns flowOf(forecast.copy(location = pinned))
        coEvery { repository.refreshForecast(pinned) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onMapTapped(51.5, -0.1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(pinned, state.selectedLocation)
        assertEquals(false, state.isResolvingMapTap)
        assertEquals(pinned, state.mapPin)
        io.mockk.coVerify { repository.reverseGeocode(51.5, -0.1) }
    }

    @Test
    fun `selecting a city cancels an in-flight map tap so it cannot overwrite`() = runTest {
        val pinned = Location(-1_000_042, "London", 51.5, -0.1, "UK", "England")
        coEvery { repository.reverseGeocode(51.5, -0.1) } coAnswers {
            delay(10_000)
            Result.Success(pinned)
        }
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onMapTapped(51.5, -0.1)
        runCurrent()
        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        assertEquals(location, viewModel.uiState.value.selectedLocation)
        io.mockk.coVerify(exactly = 0) { repository.refreshForecast(pinned) }
        io.mockk.coVerify(exactly = 0) { repository.getForecastFlow(pinned) }
    }

    @Test
    fun `granted location permission with fix populates chip and keeps home`() = runTest {
        val deviceCity = Location(-1_000_001, "Lisbon", 38.7, -9.1, "Portugal", "Lisbon")
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns GeoCoordinates(38.7, -9.1)
        coEvery { repository.reverseGeocode(38.7, -9.1) } returns Result.Success(deviceCity)
        coEvery { repository.prefetchCountryCities("PT", 8) } returns CountryPrefetchResult(8, 12)

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(deviceCity, state.deviceLocation)
        assertNull(state.selectedLocation)
        assertNull(state.mapPin)
        assertEquals(deviceCity.latitude, state.mapCamera.latitude, 0.0)
        assertEquals(deviceCity.longitude, state.mapCamera.longitude, 0.0)
        assertEquals(MapCameraPosition.HOME_DEFAULT_ZOOM, state.mapCamera.zoom, 0.0)
        io.mockk.coVerify(exactly = 0) { repository.refreshForecast(any()) }
        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 8) }
    }

    @Test
    fun `granted location with country code starts country warm with budget 8`() = runTest {
        val deviceCity = Location(
            id = -1_000_001,
            name = "Lisbon",
            latitude = 38.7,
            longitude = -9.1,
            country = "Portugal",
            admin1 = "Lisbon",
            countryCode = "pt"
        )
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns GeoCoordinates(38.7, -9.1)
        coEvery { repository.reverseGeocode(38.7, -9.1) } returns Result.Success(deviceCity)
        coEvery { repository.prefetchCountryCities("PT", 8) } returns CountryPrefetchResult(8, 12)

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 8) }
        assertEquals(deviceCity, viewModel.uiState.value.deviceLocation)
        assertNull(viewModel.uiState.value.selectedLocation)
    }

    @Test
    fun `selecting a city adds two more country warm slots`() = runTest {
        val deviceCity = Location(
            id = -1_000_001,
            name = "Lisbon",
            latitude = 38.7,
            longitude = -9.1,
            country = "Portugal",
            admin1 = "Lisbon",
            countryCode = "PT"
        )
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns GeoCoordinates(38.7, -9.1)
        coEvery { repository.reverseGeocode(38.7, -9.1) } returns Result.Success(deviceCity)
        coEvery { repository.prefetchCountryCities("PT", 8) } returns CountryPrefetchResult(8, 12)
        coEvery { repository.prefetchCountryCities("PT", 2) } returns CountryPrefetchResult(2, 10)
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 8) }
        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 2) }
    }

    @Test
    fun `without GPS selecting a city warms that country from name or ISO`() = runTest {
        val lisbon = location.copy(name = "Lisbon", country = "Portugal", countryCode = null)
        every { repository.getForecastFlow(lisbon) } returns flowOf(forecast.copy(location = lisbon))
        coEvery { repository.refreshForecast(lisbon) } returns Result.Success(Unit)
        coEvery { repository.prefetchCountryCities("PT", 2) } returns CountryPrefetchResult(2, 10)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(lisbon)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 2) }
    }

    @Test
    fun `country warm restores budget when a pass warms nothing but catalog remains`() = runTest {
        val deviceCity = Location(
            id = -1_000_001,
            name = "Lisbon",
            latitude = 38.7,
            longitude = -9.1,
            country = "Portugal",
            admin1 = "Lisbon",
            countryCode = "PT"
        )
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns GeoCoordinates(38.7, -9.1)
        coEvery { repository.reverseGeocode(38.7, -9.1) } returns Result.Success(deviceCity)
        coEvery { repository.prefetchCountryCities("PT", 8) } returns CountryPrefetchResult(0, 5)
        coEvery { repository.prefetchCountryCities("PT", 10) } returns CountryPrefetchResult(2, 3)
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 8) }
        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 10) }
    }

    @Test
    fun `country warm restores unused slots after a partial pass`() = runTest {
        val deviceCity = Location(
            id = -1_000_001,
            name = "Lisbon",
            latitude = 38.7,
            longitude = -9.1,
            country = "Portugal",
            admin1 = "Lisbon",
            countryCode = "PT"
        )
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns GeoCoordinates(38.7, -9.1)
        coEvery { repository.reverseGeocode(38.7, -9.1) } returns Result.Success(deviceCity)
        coEvery { repository.prefetchCountryCities("PT", 8) } returns CountryPrefetchResult(3, 10)
        coEvery { repository.prefetchCountryCities("PT", 7) } returns CountryPrefetchResult(2, 8)
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 8) }
        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 7) }
    }

    @Test
    fun `with GPS selecting a foreign city still warms the device country`() = runTest {
        val deviceCity = Location(
            id = -1_000_001,
            name = "Lisbon",
            latitude = 38.7,
            longitude = -9.1,
            country = "Portugal",
            admin1 = "Lisbon",
            countryCode = "PT"
        )
        val madrid = Location(
            id = 2,
            name = "Madrid",
            latitude = 40.4,
            longitude = -3.7,
            country = "Spain",
            admin1 = "Madrid",
            countryCode = "ES"
        )
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns GeoCoordinates(38.7, -9.1)
        coEvery { repository.reverseGeocode(38.7, -9.1) } returns Result.Success(deviceCity)
        coEvery { repository.prefetchCountryCities("PT", 8) } returns CountryPrefetchResult(8, 12)
        coEvery { repository.prefetchCountryCities("PT", 2) } returns CountryPrefetchResult(2, 10)
        every { repository.getForecastFlow(madrid) } returns flowOf(forecast.copy(location = madrid))
        coEvery { repository.refreshForecast(madrid) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        viewModel.onLocationSelected(madrid)
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 8) }
        io.mockk.coVerify(exactly = 1) { repository.prefetchCountryCities("PT", 2) }
        io.mockk.coVerify(exactly = 0) { repository.prefetchCountryCities("ES", any()) }
    }

    @Test
    fun `current location click opens cached device city weather`() = runTest {
        val deviceCity = Location(-1_000_001, "Lisbon", 38.7, -9.1, "Portugal", "Lisbon")
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns GeoCoordinates(38.7, -9.1)
        coEvery { repository.reverseGeocode(38.7, -9.1) } returns Result.Success(deviceCity)
        every { repository.getForecastFlow(deviceCity) } returns flowOf(forecast.copy(location = deviceCity))
        coEvery { repository.refreshForecast(deviceCity) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(any(), 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedLocation)
        assertEquals(deviceCity, viewModel.uiState.value.deviceLocation)

        viewModel.onCurrentLocationClick()
        advanceUntilIdle()
        assertEquals(deviceCity, viewModel.uiState.value.selectedLocation)

        viewModel.onBack()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedLocation)
        assertEquals(deviceCity, viewModel.uiState.value.deviceLocation)
        assertEquals(deviceCity.latitude, viewModel.uiState.value.mapCamera.latitude, 0.0)
        assertEquals(deviceCity.longitude, viewModel.uiState.value.mapCamera.longitude, 0.0)
    }

    @Test
    fun `no device fix leaves chip hidden and keeps the static default camera`() = runTest {
        every { deviceLocationProvider.hasLocationPermission() } returns true
        coEvery { deviceLocationProvider.getLastKnownLocation() } returns null

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationPermissionResult(granted = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.deviceLocation)
        assertNull(state.selectedLocation)
        assertEquals(MapCameraPosition.DEFAULT, state.mapCamera)
    }

    @Test
    fun `map tap failure stays on the map tap lane`() = runTest {
        coEvery { repository.reverseGeocode(any(), any()) } returns Result.Error(AppError.ApiError.NotFound)
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onMapTapped(51.5, -0.1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.mapTapFetch is FetchStatus.Failed)
        assertNull(state.searchError)
        assertTrue(state.forecastFetch !is FetchStatus.Failed)
    }

    @Test
    fun `home refresh when offline fails the top picks lane`() = runTest {
        every { connectivityObserver.observe() } returns flowOf(ConnectivityStatus.Unavailable)
        every { repository.observeRecentLocations(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.topPicksFetch is FetchStatus.Failed)
        assertNull(viewModel.uiState.value.searchError)
    }
}
