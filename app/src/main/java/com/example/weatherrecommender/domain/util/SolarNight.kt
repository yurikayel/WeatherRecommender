package com.example.weatherrecommender.domain.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Day/night helpers for Cycle theme resolution.
 *
 * Location-aware [isNightAt] uses solar elevation at lat/lng (below the horizon = night),
 * so it follows the place's local sunrise/sunset rather than the device time zone.
 * [isNightByLocalClock] is the fallback when GPS is not available (19:00–06:00).
 */
object SolarNight {

    const val CLOCK_NIGHT_START_HOUR = 19
    const val CLOCK_NIGHT_END_HOUR = 6

    /** Night if local hour is before 06:00 or at/after 19:00. */
    fun isNightByLocalClock(dateTime: ZonedDateTime): Boolean {
        val hour = dateTime.hour
        return hour < CLOCK_NIGHT_END_HOUR || hour >= CLOCK_NIGHT_START_HOUR
    }

    /** Night when the sun is below the horizon at [latitude]/[longitude] for [instant]. */
    fun isNightAt(latitude: Double, longitude: Double, instant: Instant): Boolean =
        solarElevationDegrees(latitude, longitude, instant) < 0.0

    /**
     * Solar night when both coordinates are present; otherwise local-clock 19:00–06:00.
     */
    fun isNightNow(
        latitude: Double?,
        longitude: Double?,
        now: Instant,
        zone: ZoneId
    ): Boolean {
        if (latitude == null || longitude == null) {
            return isNightByLocalClock(now.atZone(zone))
        }
        return isNightAt(latitude, longitude, now)
    }
}

/**
 * Solar elevation in degrees (NOAA / Meeus approximation). Positive = sun above the horizon.
 */
internal fun solarElevationDegrees(latitude: Double, longitude: Double, instant: Instant): Double {
    val jd = instant.epochSecond / 86400.0 + 2440587.5
    val n = jd - 2451545.0
    val meanLongitude = wrapDegrees(280.460 + 0.9856474 * n)
    val meanAnomalyRad = Math.toRadians(wrapDegrees(357.528 + 0.9856003 * n))
    val eclipticLongRad = Math.toRadians(
        meanLongitude + 1.915 * sin(meanAnomalyRad) + 0.020 * sin(2.0 * meanAnomalyRad)
    )
    val obliquityRad = Math.toRadians(23.439 - 0.0000004 * n)
    val declination = asin(sin(obliquityRad) * sin(eclipticLongRad))
    val rightAscension = atan2(cos(obliquityRad) * sin(eclipticLongRad), cos(eclipticLongRad))
    val gmstHours = wrapHours(18.697374558 + 24.06570982441908 * n)
    val lmstHours = wrapHours(gmstHours + longitude / 15.0)
    val hourAngle = Math.toRadians(lmstHours * 15.0) - rightAscension
    val latRad = Math.toRadians(latitude)
    val sinElevation = sin(latRad) * sin(declination) +
        cos(latRad) * cos(declination) * cos(hourAngle)
    return Math.toDegrees(asin(sinElevation.coerceIn(-1.0, 1.0)))
}

/** Wraps an angle into [0, 360). */
private fun wrapDegrees(value: Double): Double {
    val wrapped = value % 360.0
    return if (wrapped < 0.0) wrapped + 360.0 else wrapped
}

/** Wraps a hour-of-day value into [0, 24). */
private fun wrapHours(value: Double): Double {
    val wrapped = value % 24.0
    return if (wrapped < 0.0) wrapped + 24.0 else wrapped
}
