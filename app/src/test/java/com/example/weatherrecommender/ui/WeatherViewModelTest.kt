package com.example.weatherrecommender.ui

import app.cash.turbine.test
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import com.example.weatherrecommender.domain.usecase.GetRankedActivitiesUseCase
import com.example.weatherrecommender.domain.usecase.GetTopPicksUseCase
import com.example.weatherrecommender.domain.util.ConnectivityObserver
import com.example.weatherrecommender.domain.util.ConnectivityStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository: WeatherRepository = mockk()
    private val getRankedActivitiesUseCase: GetRankedActivitiesUseCase = mockk()
    private val getTopPicksUseCase: GetTopPicksUseCase = mockk()
    private val connectivityObserver: ConnectivityObserver = mockk()

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
        coEvery { getTopPicksUseCase(any(), any()) } returns emptyList()
        viewModel = WeatherViewModel(
            repository,
            getRankedActivitiesUseCase,
            getTopPicksUseCase,
            connectivityObserver
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
            assertTrue(finalState.error is com.example.weatherrecommender.ui.util.UiText.StringResource)
            assertEquals(
                com.example.weatherrecommender.R.string.error_network_offline,
                (finalState.error as com.example.weatherrecommender.ui.util.UiText.StringResource).resId
            )
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
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        val state = viewModel.uiState.value
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
    fun `refresh failure with cached data sets syncError not blocking error`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Error(AppError.NetworkError.NoConnectivity)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.forecast)
        assertNull(state.error)
        assertNotNull(state.syncError)
    }

    @Test
    fun `refresh when offline sets syncError`() = runTest {
        every { connectivityObserver.observe() } returns flowOf(ConnectivityStatus.Unavailable)
        every { repository.observeRecentLocations(any()) } returns flowOf(emptyList())
        coEvery { repository.markLocationViewed(any()) } returns Unit
        viewModel = WeatherViewModel(
            repository,
            getRankedActivitiesUseCase,
            getTopPicksUseCase,
            connectivityObserver
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast, 0) } returns day0Activities

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.syncError)
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
        viewModel = WeatherViewModel(
            repository,
            getRankedActivitiesUseCase,
            getTopPicksUseCase,
            connectivityObserver
        )

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
        viewModel = WeatherViewModel(
            repository,
            getRankedActivitiesUseCase,
            getTopPicksUseCase,
            connectivityObserver
        )

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
        viewModel = WeatherViewModel(
            repository,
            getRankedActivitiesUseCase,
            getTopPicksUseCase,
            connectivityObserver
        )

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
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(location.latitude, state.mapCamera.latitude, 0.0)
        assertEquals(location.longitude, state.mapCamera.longitude, 0.0)
        assertEquals(location, state.mapPin)
    }

    @Test
    fun `onBack keeps map camera centered on last city`() = runTest {
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
        assertEquals(location, state.mapPin)
        assertEquals(location.latitude, state.mapCamera.latitude, 0.0)
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
}
