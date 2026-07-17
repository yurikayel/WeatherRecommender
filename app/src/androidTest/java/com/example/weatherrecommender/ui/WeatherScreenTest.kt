package com.example.weatherrecommender.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.WeatherForecast
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class WeatherScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchBar_isDisplayed() {
        composeTestRule.setContent {
            WeatherScreenContent(
                uiState = WeatherUiState(),
                onQueryChanged = {},
                onLocationSelected = {},
                onRefresh = {}
            )
        }

        composeTestRule.onNodeWithText("Search a city...").assertIsDisplayed()
    }

    @Test
    fun searchResults_areDisplayed() {
        val locations = listOf(
            Location(id = 1, name = "London", latitude = 51.5, longitude = -0.1, country = "UK", admin1 = "England"),
            Location(id = 2, name = "Paris", latitude = 48.8, longitude = 2.3, country = "France", admin1 = "Ile-de-France")
        )

        composeTestRule.setContent {
            WeatherScreenContent(
                uiState = WeatherUiState(searchResults = locations, query = "Lon"),
                onQueryChanged = {},
                onLocationSelected = {},
                onRefresh = {}
            )
        }

        composeTestRule.onNodeWithText("📍 London, England, UK").assertIsDisplayed()
        composeTestRule.onNodeWithText("📍 Paris, Ile-de-France, France").assertIsDisplayed()
    }

    @Test
    fun forecast_andActivities_areDisplayed() {
        val location = Location(id = 1, name = "London", latitude = 51.5, longitude = -0.1, country = "UK", admin1 = "England")
        val daily = DailyForecast(
            date = "2026-07-16",
            weatherCode = 0,
            maxTemp = 25.0,
            minTemp = 15.0,
            precipitationSum = 0.0,
            snowfallSum = 0.0,
            maxWindSpeed = 10.0
        )
        val forecast = WeatherForecast(location = location, dailyForecasts = listOf(daily))
        val activity = RankedActivity(
            activity = RecommendedActivity.OUTDOOR_SIGHTSEEING,
            score = 95,
            reasonKey = ReasonKey.OUTDOOR_MILD,
            reasonArgs = listOf(22)
        )

        composeTestRule.setContent {
            WeatherScreenContent(
                uiState = WeatherUiState(
                    forecast = forecast,
                    rankedActivities = listOf(activity)
                ),
                onQueryChanged = {},
                onLocationSelected = {},
                onRefresh = {}
            )
        }

        composeTestRule.onNodeWithText("London").assertIsDisplayed()
        composeTestRule.onNodeWithText("7-Day Forecast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outdoor Sightseeing").assertIsDisplayed()
    }
}
