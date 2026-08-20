package com.example.weatherrecommender.ui

import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.ui.util.UiText

/**
 * Async status for one independent lane (forecast, Top Picks, map-tap).
 *
 * Distinct from a screen-level LCE: a search failure must not look like a forecast failure.
 * [Failed] carries that lane's message so banners stay local to the lane.
 */
sealed interface FetchStatus {
    data object Idle : FetchStatus
    data object Loading : FetchStatus
    data object Refreshing : FetchStatus
    data class Failed(val error: UiText) : FetchStatus
}

/** True while the lane is showing a first-load spinner. */
fun FetchStatus.isLoading(): Boolean = this is FetchStatus.Loading

/** True while the lane is refreshing over already-visible content. */
fun FetchStatus.isRefreshing(): Boolean = this is FetchStatus.Refreshing

/** Error text when this lane failed; null otherwise. */
fun FetchStatus.errorOrNull(): UiText? = (this as? FetchStatus.Failed)?.error

/**
 * Returns [FetchStatus.Idle] after an in-flight request completes, preserving [FetchStatus.Failed]
 * so a cached paint cannot wipe a refresh-error banner.
 */
fun FetchStatus.completeIfInFlight(): FetchStatus = when (this) {
    FetchStatus.Loading, FetchStatus.Refreshing -> FetchStatus.Idle
    else -> this
}

/**
 * Search-bar geocoding as a sealed state so "spinner + previous hits" and "error + previous hits"
 * are explicit, rather than an [isSearching] flag that can disagree with [searchResults].
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Loading(val previousResults: List<Location> = emptyList()) : SearchUiState
    data class Results(val locations: List<Location>) : SearchUiState
    data class Failed(
        val error: UiText,
        val previousResults: List<Location> = emptyList()
    ) : SearchUiState
}
