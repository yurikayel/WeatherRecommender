package com.example.weatherrecommender.domain.model

/**
 * Represents a geographical location returned from the geocoding service.
 *
 * @property id Unique identifier for the location.
 * @property name Primary name of the location (e.g., city name).
 * @property latitude Geographic coordinate.
 * @property longitude Geographic coordinate.
 * @property country Country name where the location resides.
 * @property admin1 Primary administrative division (e.g., state or region).
 * @property elevation Ground elevation in meters (used to gate mountain activities like skiing).
 * @property population Number of inhabitants, when known (used to weight "top pick" suggestions).
 * @property featureCode GeoNames feature code (e.g., "PPLC" for a capital).
 * @property hasSeaAccess True when the coordinate is close enough to open water to support surfing.
 * @property imageUrl Optional Wikimedia thumbnail URL (Wikipedia enrichment; not from Open-Meteo).
 * @property description Optional Wikipedia page extract for the city.
 * @property imageAttribution Short credit for the image/extract (e.g. Wikipedia page title).
 */
data class Location(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String?,
    val elevation: Double? = null,
    val population: Long? = null,
    val featureCode: String? = null,
    val hasSeaAccess: Boolean = false,
    val imageUrl: String? = null,
    val description: String? = null,
    val imageAttribution: String? = null
) {
    /**
     * Formats the location properties into a presentable string (e.g., "Paris, Île-de-France, France").
     */
    val displayName: String
        get() = listOfNotNull(name, admin1, country).joinToString(", ")
}

/**
 * Encapsulates the weather conditions for a single day.
 *
 * @property date ISO-8601 formatted date string (e.g., "2023-10-15").
 * @property weatherCode WMO Weather interpretation code.
 * @property maxTemp Maximum temperature in Celsius.
 * @property minTemp Minimum temperature in Celsius.
 * @property precipitationSum Total liquid precipitation in millimeters.
 * @property snowfallSum Total snowfall in centimeters.
 * @property maxWindSpeed Maximum wind speed in km/h.
 * @property waveHeightMax Maximum significant wave height in meters, or null when the location has no sea access.
 */
data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val maxTemp: Double,
    val minTemp: Double,
    val precipitationSum: Double,
    val snowfallSum: Double,
    val maxWindSpeed: Double,
    val waveHeightMax: Double? = null
) {
    /** Mean temperature for the day, a convenient input for several scorers. */
    val avgTemp: Double
        get() = (maxTemp + minTemp) / 2
}

/**
 * Aggregates a [Location] with its corresponding 7-day [DailyForecast].
 *
 * @property location The geographical location of the forecast.
 * @property dailyForecasts A list of daily weather conditions.
 */
data class WeatherForecast(
    val location: Location,
    val dailyForecasts: List<DailyForecast>
)

/**
 * Represents the predefined list of activities that can be recommended to the user.
 */
enum class RecommendedActivity {
    SKIING,
    SURFING,
    OUTDOOR_SIGHTSEEING,
    INDOOR_SIGHTSEEING
}

/**
 * Bundles everything a scorer needs to evaluate an activity for a single day.
 *
 * Scoring is intentionally per-day (rather than aggregated over the week) so the UI can show how
 * suitability changes from one day to the next. The [location] provides the geographic gating
 * signals (sea access, elevation) used to decide whether an activity even makes sense here.
 *
 * @property location The geography the activity is being evaluated for.
 * @property day The single day of weather being scored.
 */
data class ActivityContext(
    val location: Location,
    val day: DailyForecast
)

/**
 * A featured location surfaced on the home screen, paired with its single best activity for today.
 *
 * @property location The suggested location.
 * @property topActivity The highest-scoring activity for the nearest day.
 * @property weatherCode WMO weather code for the nearest day, used for the summary icon.
 * @property maxTemp Maximum temperature for the nearest day.
 */
data class TopPick(
    val location: Location,
    val topActivity: RankedActivity,
    val weatherCode: Int,
    val maxTemp: Double
)

/**
 * Keys mapped to specific localization strings to explain the heuristic scores.
 */
enum class ReasonKey {
    SURF_IDEAL,
    SKI_IDEAL,
    OUTDOOR_MILD,
    INDOOR_BAD_WEATHER
}

/**
 * A scored and reasoned activity recommendation based on weather conditions.
 *
 * @property activity The specific activity being recommended.
 * @property score A normalized value from 0 to 100 representing suitability.
 * @property reasonKey Key for the localized string explaining the score.
 * @property reasonArgs Arguments to be injected into the localized string.
 */
data class RankedActivity(
    val activity: RecommendedActivity,
    val score: Int, // e.g. 0-100
    val reasonKey: ReasonKey,
    val reasonArgs: List<Any> = emptyList()
)
