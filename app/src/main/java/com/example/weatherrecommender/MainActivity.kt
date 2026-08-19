package com.example.weatherrecommender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherrecommender.data.preferences.FirstRunThemeSettler
import com.example.weatherrecommender.data.preferences.ThemePreferences
import com.example.weatherrecommender.data.preferences.resolveRenderedDarkTheme
import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.domain.util.SolarNight
import com.example.weatherrecommender.theme.WeatherRecommenderTheme
import com.example.weatherrecommender.ui.WeatherScreen
import com.example.weatherrecommender.ui.WeatherViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The main entry point of the application.
 * Sets up the Compose UI and injects the ViewModel using Hilt.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var firstRunThemeSettler: FirstRunThemeSettler

    @Inject
    lateinit var deviceLocationProvider: DeviceLocationProvider

    /** Installs splash, edge-to-edge, then hosts [WeatherAppRoot]. */
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherAppRoot(
                themePreferences = themePreferences,
                firstRunThemeSettler = firstRunThemeSettler,
                deviceLocationProvider = deviceLocationProvider
            )
        }
    }
}

/**
 * Resolves first-run vs stored theme, settles day/night from GPS when possible,
 * and hosts [WeatherScreen].
 */
@Composable
private fun WeatherAppRoot(
    themePreferences: ThemePreferences,
    firstRunThemeSettler: FirstRunThemeSettler,
    deviceLocationProvider: DeviceLocationProvider
) {
    val storedMode by themePreferences.themeMode.collectAsStateWithLifecycle(
        initialValue = null
    )
    val clockNight = remember {
        SolarNight.isNightByLocalClock(ZonedDateTime.now(Clock.systemDefaultZone()))
    }
    val darkTheme = storedMode.resolveRenderedDarkTheme(
        systemInDarkTheme = isSystemInDarkTheme(),
        unsetIsNight = clockNight
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settleFirstRunTheme(deviceLocationProvider, firstRunThemeSettler)
    }

    WeatherRecommenderTheme(darkTheme = darkTheme) {
        val viewModel: WeatherViewModel = hiltViewModel()
        WeatherScreen(
            viewModel = viewModel,
            isDarkTheme = darkTheme,
            onToggleTheme = {
                scope.launch { themePreferences.toggle(currentlyDark = darkTheme) }
            }
        )
    }
}

/**
 * Persists Light/Dark from sunrise/sunset at the last-known fix, or from the local clock
 * after [FIRST_RUN_CLOCK_FALLBACK_MS] if GPS is unavailable.
 */
private suspend fun settleFirstRunTheme(
    deviceLocationProvider: DeviceLocationProvider,
    firstRunThemeSettler: FirstRunThemeSettler
) {
    val coords = if (deviceLocationProvider.hasLocationPermission()) {
        deviceLocationProvider.getLastKnownLocation()
    } else {
        null
    }
    if (coords != null) {
        firstRunThemeSettler.settle(coords)
        return
    }
    delay(FIRST_RUN_CLOCK_FALLBACK_MS)
    firstRunThemeSettler.settle(coordinates = null)
}

private const val FIRST_RUN_CLOCK_FALLBACK_MS = 3_000L
