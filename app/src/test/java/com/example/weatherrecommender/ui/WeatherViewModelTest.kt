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
    private val connectivityObserver: ConnectivityObserver = mockk()

    private lateinit var viewModel: WeatherViewModel

    private val location = Location(1, "London", 51.5, -0.1, "UK", "England")
    private val forecast = WeatherForecast(
        location = location,
        dailyForecasts = listOf(
            DailyForecast("2026-07-16", 0, 22.0, 12.0, 0.0, 0.0, 10.0)
        )
    )
    private val rankedActivities = listOf(
        RankedActivity(RecommendedActivity.OUTDOOR_SIGHTSEEING, 90, ReasonKey.OUTDOOR_MILD, listOf(22))
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { connectivityObserver.observe() } returns flowOf(ConnectivityStatus.Available)
        viewModel = WeatherViewModel(repository, getRankedActivitiesUseCase, connectivityObserver)
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
    fun `location selection loads forecast from repository flow`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast) } returns rankedActivities

        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(location, state.selectedLocation)
        assertNotNull(state.forecast)
        assertEquals(rankedActivities, state.rankedActivities)
    }

    @Test
    fun `refresh failure with cached data sets syncError not blocking error`() = runTest {
        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Error(AppError.NetworkError.NoConnectivity)
        every { getRankedActivitiesUseCase.invoke(forecast) } returns rankedActivities

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
        viewModel = WeatherViewModel(repository, getRankedActivitiesUseCase, connectivityObserver)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        every { repository.getForecastFlow(location) } returns flowOf(forecast)
        coEvery { repository.refreshForecast(location) } returns Result.Success(Unit)
        every { getRankedActivitiesUseCase.invoke(forecast) } returns rankedActivities

        viewModel.onLocationSelected(location)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.syncError)
    }
}
