package com.example.weatherrecommender.ui.util

import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.ReasonKey
import com.example.weatherrecommender.domain.model.RecommendedActivity

/**
 * Maps domain-layer [ReasonKey]s into presentation-layer [UiText] resources.
 * This guarantees that business logic errors are securely decoupled from
 * Android context or specific string resources, enabling localization.
 *
 * @param args Formatting arguments for the string.
 * @return The localized [UiText] representing the reason.
 */
fun ReasonKey.asUiText(args: List<Any>): UiText {
    return when (this) {
        ReasonKey.SURF_IDEAL -> UiText.StringResource(R.string.reason_surf_ideal, *args.toTypedArray())
        ReasonKey.SKI_IDEAL -> UiText.StringResource(R.string.reason_ski_ideal, *args.toTypedArray())
        ReasonKey.OUTDOOR_MILD -> UiText.StringResource(R.string.reason_outdoor_mild, *args.toTypedArray())
        ReasonKey.INDOOR_BAD_WEATHER -> UiText.StringResource(R.string.reason_indoor_bad_weather, *args.toTypedArray())
    }
}

/**
 * Maps domain-layer [RecommendedActivity]s into presentation-layer [UiText] resources.
 * Allows proper localization of activity names.
 *
 * @return The localized [UiText] representing the activity name.
 */
fun RecommendedActivity.asUiText(): UiText {
    return when (this) {
        RecommendedActivity.SKIING -> UiText.StringResource(R.string.activity_skiing)
        RecommendedActivity.SURFING -> UiText.StringResource(R.string.activity_surfing)
        RecommendedActivity.OUTDOOR_SIGHTSEEING -> UiText.StringResource(R.string.activity_outdoor_sightseeing)
        RecommendedActivity.INDOOR_SIGHTSEEING -> UiText.StringResource(R.string.activity_indoor_sightseeing)
    }
}
