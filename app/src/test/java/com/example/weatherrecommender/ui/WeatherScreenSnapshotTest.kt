package com.example.weatherrecommender.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.theme.WeatherRecommenderTheme
import org.junit.Rule
import org.junit.Test

class WeatherScreenSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        // Paparazzi 2.0.0-alpha05: small tolerance absorbs font anti-aliasing differences between
        // Windows (where goldens are recorded) and Linux CI despite identical layouts.
        maxPercentDifference = 0.5
    )

    /** Satisfies [rememberLauncherForActivityResult] used by share-permission UX under layoutlib. */
    private val activityResultOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry =
            object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) = Unit
            }
    }

    private fun content(state: WeatherUiState, darkTheme: Boolean = false) {
        paparazzi.snapshot {
            // Default typography uses downloadable Google Fonts, which spawns an Android-only
            // fetcher thread that crashes under layoutlib; system fonts render deterministically.
            // Inspection mode keeps MapLibre off the snapshot path (native libs are unavailable).
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalActivityResultRegistryOwner provides activityResultOwner
            ) {
                WeatherRecommenderTheme(darkTheme = darkTheme, typography = Typography()) {
                    WeatherScreenContent(
                        uiState = state,
                        onQueryChanged = {},
                        onLocationSelected = {},
                        onDaySelected = {},
                        onBack = {},
                        onRefresh = {}
                    )
                }
            }
        }
    }

    private val lisbon = Location(
        id = -4, name = "Lisbon", latitude = 38.7, longitude = -9.1,
        country = "Portugal", admin1 = "Lisbon", elevation = 68.0, hasSeaAccess = true
    )

    private val topPicks = listOf(
        TopPick(
            location = lisbon,
            topActivity = RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(90, 8)),
            weatherCode = 0,
            maxTemp = 27.0
        ),
        TopPick(
            location = Location(9, "Zermatt", 46.0, 7.7, "Switzerland", null, elevation = 1608.0),
            topActivity = RankedActivity(RecommendedActivity.SKIING, 92, ReasonKey.SKI_IDEAL, listOf(-4, 12)),
            weatherCode = 71,
            maxTemp = -3.0
        )
    )

    private val detailState = WeatherUiState(
        selectedLocation = lisbon,
        forecast = WeatherForecast(
            location = lisbon,
            dailyForecasts = listOf(
                DailyForecast("2026-07-16", 0, 27.0, 19.0, 0.0, 0.0, 10.0, 1.0),
                DailyForecast("2026-07-17", 61, 22.0, 16.0, 8.0, 0.0, 14.0, 0.8),
                DailyForecast("2026-07-18", 3, 24.0, 17.0, 0.0, 0.0, 12.0, 0.9),
                DailyForecast("2026-07-19", 71, 2.0, -3.0, 0.0, 5.0, 8.0, 0.5),
                DailyForecast("2026-07-20", 95, 18.0, 14.0, 12.0, 0.0, 20.0, 1.2)
            )
        ),
        selectedDayIndex = 0,
        rankedActivities = listOf(
            RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(100, 10)),
            RankedActivity(RecommendedActivity.OUTDOOR_SIGHTSEEING, 80, ReasonKey.OUTDOOR_MILD, listOf(23)),
            RankedActivity(RecommendedActivity.INDOOR_SIGHTSEEING, 45, ReasonKey.INDOOR_BAD_WEATHER)
        )
    )

    @Test
    fun homeEmpty() {
        content(WeatherUiState(isLoadingTopPicks = false))
    }

    @Test
    fun homeWithTopPicks() {
        content(WeatherUiState(topPicks = topPicks))
    }

    @Test
    fun homeWithCurrentLocationChip() {
        content(
            WeatherUiState(
                topPicks = topPicks,
                deviceLocation = lisbon
            )
        )
    }

    @Test
    fun homeWithTopPicksAndHistory() {
        content(
            WeatherUiState(
                topPicks = topPicks,
                recentHistory = listOf(
                    lisbon,
                    Location(1, "London", 51.5, -0.1, "UK", "England"),
                    Location(-2, "Sydney", -33.8, 151.2, "Australia", null, hasSeaAccess = true)
                )
            )
        )
    }

    @Test
    fun homeWithTopPicksDark() {
        content(WeatherUiState(topPicks = topPicks), darkTheme = true)
    }

    @Test
    fun homeSearching() {
        val locations = listOf(
            Location(1, "London", 51.5, -0.1, "UK", "England"),
            Location(2, "Paris", 48.8, 2.3, "France", "Ile-de-France")
        )
        content(WeatherUiState(query = "Lon", searchResults = locations))
    }

    @Test
    fun detailLoaded() {
        content(detailState)
    }

    @Test
    fun detailLoadedDark() {
        content(detailState, darkTheme = true)
    }
}
