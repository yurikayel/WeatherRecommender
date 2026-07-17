package com.example.weatherrecommender.data.repository

import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.remote.ForecastApi
import com.example.weatherrecommender.data.remote.GeocodingApi
import com.example.weatherrecommender.data.remote.MarineApi
import com.example.weatherrecommender.data.remote.dto.DailyForecastDto
import com.example.weatherrecommender.data.remote.dto.ForecastResponse
import com.example.weatherrecommender.data.remote.dto.GeocodingLocationDto
import com.example.weatherrecommender.data.remote.dto.GeocodingResponse
import com.example.weatherrecommender.data.remote.dto.MarineDailyDto
import com.example.weatherrecommender.data.remote.dto.MarineResponse
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

class WeatherRepositoryImplTest {

    private val geocodingApi: GeocodingApi = mockk()
    private val forecastApi: ForecastApi = mockk()
    private val marineApi: MarineApi = mockk()
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
        repository = WeatherRepositoryImpl(geocodingApi, forecastApi, marineApi, weatherDao)
        // Default: inland (no marine data) unless a test overrides it.
        coEvery { marineApi.getMarine(any(), any()) } returns MarineResponse(51.5, -0.1, null)
    }

    @Test
    fun `searchCity returns mapped locations on success`() = runTest {
        coEvery { geocodingApi.searchCity("Lon") } returns GeocodingResponse(
            results = listOf(
                GeocodingLocationDto(
                    id = 1, name = "London", latitude = 51.5, longitude = -0.1,
                    country = "UK", admin1 = "England", elevation = 25.0, population = 8_961_989
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
    }

    @Test
    fun `searchCity returns empty list when API returns null results`() = runTest {
        coEvery { geocodingApi.searchCity("xyz") } returns GeocodingResponse(results = null)

        val result = repository.searchCity("xyz")

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun `searchCity maps IOException to NoConnectivity`() = runTest {
        coEvery { geocodingApi.searchCity(any()) } throws IOException("offline")

        val result = repository.searchCity("Lon")

        assertTrue(result is Result.Error)
        assertEquals(AppError.NetworkError.NoConnectivity, (result as Result.Error).error)
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
    fun `getForecastFlow returns null when dao is empty`() = runTest {
        every { weatherDao.getLocationFlow(location.id) } returns flowOf(null)
        every { weatherDao.getDailyForecastsFlow(location.id) } returns flowOf(emptyList())

        val forecast = repository.getForecastFlow(location).first()

        assertNull(forecast)
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
        coEvery { weatherDao.getLocationCount() } returns 21
        coEvery { weatherDao.getOldestLocationIds(1) } returns listOf(99L)

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Success)
        coVerify { weatherDao.deleteLocationWithForecasts(99L) }
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
    fun `refreshForecast maps 404 to NotFound`() = runTest {
        coEvery { forecastApi.getForecast(any(), any()) } throws HttpException(
            Response.error<String>(404, "".toResponseBody())
        )

        val result = repository.refreshForecast(location)

        assertTrue(result is Result.Error)
        assertEquals(AppError.ApiError.NotFound, (result as Result.Error).error)
    }
}
