package com.example.weatherrecommender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherrecommender.data.preferences.ThemeMode
import com.example.weatherrecommender.data.preferences.ThemePreferences
import com.example.weatherrecommender.data.preferences.resolveDarkTheme
import com.example.weatherrecommender.theme.WeatherRecommenderTheme
import com.example.weatherrecommender.ui.WeatherScreen
import com.example.weatherrecommender.ui.WeatherViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())
            val scope = rememberCoroutineScope()

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
