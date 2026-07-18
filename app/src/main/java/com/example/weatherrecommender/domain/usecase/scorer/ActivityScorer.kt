package com.example.weatherrecommender.domain.usecase.scorer

import com.example.weatherrecommender.domain.model.ActivityContext
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.RecommendedActivity
import kotlin.math.roundToInt

/**
 * Contract for evaluating the suitability of a specific activity for a single day at a location.
 *
 * Implementations encapsulate the heuristic logic for one [RecommendedActivity]. Scoring is
 * per-day so the UI can present a different ranking for each day of the forecast, and gated by
 * geography via [isApplicable] so nonsensical suggestions (e.g. surfing in a landlocked city) are
 * never shown.
 */
interface ActivityScorer {
    /** The specific type of activity this scorer evaluates. */
    val activityType: RecommendedActivity

    /**
     * Whether this activity is even plausible for the given location.
     *
     * For example, surfing requires sea access and skiing requires mountainous terrain. Activities
     * that fail this check are omitted from the ranking entirely rather than shown with a zero score.
     */
    fun isApplicable(context: ActivityContext): Boolean

    /**
     * Calculates a ranking score (0-100) for the activity for a single day.
     */
    fun score(context: ActivityContext): RankedActivity
}

/**
 * Surfing is only suggested where the coordinate has sea access (detected via the Marine API).
 * The score rewards rideable-but-manageable waves, warm air, and winds that aren't too strong.
 */
class SurfScorer : ActivityScorer {
    override val activityType = RecommendedActivity.SURFING

    companion object {
        private const val BASE_SCORE = 45
        private const val WAVE_MIN_RIDEABLE = 0.4
        private const val WAVE_IDEAL_MAX = 2.5
        private const val WIND_IDEAL_MAX = 20.0
        private const val WIND_BAD_MIN = 35.0
        private const val TEMP_WARM_MIN = 18.0
    }

    override fun isApplicable(context: ActivityContext): Boolean =
        context.location.hasSeaAccess

    override fun score(context: ActivityContext): RankedActivity {
        val day = context.day
        val wave = day.waveHeightMax ?: 0.0
        var score = BASE_SCORE

        when {
            wave < WAVE_MIN_RIDEABLE -> score -= 25
            wave <= WAVE_IDEAL_MAX -> score += 35
            else -> score -= 15
        }

        when {
            day.maxWindSpeed < WIND_IDEAL_MAX -> score += 15
            day.maxWindSpeed > WIND_BAD_MIN -> score -= 30
        }

        if (day.maxTemp >= TEMP_WARM_MIN) score += 10

        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = ReasonKey.SURF_IDEAL,
            reasonArgs = listOf((wave * 100).roundToInt(), day.maxWindSpeed.roundToInt())
        )
    }
}

/**
 * Skiing is only suggested for mountainous locations (by elevation) or when fresh snow is falling.
 * The score rewards accumulating snowfall and sub-freezing temperatures.
 */
class SkiScorer : ActivityScorer {
    override val activityType = RecommendedActivity.SKIING

    companion object {
        private const val BASE_SCORE = 20
        private const val ELEVATION_MOUNTAIN_MIN = 800.0
        private const val SNOW_IDEAL_MIN = 3.0
        private const val TEMP_FREEZING_MAX = 0.0
        private const val TEMP_MELTING_MIN = 6.0
    }

    override fun isApplicable(context: ActivityContext): Boolean {
        val elevation = context.location.elevation
        val mountainous = elevation != null && elevation >= ELEVATION_MOUNTAIN_MIN
        return mountainous || context.day.snowfallSum > 0.0
    }

    override fun score(context: ActivityContext): RankedActivity {
        val day = context.day
        var score = BASE_SCORE

        if (day.snowfallSum >= SNOW_IDEAL_MIN) score += 50
        else if (day.snowfallSum > 0.0) score += 25

        if (day.avgTemp < TEMP_FREEZING_MAX) score += 30
        if (day.avgTemp > TEMP_MELTING_MIN) score -= 40

        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = ReasonKey.SKI_IDEAL,
            reasonArgs = listOf(day.avgTemp.roundToInt(), day.snowfallSum.roundToInt())
        )
    }
}

/**
 * Outdoor sightseeing is always plausible. The score rewards mild, dry days and penalises rain,
 * strong wind, and uncomfortable heat.
 */
class OutdoorSightseeingScorer : ActivityScorer {
    override val activityType = RecommendedActivity.OUTDOOR_SIGHTSEEING

    companion object {
        private const val BASE_SCORE = 55
        private const val PRECIP_BAD_MIN = 5.0
        private const val TEMP_MILD_MIN = 14.0
        private const val TEMP_MILD_MAX = 26.0
        private const val TEMP_HOT_MIN = 32.0
        private const val WIND_STRONG_MIN = 35.0
    }

    override fun isApplicable(context: ActivityContext): Boolean = true

    override fun score(context: ActivityContext): RankedActivity {
        val day = context.day
        var score = BASE_SCORE

        if (day.precipitationSum > PRECIP_BAD_MIN) score -= 45
        if (day.avgTemp in TEMP_MILD_MIN..TEMP_MILD_MAX) score += 35
        if (day.avgTemp > TEMP_HOT_MIN) score -= 30
        if (day.maxWindSpeed > WIND_STRONG_MIN) score -= 15

        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = ReasonKey.OUTDOOR_MILD,
            reasonArgs = listOf(day.avgTemp.roundToInt())
        )
    }
}

/**
 * Indoor sightseeing is always plausible and becomes the go-to option when the weather turns.
 * The score rises with rain, snow, and cold.
 */
class IndoorSightseeingScorer : ActivityScorer {
    override val activityType = RecommendedActivity.INDOOR_SIGHTSEEING

    companion object {
        private const val BASE_SCORE = 45
        private const val PRECIP_BAD_MIN = 4.0
        private const val SNOW_BAD_MIN = 1.0
        private const val TEMP_COLD_MAX = 4.0
    }

    override fun isApplicable(context: ActivityContext): Boolean = true

    override fun score(context: ActivityContext): RankedActivity {
        val day = context.day
        var score = BASE_SCORE

        if (day.precipitationSum > PRECIP_BAD_MIN || day.snowfallSum > SNOW_BAD_MIN) score += 35
        if (day.minTemp < TEMP_COLD_MAX) score += 15

        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = ReasonKey.INDOOR_BAD_WEATHER
        )
    }
}
