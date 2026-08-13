package com.example.weatherrecommender.ui

import com.example.weatherrecommender.domain.model.Location

/** Explicit navigation target for the weather screen (home vs city detail). */
sealed interface WeatherDestination {
    data object Home : WeatherDestination
    data class Detail(val location: Location) : WeatherDestination
}
