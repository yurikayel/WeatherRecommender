package com.example.weatherrecommender.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

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
