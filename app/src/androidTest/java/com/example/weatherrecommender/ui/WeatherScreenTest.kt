package com.example.weatherrecommender.ui

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.TopPick
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.theme.WeatherRecommenderTheme
import com.example.weatherrecommender.ui.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * WeatherScreenContent auto-requests location permission on first composition; without a
     * prior grant the system dialog covers the compose host and every subsequent test fails
     * with "No compose hierarchies found".
     */
    @get:Rule
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val london = Location(1, "London", 51.5, -0.1, "UK", "England", elevation = 25.0)
    private val lisbon = Location(
        id = -4, name = "Lisbon", latitude = 38.7, longitude = -9.1,
        country = "Portugal", admin1 = "Lisbon", elevation = 68.0, hasSeaAccess = true
    )

    private val twoDayForecast = WeatherForecast(
        location = london,
        dailyForecasts = listOf(
            DailyForecast("2026-07-16", 0, 25.0, 15.0, 0.0, 0.0, 10.0),
            DailyForecast("2026-07-17", 61, 14.0, 9.0, 20.0, 0.0, 12.0)
        )
    )

    private val outdoorActivity = RankedActivity(
        activity = RecommendedActivity.OUTDOOR_SIGHTSEEING,
        score = 95,
        reasonKey = ReasonKey.OUTDOOR_MILD,
        reasonArgs = listOf(22)
    )

    private fun setContent(
        state: WeatherUiState,
        darkTheme: Boolean = false,
        onQueryChanged: (String) -> Unit = {},
        onLocationSelected: (Location) -> Unit = {},
        onDaySelected: (Int) -> Unit = {},
        onBack: () -> Unit = {},
        onCurrentLocationClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            WeatherRecommenderTheme(darkTheme = darkTheme) {
                WeatherScreenContent(
                    uiState = state,
                    onQueryChanged = onQueryChanged,
                    onLocationSelected = onLocationSelected,
                    onDaySelected = onDaySelected,
                    onBack = onBack,
                    onRefresh = {},
                    onCurrentLocationClick = onCurrentLocationClick
                )
            }
        }
    }

    // --- Home ---

    @Test
    fun home_showsSearchBarAndTopPicks() {
        setContent(WeatherUiState())

        // Title lives in the fixed sheet header below the map — not inside the scrollable body.
        composeTestRule.onNodeWithText("Concierge Weather").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Plan your day").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Switch to dark mode").assertIsDisplayed()
        // Square map leaves little sheet viewport — scroll before asserting body content.
        composeTestRule.onNodeWithText("Search a city…").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Top picks for you").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun home_withDeviceLocation_staysOnHome_andShowsChip() {
        setContent(
            WeatherUiState(
                deviceLocation = lisbon,
                topPicks = emptyList(),
                isLoadingTopPicks = false
            )
        )

        // GPS resolves the chip only — it must not auto-open detail.
        composeTestRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        // Device location name is shown in the fixed sheet header (homeSheetTitle), not scrollable body.
        composeTestRule.onNodeWithText("Lisbon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current location · Lisbon").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Search a city…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun home_tappingCurrentLocationChip_invokesCallback() {
        var clicked = false
        setContent(
            WeatherUiState(deviceLocation = lisbon),
            onCurrentLocationClick = { clicked = true }
        )

        composeTestRule
            .onNodeWithContentDescription("Check weather for your current location, Lisbon")
            .performScrollTo()
            .performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun home_typingInSearchBar_invokesQueryCallback() {
        var lastQuery = ""
        setContent(WeatherUiState(), onQueryChanged = { lastQuery = it })

        composeTestRule.onNodeWithText("Search a city…").performScrollTo().performTextInput("Lis")

        assertEquals("Lis", lastQuery)
    }

    @Test
    fun home_clearButton_shownWhenQueryPresent_andClears() {
        var lastQuery = "unchanged"
        setContent(WeatherUiState(query = "Lon"), onQueryChanged = { lastQuery = it })

        composeTestRule.onNodeWithContentDescription("Clear search").performScrollTo().performClick()

        assertEquals("", lastQuery)
    }

    @Test
    fun home_emptyTopPicks_showsOfflineHint() {
        setContent(WeatherUiState(topPicks = emptyList(), isLoadingTopPicks = false))

        composeTestRule
            .onNodeWithText("Connect to the internet to see today's suggestions.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun home_topPicks_displayCityAndBestActivity() {
        val picks = listOf(
            TopPick(
                location = lisbon,
                topActivity = RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(90, 8)),
                weatherCode = 0,
                maxTemp = 27.0
            )
        )
        setContent(WeatherUiState(topPicks = picks))

        composeTestRule.onNodeWithText("Lisbon").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Portugal").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Surfing").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("27°").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun home_tappingTopPick_invokesLocationSelected() {
        var selected: Location? = null
        val picks = listOf(
            TopPick(
                location = lisbon,
                topActivity = RankedActivity(RecommendedActivity.SURFING, 88, ReasonKey.SURF_IDEAL, listOf(90, 8)),
                weatherCode = 0,
                maxTemp = 27.0
            )
        )
        setContent(WeatherUiState(topPicks = picks), onLocationSelected = { selected = it })

        composeTestRule.onNodeWithText("Lisbon").performScrollTo().performClick()

        assertEquals(lisbon, selected)
    }

    @Test
    fun home_history_isDisplayed() {
        setContent(
            WeatherUiState(
                topPicks = emptyList(),
                isLoadingTopPicks = false,
                recentHistory = listOf(london, lisbon)
            )
        )

        composeTestRule.onNodeWithText("Recently viewed").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("London").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Lisbon").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun home_error_isDisplayed() {
        setContent(WeatherUiState(error = UiText.DynamicString("City not found")))

        composeTestRule.onNodeWithText("Error: City not found").performScrollTo().assertIsDisplayed()
    }

    // --- Search results ---

    @Test
    fun search_results_areDisplayed() {
        val locations = listOf(
            london,
            Location(3, "Paris", 48.8, 2.3, "France", "Ile-de-France")
        )
        setContent(WeatherUiState(searchResults = locations, query = "Lon"))

        composeTestRule.onNodeWithText("📍 London, England, UK").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("📍 Paris, Ile-de-France, France").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun search_tappingResult_invokesLocationSelected() {
        var selected: Location? = null
        setContent(
            WeatherUiState(searchResults = listOf(london), query = "Lon"),
            onLocationSelected = { selected = it }
        )

        composeTestRule.onNodeWithText("📍 London, England, UK").performScrollTo().performClick()

        assertEquals(london, selected)
    }

    // --- Detail ---

    @Test
    fun detail_showsWeekSummaryHeading() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                selectedDayIndex = 0,
                rankedActivities = listOf(outdoorActivity),
                weekTopActivities = listOf(outdoorActivity, outdoorActivity)
            )
        )

        composeTestRule.onNodeWithText("7-Day Forecast").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_showsCityTitleDaySelectorAndActivities() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                selectedDayIndex = 0,
                rankedActivities = listOf(outdoorActivity)
            )
        )

        composeTestRule.onNodeWithText("London").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pick a day").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Outdoor Sightseeing").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("95").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_showsShareActionWhenForecastLoaded() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                selectedDayIndex = 0,
                rankedActivities = listOf(outdoorActivity)
            )
        )

        composeTestRule.onNodeWithContentDescription("Share weather").assertIsDisplayed()
    }

    @Test
    fun detail_hidesShareActionWhileForecastLoading() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = null,
                isLoadingForecast = true
            )
        )

        composeTestRule.onAllNodesWithContentDescription("Share weather").assertCountEquals(0)
    }

    @Test
    fun detail_showsGeographyChips() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(lisbon),
                forecast = twoDayForecast.copy(location = lisbon),
                rankedActivities = listOf(outdoorActivity)
            )
        )

        composeTestRule.onNodeWithText("Coastal").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("68 m elevation").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_inlandCity_showsInlandChip() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                rankedActivities = listOf(outdoorActivity)
            )
        )

        composeTestRule.onNodeWithText("Inland").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_tappingDay_invokesDaySelectedWithIndex() {
        var selectedDay = -1
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                selectedDayIndex = 0,
                rankedActivities = listOf(outdoorActivity)
            ),
            onDaySelected = { selectedDay = it }
        )

        // Invoke OnClick via semantics so the collapsing map/sheet header cannot steal the touch.
        composeTestRule.onNodeWithTag("week_summary_day_1")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, selectedDay)

        selectedDay = -1
        composeTestRule.onNodeWithTag("day_chip_1")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, selectedDay)
    }

    @Test
    fun detail_backButton_invokesOnBack() {
        var backCalled = false
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                rankedActivities = listOf(outdoorActivity)
            ),
            onBack = { backCalled = true }
        )

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(true, backCalled)
    }

    @Test
    fun detail_syncError_showsBanner() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                rankedActivities = listOf(outdoorActivity),
                syncError = UiText.DynamicString("No internet connection. Showing offline data.")
            )
        )

        composeTestRule
            .onNodeWithText(
                "⚠️ Offline Mode: No internet connection. Showing offline data.",
                substring = true
            )
            .assertIsDisplayed()
    }

    @Test
    fun detail_blockingError_isDisplayed() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                isLoadingForecast = false,
                error = UiText.DynamicString("Server error")
            )
        )

        composeTestRule.onNodeWithText("Error: Server error").assertIsDisplayed()
    }

    // --- Dark mode smoke ---

    @Test
    fun darkTheme_rendersHomeAndDetail() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                rankedActivities = listOf(outdoorActivity)
            ),
            darkTheme = true
        )

        composeTestRule.onNodeWithText("London").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outdoor Sightseeing").performScrollTo().assertIsDisplayed()
    }
}
