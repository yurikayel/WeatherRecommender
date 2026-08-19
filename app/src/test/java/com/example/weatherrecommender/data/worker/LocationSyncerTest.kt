package com.example.weatherrecommender.data.worker

import com.example.weatherrecommender.data.local.dao.WeatherDao
import com.example.weatherrecommender.data.local.entity.LocationEntity
import com.example.weatherrecommender.domain.model.AppError
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSyncerTest {

    private val repository: WeatherRepository = mockk()
    private val weatherDao: WeatherDao = mockk()
    private val syncer = LocationSyncer(repository, weatherDao)

    private val londonEntity = LocationEntity(
        id = 1,
        name = "London",
        latitude = 51.5,
        longitude = -0.1,
        country = "UK",
        admin1 = "England",
        lastUpdated = 1L
    )

    @Test
    fun `syncAllLocations returns true when every refresh succeeds`() = runTest {
        coEvery { weatherDao.getAllLocations() } returns listOf(londonEntity)
        coEvery { repository.refreshForecast(any(), any()) } returns Result.Success(Unit)

        assertTrue(syncer.syncAllLocations())

        coVerify(exactly = 1) { repository.refreshForecast(any(), any()) }
    }

    @Test
    fun `syncAllLocations returns false when any refresh fails`() = runTest {
        val parisEntity = londonEntity.copy(id = 2, name = "Paris")
        val tokyoEntity = londonEntity.copy(id = 3, name = "Tokyo")
        val sydneyEntity = londonEntity.copy(id = 4, name = "Sydney")
        coEvery { weatherDao.getAllLocations() } returns listOf(
            londonEntity,
            parisEntity,
            tokyoEntity,
            sydneyEntity
        )
        coEvery { repository.refreshForecast(match { it.id == 1L }, any()) } returns Result.Success(Unit)
        coEvery { repository.refreshForecast(match { it.id == 2L }, any()) } returns Result.Error(
            AppError.NetworkError.NoConnectivity
        )
        coEvery { repository.refreshForecast(match { it.id == 3L }, any()) } returns Result.Success(Unit)
        coEvery { repository.refreshForecast(match { it.id == 4L }, any()) } returns Result.Success(Unit)

        assertFalse(syncer.syncAllLocations())

        coVerify(exactly = 4) { repository.refreshForecast(any(), any()) }
    }

    @Test
    fun `syncAllLocations refreshes in chunks of three with stagger between batches`() = runTest {
        val entities = (1L..4L).map { id ->
            londonEntity.copy(id = id, name = "City$id")
        }
        coEvery { weatherDao.getAllLocations() } returns entities
        coEvery { repository.refreshForecast(any(), any()) } returns Result.Success(Unit)

        assertTrue(syncer.syncAllLocations())

        coVerify(exactly = 4) { repository.refreshForecast(any(), any()) }
    }

    @Test
    fun `syncAllLocations returns true when cache is empty`() = runTest {
        coEvery { weatherDao.getAllLocations() } returns emptyList()

        assertTrue(syncer.syncAllLocations())

        coVerify(exactly = 0) { repository.refreshForecast(any(), any()) }
    }
}
