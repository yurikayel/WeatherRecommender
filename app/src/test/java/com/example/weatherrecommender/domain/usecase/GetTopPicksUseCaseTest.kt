package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.Result
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.domain.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GetTopPicksUseCaseTest {

    private val repository: WeatherRepository = mockk()
    private val getRankedActivities: GetRankedActivitiesUseCase = mockk()
    private val featuredCities: FeaturedCities = mockk()
    private val topPicksCache = TopPicksCache()

    private val useCase = GetTopPicksUseCase(
        repository,
        getRankedActivities,
        featuredCities,
        topPicksCache
    )

    private val lisbon = Location(-4, "Lisbon", 38.7, -9.1, "Portugal", null, hasSeaAccess = true)
    private val zurich = Location(-9, "Zurich", 47.4, 8.5, "Switzerland", null, elevation = 429.0)

    private fun forecast(location: Location) = WeatherForecast(
        location = location,
        dailyForecasts = listOf(DailyForecast("2026-07-16", 0, 26.0, 18.0, 0.0, 0.0, 8.0, 1.0))
    )

    @Test
    fun `builds a top pick per successfully loaded city and drops failures`() = runTest {
        every { featuredCities.randomWeightedByPopulation(any(), any()) } returns listOf(lisbon, zurich)
        coEvery { repository.getForecastRemote(lisbon) } returns Result.Success(forecast(lisbon))
        coEvery { repository.getForecastRemote(zurich) } returns Result.Error(
            com.example.weatherrecommender.domain.model.AppError.NetworkError.NoConnectivity
        )
        every { getRankedActivities.invoke(any(), 0) } returns listOf(
            RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(100, 8))
        )

        val picks = useCase.invoke(2, forceRefresh = true)

        assertEquals(1, picks.size)
        assertEquals("Lisbon", picks.first().location.name)
        assertEquals(RecommendedActivity.SURFING, picks.first().topActivity.activity)
    }

    @Test
    fun `returns cached picks without hitting the repository again`() = runTest {
        val cached = listOf(
            TopPick(
                location = lisbon,
                topActivity = RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(100, 8)),
                weatherCode = 0,
                maxTemp = 26.0
            )
        )
        topPicksCache.put(cached)

        val picks = useCase.invoke()

        assertEquals(cached, picks)
        coVerify(exactly = 0) { repository.getForecastRemote(any()) }
    }

    @Test
    fun `forceRefresh bypasses the in-memory cache`() = runTest {
        val cached = listOf(
            TopPick(
                location = lisbon,
                topActivity = RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(100, 8)),
                weatherCode = 0,
                maxTemp = 26.0
            )
        )
        topPicksCache.put(cached)
        every { featuredCities.randomWeightedByPopulation(any(), any()) } returns listOf(lisbon)
        coEvery { repository.getForecastRemote(lisbon) } returns Result.Success(forecast(lisbon))
        every { getRankedActivities.invoke(any(), 0) } returns listOf(
            RankedActivity(RecommendedActivity.SURFING, 70, ReasonKey.SURF_IDEAL, listOf(80, 12))
        )

        val picks = useCase.invoke(1, forceRefresh = true)

        assertEquals(1, picks.size)
        assertEquals(70, picks.first().topActivity.score)
        coVerify(exactly = 1) { repository.getForecastRemote(lisbon) }
    }

    @Test
    fun `weighted selection returns the requested distinct count`() {
        val featured = FeaturedCities()
        val picks = featured.randomWeightedByPopulation(4, Random(42))

        assertEquals(4, picks.size)
        assertEquals(picks.distinctBy { it.id }.size, picks.size)
    }

    @Test
    fun `featured seed ids are negative to avoid GeoNames collisions`() {
        val featured = FeaturedCities()
        assertEquals(14, featured.all.size)
        assertTrue(featured.all.all { it.id < 0L })
        assertEquals(featured.all.size, featured.all.distinctBy { it.placeKey }.size)
        assertEquals(
            HubCities.all.first { it.name == "Lisbon" }.id,
            featured.all.first { it.name == "Lisbon" }.id
        )
    }
}
