package com.example.weatherrecommender.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.WeatherForecast
import org.junit.Rule
import org.junit.Test

class WeatherScreenSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun defaultState() {
        paparazzi.snapshot {
            WeatherScreenContent(
                uiState = WeatherUiState(),
                onQueryChanged = {},
                onLocationSelected = {},
                onRefresh = {}
            )
        }
    }

    @Test
    fun searchingState() {
        val locations = listOf(
            Location(id = 1, name = "London", latitude = 51.5, longitude = -0.1, country = "UK", admin1 = "England"),
            Location(id = 2, name = "Paris", latitude = 48.8, longitude = 2.3, country = "France", admin1 = "Ile-de-France")
        )
        paparazzi.snapshot {
            WeatherScreenContent(
                uiState = WeatherUiState(query = "Lon", isSearching = false, searchResults = locations),
                onQueryChanged = {},
                onLocationSelected = {},
                onRefresh = {}
            )
        }
    }

    @Test
    fun loadedState() {
        val location = Location(id = 1, name = "London", latitude = 51.5, longitude = -0.1, country = "UK", admin1 = "England")
        val forecast = WeatherForecast(
            location = location,
            dailyForecasts = listOf(
                DailyForecast("2026-07-16", weatherCode = 1, 25.0, 15.0, 0.0, 0.0, 10.0),
                DailyForecast("2026-07-17", weatherCode = 2, 22.0, 14.0, 2.0, 0.0, 12.0)
            )
        )
        val activities = listOf(
            RankedActivity(com.example.weatherrecommender.domain.model.RecommendedActivity.OUTDOOR_SIGHTSEEING, 95, com.example.weatherrecommender.domain.model.ReasonKey.OUTDOOR_MILD, listOf(22)),
            RankedActivity(com.example.weatherrecommender.domain.model.RecommendedActivity.INDOOR_SIGHTSEEING, 60, com.example.weatherrecommender.domain.model.ReasonKey.INDOOR_BAD_WEATHER, emptyList())
        )
        paparazzi.snapshot {
            WeatherScreenContent(
                uiState = WeatherUiState(forecast = forecast, rankedActivities = activities),
                onQueryChanged = {},
                onLocationSelected = {},
                onRefresh = {}
            )
        }
    }
}
