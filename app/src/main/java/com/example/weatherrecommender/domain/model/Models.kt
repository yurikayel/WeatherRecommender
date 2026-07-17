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
 */
data class Location(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String?
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
 */
data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val maxTemp: Double,
    val minTemp: Double,
    val precipitationSum: Double,
    val snowfallSum: Double,
    val maxWindSpeed: Double
)

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
