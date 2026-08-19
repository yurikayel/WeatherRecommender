package com.example.weatherrecommender.ui

import com.example.weatherrecommender.domain.model.Location

/**
 * Async status for a lane that can show previous content while a new request is in flight
 * (search spinner over last results, pull-to-refresh over Top Picks).
 *
 * Distinct from a screen-level LCE: search, Top Picks, forecast, and map-tap are independent.
 */
enum class FetchStatus {
    Idle,
    Loading,
    Refreshing
}

/**
 * Search-bar geocoding as a sealed state so "spinner + previous hits" is explicit,
 * rather than an [isSearching] flag that can disagree with [searchResults].
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Loading(val previousResults: List<Location> = emptyList()) : SearchUiState
    data class Results(val locations: List<Location>) : SearchUiState
}
