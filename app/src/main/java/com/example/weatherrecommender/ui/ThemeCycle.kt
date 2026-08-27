package com.example.weatherrecommender.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.example.weatherrecommender.data.preferences.ThemeMode
import com.example.weatherrecommender.domain.location.DeviceLocationProvider
import com.example.weatherrecommender.domain.location.GeoCoordinates
import com.example.weatherrecommender.domain.util.SolarNight
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/** Interval for recomputing Cycle day/night while the app stays open. */
internal const val CYCLE_THEME_TICK_MS = 60_000L

/** Header toggle reads this so sheet bodies do not thread [ThemeMode] through the map scaffold. */
val LocalThemeMode = compositionLocalOf { ThemeMode.CYCLE }

/**
 * Live day/night for Cycle mode: solar elevation when a last-known fix exists, otherwise
 * the local clock. Recomputes about every 60s so an open session still tracks dusk.
 */
@Composable
fun rememberCycleIsNight(locationProvider: DeviceLocationProvider): Boolean {
    val initial = remember {
        SolarNight.isNightByLocalClock(ZonedDateTime.now(Clock.systemDefaultZone()))
    }
    val isNight by produceState(initialValue = initial, key1 = locationProvider) {
        while (true) {
            value = readCycleIsNight(locationProvider)
            delay(CYCLE_THEME_TICK_MS)
        }
    }
    return isNight
}

/** Last-known GPS solar night, or local-clock night when a fix is unavailable. */
internal suspend fun readCycleIsNight(locationProvider: DeviceLocationProvider): Boolean {
    val coords = locationFixOrNull(locationProvider)
    return SolarNight.isNightNow(
        latitude = coords?.latitude,
        longitude = coords?.longitude,
        now = Instant.now(),
        zone = ZoneId.systemDefault()
    )
}

/** Last-known device fix when permission is granted; otherwise null. */
internal suspend fun locationFixOrNull(
    locationProvider: DeviceLocationProvider
): GeoCoordinates? {
    if (!locationProvider.hasLocationPermission()) return null
    return locationProvider.getLastKnownLocation()
}
