package com.example.weatherrecommender.ui.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Lightweight ISO-date helpers used by the UI.
 *
 * Uses [SimpleDateFormat] rather than `java.time` so we stay compatible with `minSdk 24` without
 * requiring core-library desugaring. Parsing failures degrade gracefully to a raw substring.
 */
private const val ISO_PATTERN = "yyyy-MM-dd"

/** Returns a full localized weekday for an ISO date, e.g. "Sunday". */
fun isoDateToWeekday(isoDate: String): String = formatIso(isoDate, "EEEE") ?: isoDate.takeLast(5)

/** Returns an abbreviated localized weekday for an ISO date, e.g. "Sun". */
fun isoDateToShortWeekday(isoDate: String): String = formatIso(isoDate, "EEE") ?: isoDate.takeLast(3)

/** Returns the day-of-month for an ISO date, e.g. "17". */
fun isoDateToDayOfMonth(isoDate: String): String = formatIso(isoDate, "d") ?: isoDate.takeLast(2)

/** Returns a short localized month + day for an ISO date, e.g. "Jul 17". */
fun isoDateToShortDate(isoDate: String): String = formatIso(isoDate, "MMM d") ?: isoDate.takeLast(5)

private fun formatIso(isoDate: String, outputPattern: String): String? {
    return try {
        val parsed = SimpleDateFormat(ISO_PATTERN, Locale.US).parse(isoDate) ?: return null
        SimpleDateFormat(outputPattern, Locale.getDefault()).format(parsed)
    } catch (_: Exception) {
        null
    }
}
