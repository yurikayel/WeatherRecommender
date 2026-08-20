package com.example.weatherrecommender.data.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePreferencesMappingTest {

    private val modeKey = stringPreferencesKey("theme_mode")
    private val sourceKey = stringPreferencesKey("theme_source")

    @Test
    fun storageValue_roundTripsEveryMode() {
        assertEquals("cycle", ThemeMode.CYCLE.storageValue())
        assertEquals("light", ThemeMode.LIGHT.storageValue())
        assertEquals("dark", ThemeMode.DARK.storageValue())
    }

    @Test
    fun storageValue_roundTripsEverySource() {
        assertEquals("clock", ThemeSource.CLOCK.storageValue())
        assertEquals("gps", ThemeSource.GPS.storageValue())
        assertEquals("user", ThemeSource.USER.storageValue())
    }

    @Test
    fun storedThemeMode_readsKnownTokensAndMapsLegacySystemToCycle() {
        assertEquals(ThemeMode.LIGHT, mutablePreferencesOf(modeKey to "light").storedThemeMode())
        assertEquals(ThemeMode.DARK, mutablePreferencesOf(modeKey to "dark").storedThemeMode())
        assertEquals(ThemeMode.CYCLE, mutablePreferencesOf(modeKey to "cycle").storedThemeMode())
        assertEquals(ThemeMode.CYCLE, mutablePreferencesOf(modeKey to "system").storedThemeMode())
        assertNull(mutablePreferencesOf(modeKey to "midnight").storedThemeMode())
        assertNull(mutablePreferencesOf().storedThemeMode())
    }

    @Test
    fun storedThemeSource_readsKnownTokens() {
        assertEquals(ThemeSource.CLOCK, prefs("cycle", "clock").storedThemeSource())
        assertEquals(ThemeSource.GPS, prefs("dark", "gps").storedThemeSource())
        assertEquals(ThemeSource.USER, prefs("light", "user").storedThemeSource())
    }

    @Test
    fun storedThemeSource_legacyModeWithoutSourceIsUser() {
        assertEquals(ThemeSource.USER, mutablePreferencesOf(modeKey to "dark").storedThemeSource())
        assertNull(mutablePreferencesOf().storedThemeSource())
    }

    private fun prefs(mode: String, source: String) =
        mutablePreferencesOf(modeKey to mode, sourceKey to source)
}
