package com.example.weatherrecommender.domain.model

/**
 * Centralised meteorological and comfort thresholds for activity scoring and UI score bands.
 *
 * Scores are 0–100; [BASE_SCORE] values in each scorer set a neutral starting point before
 * weather adjustments, so rankings stay relative within a day rather than absolute forecasts.
 */
object ScoringThresholds {

    /** UI band: primary colour for strong suitability (≥ this score). */
    const val SCORE_HIGH = 75

    /** UI band: secondary colour for moderate suitability (above this, below [SCORE_HIGH]). */
    const val SCORE_MID = 40

    // --- SurfScorer ---

    /** Neutral-positive base; ideal waves + light wind push scores toward 100. */
    const val SURF_BASE_SCORE = 45

    /** Below ~40 cm chop is not rideable (surf guides / Beaufort scale). */
    const val SURF_WAVE_MIN_RIDEABLE = 0.4

    /** Above 2.5 m is hazardous for recreational surfers. */
    const val SURF_WAVE_IDEAL_MAX = 2.5

    /** Up to 20 km/h keeps a clean wave face. */
    const val SURF_WIND_IDEAL_MAX = 20.0

    /** Beaufort 5+; unsafe for casual surf. */
    const val SURF_WIND_BAD_MIN = 35.0

    /** Below 18 °C reduces comfort without a wetsuit. */
    const val SURF_TEMP_WARM_MIN = 18.0

    // --- SkiScorer ---

    /** Low base; snowfall and freeze bonuses do most of the ranking work. */
    const val SKI_BASE_SCORE = 20

    /** Typical minimum resort altitude in the Alps / Pyrenees (~800 m). */
    const val SKI_ELEVATION_MOUNTAIN_MIN = 800.0

    /** At least 3 cm fresh snow for a decent groomed run. */
    const val SKI_SNOW_IDEAL_MIN = 3.0

    /** At or below freezing preserves snow quality. */
    const val SKI_TEMP_FREEZING_MAX = 0.0

    /** Rapid melt above 6 °C average. */
    const val SKI_TEMP_MELTING_MIN = 6.0

    // --- OutdoorSightseeingScorer ---

    /** Mild-day default; rain, heat, and wind subtract from a comfortable starting point. */
    const val OUTDOOR_BASE_SCORE = 55

    /** Moderate rain discourages walking tours (> 5 mm/day). */
    const val OUTDOOR_PRECIP_BAD_MIN = 5.0

    /** ASHRAE-style mild comfort band for outdoor activity. */
    const val OUTDOOR_TEMP_MILD_MIN = 14.0

    const val OUTDOOR_TEMP_MILD_MAX = 26.0

    /** Extreme heat for extended walking (> 32 °C). */
    const val OUTDOOR_TEMP_HOT_MIN = 32.0

    /** Beaufort 5+ strong wind. */
    const val OUTDOOR_WIND_STRONG_MIN = 35.0

    // --- IndoorSightseeingScorer ---

    /** Neutral indoor fallback; rain, snow, and cold raise it above outdoor options. */
    const val INDOOR_BASE_SCORE = 45

    /** Slightly lower rain threshold than outdoor — indoor rises sooner. */
    const val INDOOR_PRECIP_BAD_MIN = 4.0

    /** Any measurable snow nudges toward indoor. */
    const val INDOOR_SNOW_BAD_MIN = 1.0

    /** Uncomfortably cold for waiting outside (~4 °C min). */
    const val INDOOR_TEMP_COLD_MAX = 4.0
}
