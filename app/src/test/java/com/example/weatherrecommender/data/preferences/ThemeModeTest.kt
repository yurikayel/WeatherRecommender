package com.example.weatherrecommender.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun cycle_followsIsNight() {
        assertTrue(ThemeMode.CYCLE.resolveDarkTheme(isNight = true))
        assertFalse(ThemeMode.CYCLE.resolveDarkTheme(isNight = false))
    }

    @Test
    fun light_and_dark_ignoreNight() {
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(isNight = true))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(isNight = false))
    }

    @Test
    fun unset_usesCycleNightFallback() {
        val unset: ThemeMode? = null
        assertTrue(unset.resolveRenderedDarkTheme(isNight = true))
        assertFalse(unset.resolveRenderedDarkTheme(isNight = false))
    }

    @Test
    fun nextToggleMode_cyclesLightDarkCycle() {
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.nextToggleMode())
        assertEquals(ThemeMode.CYCLE, ThemeMode.DARK.nextToggleMode())
        assertEquals(ThemeMode.LIGHT, ThemeMode.CYCLE.nextToggleMode())
        assertEquals(ThemeMode.LIGHT, null.nextToggleMode())
    }

    @Test
    fun sourceWhenSelected_cycleIsAutomatic_locksAreUser() {
        assertEquals(ThemeSource.CLOCK, ThemeMode.CYCLE.sourceWhenSelected())
        assertEquals(ThemeSource.USER, ThemeMode.LIGHT.sourceWhenSelected())
        assertEquals(ThemeSource.USER, ThemeMode.DARK.sourceWhenSelected())
    }

    @Test
    fun firstRun_unsetWritesCycleFromClock() {
        val write = firstRunThemeWrite(null, null)
        assertEquals(ThemeMode.CYCLE, write?.mode)
        assertEquals(ThemeSource.CLOCK, write?.source)
    }

    @Test
    fun firstRun_clockProvisionalLightOrDarkMigratesToCycle() {
        val fromDark = firstRunThemeWrite(ThemeMode.DARK, ThemeSource.CLOCK)
        assertEquals(ThemeMode.CYCLE, fromDark?.mode)
        assertEquals(ThemeSource.CLOCK, fromDark?.source)
        val fromLight = firstRunThemeWrite(ThemeMode.LIGHT, ThemeSource.CLOCK)
        assertEquals(ThemeMode.CYCLE, fromLight?.mode)
    }

    @Test
    fun firstRun_doesNotOverwriteCycleOrStickyLocks() {
        assertNull(firstRunThemeWrite(ThemeMode.CYCLE, ThemeSource.CLOCK))
        assertNull(firstRunThemeWrite(ThemeMode.LIGHT, ThemeSource.USER))
        assertNull(firstRunThemeWrite(ThemeMode.DARK, ThemeSource.GPS))
        assertNull(firstRunThemeWrite(ThemeMode.DARK, null))
    }
}
