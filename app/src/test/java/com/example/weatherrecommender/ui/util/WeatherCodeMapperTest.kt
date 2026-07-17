package com.example.weatherrecommender.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodeMapperTest {

    @Test
    fun `clear sky maps to sunny icon`() {
        assertEquals(Icons.Outlined.WbSunny, weatherCodeIcon(0))
    }

    @Test
    fun `partly cloudy maps to cloud queue icon`() {
        assertEquals(Icons.Outlined.CloudQueue, weatherCodeIcon(2))
    }

    @Test
    fun `rain maps to water drop icon`() {
        assertEquals(Icons.Outlined.WaterDrop, weatherCodeIcon(61))
    }

    @Test
    fun `snow maps to snow icon`() {
        assertEquals(Icons.Outlined.AcUnit, weatherCodeIcon(71))
    }

    @Test
    fun `thunderstorm maps to thunderstorm icon`() {
        assertEquals(Icons.Outlined.Thunderstorm, weatherCodeIcon(95))
    }

    @Test
    fun `unknown code maps to cloud icon`() {
        assertEquals(Icons.Outlined.Cloud, weatherCodeIcon(999))
    }
}
