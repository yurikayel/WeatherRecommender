package com.example.weatherrecommender.data.preferences

import com.example.weatherrecommender.domain.location.GeoCoordinates
import com.example.weatherrecommender.domain.util.SolarNight
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Writes the first-launch [ThemeMode] from day/night at the device location.
 * No-ops once any preference (Light / Dark / System) has been stored, so a later GPS
 * fix or clock change cannot override the user — or the first decision.
 */
@Singleton
class FirstRunThemeSettler @Inject constructor(
    private val themePreferences: ThemePreferences
) {
    private val mutex = Mutex()

    suspend fun settle(
        coordinates: GeoCoordinates?,
        now: Instant = Clock.systemUTC().instant(),
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        mutex.withLock {
            val existing = themePreferences.currentMode()
            val night = if (coordinates != null) {
                SolarNight.isNightAt(coordinates.latitude, coordinates.longitude, now)
            } else {
                SolarNight.isNightByLocalClock(now.atZone(zone))
            }
            val next = firstRunThemeMode(existing, night) ?: return
            themePreferences.setThemeMode(next)
        }
    }
}

/**
 * @return Light/Dark to persist on first run, or null when [existing] is already set.
 */
internal fun firstRunThemeMode(existing: ThemeMode?, isNight: Boolean): ThemeMode? {
    if (existing != null) return null
    return if (isNight) ThemeMode.DARK else ThemeMode.LIGHT
}
