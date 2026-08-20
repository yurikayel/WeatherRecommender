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
    fun firstRun_nightWritesDarkFromClock_dayWritesLight() {
        val night = firstRunThemeWrite(null, null, hasCoordinates = false, isNight = true)
        assertEquals(ThemeMode.DARK, night?.mode)
        assertEquals(ThemeSource.CLOCK, night?.source)

        val day = firstRunThemeWrite(null, null, hasCoordinates = false, isNight = false)
        assertEquals(ThemeMode.LIGHT, day?.mode)
        assertEquals(ThemeSource.CLOCK, day?.source)
    }

    @Test
    fun firstRun_gpsWritesSolarAndIsSticky() {
        val gps = firstRunThemeWrite(null, null, hasCoordinates = true, isNight = false)
        assertEquals(ThemeMode.LIGHT, gps?.mode)
        assertEquals(ThemeSource.GPS, gps?.source)
        assertNull(
            firstRunThemeWrite(
                existingMode = ThemeMode.LIGHT,
                existingSource = ThemeSource.GPS,
                hasCoordinates = false,
                isNight = true
            )
        )
    }

    @Test
    fun firstRun_clockMayBeReplacedByGpsOnce() {
        val override = firstRunThemeWrite(
            existingMode = ThemeMode.DARK,
            existingSource = ThemeSource.CLOCK,
            hasCoordinates = true,
            isNight = false
        )
        assertEquals(ThemeMode.LIGHT, override?.mode)
        assertEquals(ThemeSource.GPS, override?.source)
    }

    @Test
    fun firstRun_secondClockSettleIsIgnored() {
        assertNull(
            firstRunThemeWrite(
                existingMode = ThemeMode.DARK,
                existingSource = ThemeSource.CLOCK,
                hasCoordinates = false,
                isNight = false
            )
        )
    }

    @Test
    fun firstRun_doesNotOverwriteUserOrLegacyPreference() {
        assertNull(
            firstRunThemeWrite(ThemeMode.LIGHT, ThemeSource.USER, hasCoordinates = true, isNight = true)
        )
        assertNull(
            firstRunThemeWrite(ThemeMode.DARK, null, hasCoordinates = true, isNight = false)
        )
        assertNull(
            firstRunThemeWrite(ThemeMode.SYSTEM, ThemeSource.USER, hasCoordinates = true, isNight = true)
        )
    }
}
