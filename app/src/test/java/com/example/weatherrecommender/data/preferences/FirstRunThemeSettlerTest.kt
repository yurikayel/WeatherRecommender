package com.example.weatherrecommender.data.preferences

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FirstRunThemeSettlerTest {

    private val themePreferences = mockk<ThemePreferences>(relaxUnitFun = true)
    private val settler = FirstRunThemeSettler(themePreferences)

    @Test
    fun settle_writesCycleWhenUnset() = runTest {
        stub(mode = null, source = null)
        settler.settle()
        coVerify { themePreferences.setThemeMode(ThemeMode.CYCLE, ThemeSource.CLOCK) }
    }

    @Test
    fun settle_migratesClockProvisionalDarkToCycle() = runTest {
        stub(mode = ThemeMode.DARK, source = ThemeSource.CLOCK)
        settler.settle()
        coVerify { themePreferences.setThemeMode(ThemeMode.CYCLE, ThemeSource.CLOCK) }
    }

    @Test
    fun settle_skipsWhenAlreadyCycle() = runTest {
        stub(mode = ThemeMode.CYCLE, source = ThemeSource.CLOCK)
        settler.settle()
        coVerify(exactly = 0) { themePreferences.setThemeMode(any(), any()) }
    }

    @Test
    fun settle_doesNotOverwriteUserLock() = runTest {
        stub(mode = ThemeMode.LIGHT, source = ThemeSource.USER)
        settler.settle()
        coVerify(exactly = 0) { themePreferences.setThemeMode(any(), any()) }
    }

    @Test
    fun settle_doesNotOverwriteLegacyGpsLock() = runTest {
        stub(mode = ThemeMode.DARK, source = ThemeSource.GPS)
        settler.settle()
        coVerify(exactly = 0) { themePreferences.setThemeMode(any(), any()) }
    }

    private fun stub(mode: ThemeMode?, source: ThemeSource?) {
        coEvery { themePreferences.currentMode() } returns mode
        coEvery { themePreferences.currentSource() } returns source
    }
}
