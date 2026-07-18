package com.example.weatherrecommender.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun system_followsSystemSetting() {
        assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(systemInDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.resolveDarkTheme(systemInDarkTheme = false))
    }

    @Test
    fun light_and_dark_ignoreSystem() {
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemInDarkTheme = true))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(systemInDarkTheme = false))
    }
}
