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

    @Test
    fun `clear sky maps to clear category`() {
        assertEquals(WeatherUiCategory.CLEAR, weatherUiCategory(0))
    }

    @Test
    fun `partly cloudy and fog map to cloudy category`() {
        assertEquals(WeatherUiCategory.CLOUDY, weatherUiCategory(2))
        assertEquals(WeatherUiCategory.CLOUDY, weatherUiCategory(45))
    }

    @Test
    fun `rain maps to rain category`() {
        assertEquals(WeatherUiCategory.RAIN, weatherUiCategory(61))
    }

    @Test
    fun `snow maps to snow category`() {
        assertEquals(WeatherUiCategory.SNOW, weatherUiCategory(71))
    }

    @Test
    fun `thunderstorm maps to thunderstorm category`() {
        assertEquals(WeatherUiCategory.THUNDERSTORM, weatherUiCategory(95))
    }

    @Test
    fun `unknown code maps to cloudy category`() {
        assertEquals(WeatherUiCategory.CLOUDY, weatherUiCategory(999))
    }
}
