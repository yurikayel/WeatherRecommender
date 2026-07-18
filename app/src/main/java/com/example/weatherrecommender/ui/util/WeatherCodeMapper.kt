package com.example.weatherrecommender.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.weatherrecommender.R

/**
 * Maps Open-Meteo WMO weather interpretation codes to a UI category.
 * @see <a href="https://open-meteo.com/en/docs">Open-Meteo WMO codes</a>
 */
fun weatherUiCategory(weatherCode: Int): WeatherUiCategory = when (weatherCode) {
    0 -> WeatherUiCategory.CLEAR
    1, 2, 3, 45, 48 -> WeatherUiCategory.CLOUDY
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherUiCategory.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherUiCategory.SNOW
    95, 96, 99 -> WeatherUiCategory.THUNDERSTORM
    else -> WeatherUiCategory.CLOUDY
}

/**
 * Maps Open-Meteo WMO weather interpretation codes to Material icons.
 * @see <a href="https://open-meteo.com/en/docs">Open-Meteo WMO codes</a>
 */
fun weatherCodeIcon(weatherCode: Int): ImageVector = when (weatherCode) {
    0 -> Icons.Outlined.WbSunny
    1, 2, 3 -> Icons.Outlined.CloudQueue
    45, 48 -> Icons.Outlined.Cloud
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Outlined.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Outlined.AcUnit
    95, 96, 99 -> Icons.Outlined.Thunderstorm
    else -> Icons.Outlined.Cloud
}

/** Localized accessibility label for a WMO weather code. */
@Composable
fun weatherCodeDescription(weatherCode: Int): String = stringResource(
    when (weatherCode) {
        0 -> R.string.weather_clear
        1, 2, 3 -> R.string.weather_partly_cloudy
        45, 48 -> R.string.weather_fog
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.string.weather_rain
        71, 73, 75, 77, 85, 86 -> R.string.weather_snow
        95, 96, 99 -> R.string.weather_thunderstorm
        else -> R.string.weather_cloudy
    }
)
