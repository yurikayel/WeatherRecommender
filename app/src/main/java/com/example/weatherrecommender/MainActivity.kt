package com.example.weatherrecommender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherrecommender.data.preferences.FirstRunThemeSettler
import com.example.weatherrecommender.data.preferences.ThemeMode
import com.example.weatherrecommender.data.preferences.ThemePreferences
import com.example.weatherrecommender.data.preferences.resolveRenderedDarkTheme
import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.theme.WeatherRecommenderTheme
import com.example.weatherrecommender.ui.LocalThemeMode
import com.example.weatherrecommender.ui.WeatherScreen
import com.example.weatherrecommender.ui.WeatherViewModel
import com.example.weatherrecommender.ui.rememberCycleIsNight
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
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
 * Resolves Cycle vs locked Light/Dark, persists Cycle on first launch, and hosts [WeatherScreen].
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
    val isNight = rememberCycleIsNight(deviceLocationProvider)
    val darkTheme = storedMode.resolveRenderedDarkTheme(isNight)
    val scope = rememberCoroutineScope()
    val displayMode = storedMode ?: ThemeMode.CYCLE

    LaunchedEffect(Unit) {
        firstRunThemeSettler.settle()
    }

    CompositionLocalProvider(LocalThemeMode provides displayMode) {
        WeatherRecommenderTheme(darkTheme = darkTheme) {
            val viewModel: WeatherViewModel = hiltViewModel()
            WeatherScreen(
                viewModel = viewModel,
                isDarkTheme = darkTheme,
                onToggleTheme = {
                    scope.launch { themePreferences.advanceThemeMode(storedMode) }
                }
            )
        }
    }
}
