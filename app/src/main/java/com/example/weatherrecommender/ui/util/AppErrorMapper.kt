package com.example.weatherrecommender.ui.util

import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.AppError

/**
 * Maps domain-layer [AppError]s into presentation-layer [UiText] resources.
 * This guarantees that business logic errors are securely decoupled from
 * Android context or specific string resources.
 *
 * @return The localized [UiText] representing the error.
 */
fun AppError.asUiText(): UiText {
    return when (this) {
        is AppError.ApiError.NotFound -> UiText.StringResource(R.string.error_api_not_found)
        is AppError.ApiError.RateLimitExceeded -> UiText.StringResource(R.string.error_api_rate_limit)
        is AppError.NetworkError.NoConnectivity -> UiText.StringResource(R.string.error_network_offline)
        is AppError.NetworkError.ServerError -> UiText.StringResource(R.string.error_network_server, code)
        is AppError.NetworkError.Timeout -> UiText.StringResource(R.string.error_network_timeout)
        is AppError.NetworkError.Unknown -> UiText.StringResource(R.string.error_unknown)
        is AppError.Unknown -> UiText.StringResource(R.string.error_unknown)
    }
}
