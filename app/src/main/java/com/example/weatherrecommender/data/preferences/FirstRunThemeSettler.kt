package com.example.weatherrecommender.data.preferences

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists [ThemeMode.CYCLE] once on first launch so cold start is not unset.
 *
 * Solar/clock only affect rendered light/dark while the stored mode is Cycle.
 * A clock-provisional Light/Dark from an older build is migrated to Cycle;
 * GPS and user Light/Dark locks stay sticky.
 */
@Singleton
class FirstRunThemeSettler @Inject constructor(
    private val themePreferences: ThemePreferences
) {
    private val mutex = Mutex()

    /** Writes Cycle when first-run is still open; no-ops once Cycle or a lock is stored. */
    suspend fun settle() {
        mutex.withLock {
            val write = firstRunThemeWrite(
                existingMode = themePreferences.currentMode(),
                existingSource = themePreferences.currentSource()
            ) ?: return
            themePreferences.setThemeMode(write.mode, write.source)
        }
    }
}

/** Mode + provenance to persist on first run (or clock→Cycle migration). */
internal data class FirstRunThemeWrite(
    val mode: ThemeMode,
    val source: ThemeSource
)

/**
 * @return Cycle plus CLOCK, or null when first-run is closed (already Cycle, a user/GPS
 * Light/Dark lock, or a second automatic settle).
 */
internal fun firstRunThemeWrite(
    existingMode: ThemeMode?,
    existingSource: ThemeSource?
): FirstRunThemeWrite? {
    val closed = existingMode == ThemeMode.CYCLE ||
        isStickyLightOrDarkLock(existingMode, existingSource)
    if (closed) return null
    return FirstRunThemeWrite(ThemeMode.CYCLE, ThemeSource.CLOCK)
}

/**
 * True when a stored Light/Dark must not be replaced by Cycle: user choice, legacy GPS
 * settle, or a mode row with no source (treated as user). Clock-provisional Light/Dark
 * from older builds is not sticky.
 */
internal fun isStickyLightOrDarkLock(
    existingMode: ThemeMode?,
    existingSource: ThemeSource?
): Boolean {
    val isLockMode = existingMode == ThemeMode.LIGHT || existingMode == ThemeMode.DARK
    val source = existingSource ?: ThemeSource.USER
    return isLockMode && source != ThemeSource.CLOCK
}
