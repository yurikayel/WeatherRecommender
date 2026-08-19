package com.example.weatherrecommender.domain.usecase.scorer

import com.example.weatherrecommender.domain.model.ActivityContext
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.domain.model.ScoringThresholds.INDOOR_BASE_SCORE
import com.example.weatherrecommender.domain.model.ScoringThresholds.INDOOR_PRECIP_BAD_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.INDOOR_SNOW_BAD_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.INDOOR_TEMP_COLD_MAX
import com.example.weatherrecommender.domain.model.ScoringThresholds.OUTDOOR_BASE_SCORE
import com.example.weatherrecommender.domain.model.ScoringThresholds.OUTDOOR_PRECIP_BAD_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.OUTDOOR_TEMP_HOT_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.OUTDOOR_TEMP_MILD_MAX
import com.example.weatherrecommender.domain.model.ScoringThresholds.OUTDOOR_TEMP_MILD_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.OUTDOOR_WIND_STRONG_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.SKI_BASE_SCORE
import com.example.weatherrecommender.domain.model.ScoringThresholds.SKI_ELEVATION_MOUNTAIN_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.SKI_SNOW_IDEAL_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.SKI_TEMP_FREEZING_MAX
import com.example.weatherrecommender.domain.model.ScoringThresholds.SKI_TEMP_MELTING_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.SURF_BASE_SCORE
import com.example.weatherrecommender.domain.model.ScoringThresholds.SURF_TEMP_WARM_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.SURF_WAVE_IDEAL_MAX
import com.example.weatherrecommender.domain.model.ScoringThresholds.SURF_WAVE_MIN_RIDEABLE
import com.example.weatherrecommender.domain.model.ScoringThresholds.SURF_WIND_BAD_MIN
import com.example.weatherrecommender.domain.model.ScoringThresholds.SURF_WIND_IDEAL_MAX
import javax.inject.Inject
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
class SurfScorer @Inject constructor() : ActivityScorer {
    override val activityType = RecommendedActivity.SURFING

    override fun isApplicable(context: ActivityContext): Boolean =
        context.location.hasSeaAccess

    override fun score(context: ActivityContext): RankedActivity {
        val day = context.day
        val wave = day.waveHeightMax ?: 0.0
        var score = SURF_BASE_SCORE

        when {
            wave < SURF_WAVE_MIN_RIDEABLE -> score -= 25
            wave <= SURF_WAVE_IDEAL_MAX -> score += 35
            else -> score -= 15
        }

        when {
            day.maxWindSpeed < SURF_WIND_IDEAL_MAX -> score += 15
            day.maxWindSpeed > SURF_WIND_BAD_MIN -> score -= 30
        }

        if (day.maxTemp >= SURF_TEMP_WARM_MIN) score += 10

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
class SkiScorer @Inject constructor() : ActivityScorer {
    override val activityType = RecommendedActivity.SKIING

    override fun isApplicable(context: ActivityContext): Boolean {
        val elevation = context.location.elevation
        val mountainous = elevation != null && elevation >= SKI_ELEVATION_MOUNTAIN_MIN
        return mountainous || context.day.snowfallSum > 0.0
    }

    override fun score(context: ActivityContext): RankedActivity {
        val day = context.day
        var score = SKI_BASE_SCORE

        if (day.snowfallSum >= SKI_SNOW_IDEAL_MIN) score += 50
        else if (day.snowfallSum > 0.0) score += 25

        if (day.avgTemp < SKI_TEMP_FREEZING_MAX) score += 30
        if (day.avgTemp > SKI_TEMP_MELTING_MIN) score -= 40

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
class OutdoorSightseeingScorer @Inject constructor() : ActivityScorer {
    override val activityType = RecommendedActivity.OUTDOOR_SIGHTSEEING

    override fun isApplicable(context: ActivityContext): Boolean = true

    override fun score(context: ActivityContext): RankedActivity {
        val day = context.day
        var score = OUTDOOR_BASE_SCORE

        if (day.precipitationSum > OUTDOOR_PRECIP_BAD_MIN) score -= 45
        if (day.avgTemp in OUTDOOR_TEMP_MILD_MIN..OUTDOOR_TEMP_MILD_MAX) score += 35
        if (day.avgTemp > OUTDOOR_TEMP_HOT_MIN) score -= 30
        if (day.maxWindSpeed > OUTDOOR_WIND_STRONG_MIN) score -= 15

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
class IndoorSightseeingScorer @Inject constructor() : ActivityScorer {
    override val activityType = RecommendedActivity.INDOOR_SIGHTSEEING

    override fun isApplicable(context: ActivityContext): Boolean = true

    override fun score(context: ActivityContext): RankedActivity {
        val day = context.day
        var score = INDOOR_BASE_SCORE

        if (day.precipitationSum > INDOOR_PRECIP_BAD_MIN || day.snowfallSum > INDOOR_SNOW_BAD_MIN) {
            score += 35
        }
        if (day.minTemp < INDOOR_TEMP_COLD_MAX) score += 15

        return RankedActivity(
            activity = activityType,
            score = score.coerceIn(0, 100),
            reasonKey = ReasonKey.INDOOR_BAD_WEATHER
        )
    }
}
