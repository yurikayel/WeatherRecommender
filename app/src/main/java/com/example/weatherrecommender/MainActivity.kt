package com.example.weatherrecommender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val storedMode by themePreferences.themeMode.collectAsStateWithLifecycle(
                initialValue = null
            )
            val clockNight = remember {
                SolarNight.isNightByLocalClock(ZonedDateTime.now())
            }
            val darkTheme = storedMode.resolveRenderedDarkTheme(
                systemInDarkTheme = isSystemInDarkTheme(),
                unsetIsNight = clockNight
            )
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val coords = if (deviceLocationProvider.hasLocationPermission()) {
                    deviceLocationProvider.getLastKnownLocation()
                } else {
                    null
                }
                if (coords != null) {
                    firstRunThemeSettler.settle(coords)
                } else {
                    delay(FIRST_RUN_CLOCK_FALLBACK_MS)
                    firstRunThemeSettler.settle(coordinates = null)
                }
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
    }
}

private const val FIRST_RUN_CLOCK_FALLBACK_MS = 3_000L
