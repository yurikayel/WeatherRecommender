package com.example.weatherrecommender.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.local.entity.DailyForecastEntity
import com.example.weatherrecommender.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDaoIntegrationTest {

    private lateinit var weatherDao: WeatherDao
    private lateinit var database: WeatherDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        weatherDao = database.weatherDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveLocationWithForecasts() = runTest {
        val location = sampleLocation(id = 1, lastUpdated = 100L)
        val forecasts = listOf(
            forecastEntity(locationId = 1, date = "2026-07-16"),
            forecastEntity(locationId = 1, date = "2026-07-17")
        )

        weatherDao.insertLocationWithForecast(location, forecasts)

        val storedLocation = weatherDao.getLocation(1)
        val storedForecasts = weatherDao.getDailyForecastsFlow(1).first()

        assertNotNull(storedLocation)
        assertEquals("London", storedLocation?.name)
        assertEquals(2, storedForecasts.size)
        assertEquals("2026-07-16", storedForecasts.first().date)
    }

    @Test
    fun upsertPreservesLastViewedAt() = runTest {
        val viewedAt = 9_000L
        val location = sampleLocation(id = 2, lastUpdated = 200L, lastViewedAt = viewedAt)
        weatherDao.insertLocation(location)

        val existingViewedAt = weatherDao.getLocation(2)?.lastViewedAt ?: 0L
        val refreshed = location.copy(lastUpdated = 500L, lastViewedAt = 0L)
        weatherDao.insertLocationWithForecast(
            location = refreshed.copy(lastViewedAt = existingViewedAt),
            forecasts = listOf(forecastEntity(locationId = 2, date = "2026-07-16"))
        )

        val merged = weatherDao.getLocation(2)
        assertEquals(viewedAt, merged?.lastViewedAt)
    }

    @Test
    fun evictionRemovesOldestLocations() = runTest {
        weatherDao.insertLocation(sampleLocation(id = 10, lastUpdated = 100L))
        weatherDao.insertLocation(sampleLocation(id = 11, lastUpdated = 200L))
        weatherDao.insertLocation(sampleLocation(id = 12, lastUpdated = 300L))

        val oldest = weatherDao.getOldestLocationIds(1)
        assertEquals(listOf(10L), oldest)

        weatherDao.deleteLocationWithForecasts(oldest.first())

        assertEquals(2, weatherDao.getLocationCount())
        assertNotNull(weatherDao.getLocation(12))
    }

    @Test
    fun observeForecastFlowEmitsOnUpdate() = runTest {
        val location = sampleLocation(id = 3, lastUpdated = 400L)
        weatherDao.insertLocationWithForecast(
            location = location,
            forecasts = listOf(forecastEntity(locationId = 3, date = "2026-07-16", maxTemp = 20.0))
        )

        val initial = weatherDao.getDailyForecastsFlow(3).first()
        assertEquals(20.0, initial.first().maxTemp, 0.0)

        weatherDao.insertLocationWithForecast(
            location = location.copy(lastUpdated = 500L),
            forecasts = listOf(
                forecastEntity(locationId = 3, date = "2026-07-16", maxTemp = 25.0),
                forecastEntity(locationId = 3, date = "2026-07-17", maxTemp = 18.0)
            )
        )

        val updated = weatherDao.getDailyForecastsFlow(3).first()
        assertEquals(2, updated.size)
        assertTrue(updated.any { it.maxTemp == 25.0 })
    }

    private fun sampleLocation(
        id: Long,
        lastUpdated: Long,
        lastViewedAt: Long = 0L
    ) = LocationEntity(
        id = id,
        name = "London",
        latitude = 51.5,
        longitude = -0.1,
        country = "UK",
        admin1 = "England",
        lastUpdated = lastUpdated,
        lastViewedAt = lastViewedAt
    )

    private fun forecastEntity(
        locationId: Long,
        date: String,
        maxTemp: Double = 22.0
    ) = DailyForecastEntity(
        locationId = locationId,
        date = date,
        maxTemp = maxTemp,
        minTemp = 12.0,
        weatherCode = 0,
        precipitationSum = 0.0,
        maxWindSpeed = 10.0,
        snowfallSum = 0.0
    )
}
