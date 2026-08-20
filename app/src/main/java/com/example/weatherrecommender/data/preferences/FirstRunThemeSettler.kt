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
 *
 * A clock-only settle is provisional: a later GPS fix may replace it once.
 * A GPS settle, and any user/system choice, is sticky.
 */
@Singleton
class FirstRunThemeSettler @Inject constructor(
    private val themePreferences: ThemePreferences
) {
    private val mutex = Mutex()

    /** Persists Light/Dark from solar night (or clock) when first-run is still open. */
    suspend fun settle(
        coordinates: GeoCoordinates?,
        now: Instant = Clock.systemUTC().instant(),
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        mutex.withLock {
            val write = firstRunThemeWrite(
                existingMode = themePreferences.currentMode(),
                existingSource = themePreferences.currentSource(),
                hasCoordinates = coordinates != null,
                isNight = isNightNow(coordinates, now, zone)
            ) ?: return
            themePreferences.setThemeMode(write.mode, write.source)
        }
    }

    /** Solar night at [coordinates], or local-clock 19:00–06:00 when GPS is missing. */
    private fun isNightNow(
        coordinates: GeoCoordinates?,
        now: Instant,
        zone: ZoneId
    ): Boolean {
        return if (coordinates != null) {
            SolarNight.isNightAt(coordinates.latitude, coordinates.longitude, now)
        } else {
            SolarNight.isNightByLocalClock(now.atZone(zone))
        }
    }
}

/** Mode + provenance to persist on first run (or clock→GPS override). */
internal data class FirstRunThemeWrite(
    val mode: ThemeMode,
    val source: ThemeSource
)

/**
 * @return Light/Dark plus CLOCK or GPS, or null when first-run is closed
 * (user/system choice, or GPS already settled, or a second clock settle).
 */
internal fun firstRunThemeWrite(
    existingMode: ThemeMode?,
    existingSource: ThemeSource?,
    hasCoordinates: Boolean,
    isNight: Boolean
): FirstRunThemeWrite? {
    val source = existingSource ?: existingMode?.let { ThemeSource.USER }
    val closed = source == ThemeSource.USER ||
        source == ThemeSource.GPS ||
        (source == ThemeSource.CLOCK && !hasCoordinates)
    if (closed) return null
    return FirstRunThemeWrite(
        mode = if (isNight) ThemeMode.DARK else ThemeMode.LIGHT,
        source = if (hasCoordinates) ThemeSource.GPS else ThemeSource.CLOCK
    )
}
