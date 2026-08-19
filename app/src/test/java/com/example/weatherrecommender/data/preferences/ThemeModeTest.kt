package com.example.weatherrecommender.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun unset_usesFirstRunNightFallback() {
        val unset: ThemeMode? = null
        assertTrue(unset.resolveRenderedDarkTheme(systemInDarkTheme = false, unsetIsNight = true))
        assertFalse(unset.resolveRenderedDarkTheme(systemInDarkTheme = true, unsetIsNight = false))
    }

    @Test
    fun firstRun_nightWritesDark_dayWritesLight() {
        assertEquals(ThemeMode.DARK, firstRunThemeMode(existing = null, isNight = true))
        assertEquals(ThemeMode.LIGHT, firstRunThemeMode(existing = null, isNight = false))
    }

    @Test
    fun firstRun_doesNotOverwriteStoredPreference() {
        assertNull(firstRunThemeMode(existing = ThemeMode.LIGHT, isNight = true))
        assertNull(firstRunThemeMode(existing = ThemeMode.DARK, isNight = false))
        assertNull(firstRunThemeMode(existing = ThemeMode.SYSTEM, isNight = true))
    }
}
