package com.example.weatherrecommender.ui

import android.Manifest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.weatherrecommender.data.preferences.ThemeMode
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

    private var openedUri: String? = null

    private val capturingUriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            openedUri = uri
        }
    }

    private fun setContent(
        state: WeatherUiState,
        darkTheme: Boolean = false,
        themeMode: ThemeMode = ThemeMode.CYCLE,
        onQueryChanged: (String) -> Unit = {},
        onLocationSelected: (Location) -> Unit = {},
        onDaySelected: (Int) -> Unit = {},
        onBack: () -> Unit = {},
        onCurrentLocationClick: () -> Unit = {}
    ) {
        openedUri = null
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides capturingUriHandler,
                LocalThemeMode provides themeMode
            ) {
                WeatherRecommenderTheme(darkTheme = darkTheme) {
                    WeatherScreenContent(
                        uiState = state,
                        onQueryChanged = onQueryChanged,
                        onLocationSelected = onLocationSelected,
                        onDaySelected = onDaySelected,
                        onBack = onBack,
                        onRefresh = {},
                        onCurrentLocationClick = onCurrentLocationClick,
                        isDarkTheme = darkTheme
                    )
                }
            }
        }
    }

    /** Home peek clips content below ~40%; expand like a user so assertIsDisplayed can pass. */
    private fun expandHomeSheet() {
        val handle = composeTestRule.onNodeWithContentDescription("Drag handle")
        val expanded = runCatching {
            handle.performSemanticsAction(SemanticsActions.Expand)
        }.isSuccess
        if (!expanded) {
            handle.performTouchInput {
                swipe(
                    start = center,
                    end = Offset(center.x, center.y - 1600f),
                    durationMillis = 500
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** Detail is locked at 60%; ranked rows share the leftover space and stay on screen. */
    private fun assertDisplayedInDetailSheet(text: String) {
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    // --- Home ---

    @Test
    fun home_showsSearchBarAndTopPicks() {
        setContent(WeatherUiState())

        composeTestRule.onAllNodesWithText("Plan your day").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Switch to light mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("CYCLE").assertIsDisplayed()
        // Search lives in the fixed sheet header — not inside the scrollable body.
        composeTestRule.onNodeWithText("Search a city…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Top Picks").assertIsDisplayed()
    }

    @Test
    fun themeToggle_lightMode_showsSwitchToDarkDescription() {
        setContent(WeatherUiState(), themeMode = ThemeMode.LIGHT)
        composeTestRule.onNodeWithContentDescription("Switch to dark mode").assertIsDisplayed()
    }

    @Test
    fun themeToggle_darkMode_showsSwitchToCycleDescription() {
        setContent(WeatherUiState(), darkTheme = true, themeMode = ThemeMode.DARK)
        composeTestRule.onNodeWithContentDescription("Switch to cycle mode").assertIsDisplayed()
    }

    @Test
    fun themeToggle_cycleMode_showsSwitchToLightDescriptionAndLabel() {
        setContent(WeatherUiState(), themeMode = ThemeMode.CYCLE)
        composeTestRule.onNodeWithContentDescription("Switch to light mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("CYCLE").assertIsDisplayed()
    }

    @Test
    fun home_withDeviceLocation_staysOnHome_andShowsChip() {
        setContent(
            WeatherUiState(
                deviceLocation = lisbon,
                topPicks = emptyList()
            )
        )

        // GPS resolves the chip only — it must not auto-open detail.
        composeTestRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeTestRule.onNodeWithText("Search a city…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current location · Lisbon").performScrollTo().assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Search a city…").performTextInput("Lis")

        assertEquals("Lis", lastQuery)
    }

    @Test
    fun home_clearButton_shownWhenQueryPresent_andClears() {
        var lastQuery = "unchanged"
        setContent(WeatherUiState(query = "Lon"), onQueryChanged = { lastQuery = it })

        composeTestRule.onNodeWithContentDescription("Clear search").performClick()

        assertEquals("", lastQuery)
    }

    @Test
    fun home_emptyTopPicks_showsOfflineHint() {
        setContent(WeatherUiState(topPicks = emptyList()))

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
        composeTestRule.onNodeWithText("Lisbon, Portugal").performScrollTo().assertIsDisplayed()
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
                recentHistory = listOf(london, lisbon)
            )
        )

        composeTestRule.onNodeWithText("Recent").performClick()
        composeTestRule.onNodeWithText("Recent").assertIsDisplayed()
        expandHomeSheet()
        composeTestRule.onNodeWithText("London").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Lisbon").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun home_error_isDisplayed() {
        setContent(
            WeatherUiState(
                search = SearchUiState.Failed(UiText.DynamicString("City not found"))
            )
        )

        composeTestRule.onNodeWithText("Error: City not found").performScrollTo().assertIsDisplayed()
    }

    // --- Search results ---

    @Test
    fun search_results_areDisplayed() {
        val locations = listOf(
            london,
            Location(3, "Paris", 48.8, 2.3, "France", "Ile-de-France")
        )
        setContent(WeatherUiState(search = SearchUiState.Results(locations), query = "Lon"))

        composeTestRule.onNodeWithText("📍 London, England, UK").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("📍 Paris, Ile-de-France, France").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun search_tappingResult_invokesLocationSelected() {
        var selected: Location? = null
        setContent(
            WeatherUiState(search = SearchUiState.Results(listOf(london)), query = "Lon"),
            onLocationSelected = { selected = it }
        )

        composeTestRule.onNodeWithText("📍 London, England, UK").performScrollTo().performClick()

        assertEquals(london, selected)
    }

    // --- Detail ---

    @Test
    fun detail_showsDayButtonsWithoutWeekHeading() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                selectedDayIndex = 0,
                rankedActivities = listOf(outdoorActivity)
            )
        )

        composeTestRule.onAllNodesWithText("7-Day").assertCountEquals(0)
        composeTestRule.onNodeWithTag("week_summary_day_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("week_summary_day_1").assertIsDisplayed()
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
        composeTestRule.onNodeWithTag("week_summary_day_0").assertIsDisplayed()
        assertDisplayedInDetailSheet("Outdoor")
        assertDisplayedInDetailSheet("95")
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
                forecastFetch = FetchStatus.Loading
            )
        )

        composeTestRule.onAllNodesWithContentDescription("Share weather").assertCountEquals(0)
    }

    @Test
    fun detail_wikipediaButton_opensArticleUrl() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(london),
                forecast = twoDayForecast,
                rankedActivities = listOf(outdoorActivity)
            )
        )

        composeTestRule.onNodeWithContentDescription("Open Wikipedia").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open Wikipedia").performClick()
        assertEquals("https://en.wikipedia.org/wiki/London", openedUri)
    }

    @Test
    fun detail_wikipediaButton_usesCityTitle() {
        setContent(
            WeatherUiState(
                destination = WeatherDestination.Detail(lisbon),
                forecast = twoDayForecast.copy(location = lisbon),
                rankedActivities = listOf(outdoorActivity)
            )
        )

        composeTestRule.onNodeWithContentDescription("Open Wikipedia").performClick()
        assertEquals("https://en.wikipedia.org/wiki/Lisbon", openedUri)
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
                forecastFetch = FetchStatus.Failed(
                    UiText.DynamicString("No internet connection. Showing offline data.")
                )
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
                forecastFetch = FetchStatus.Failed(UiText.DynamicString("Server error"))
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
        assertDisplayedInDetailSheet("Outdoor")
    }
}
