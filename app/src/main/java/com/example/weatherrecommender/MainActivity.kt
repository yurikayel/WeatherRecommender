package com.example.weatherrecommender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.weatherrecommender.ui.WeatherScreen
import com.example.weatherrecommender.ui.WeatherViewModel
import com.example.weatherrecommender.theme.WeatherRecommenderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The main entry point of the application.
 * Sets up the Compose UI and injects the ViewModel using Hilt.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherRecommenderTheme {
                val viewModel: WeatherViewModel = hiltViewModel()
                WeatherScreen(viewModel = viewModel)
            }
        }
    }
}
