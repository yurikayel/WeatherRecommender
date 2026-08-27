package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.Location

/**
 * Regional hubs used to prefetch neighbors visible at the detail map zoom.
 *
 * Canonical coordinates and ids live in [HubCities] so a featured top pick and a nearby
 * prefetch of the same city share one Room row.
 */
object MajorCities {
    /** Same list as [HubCities.all] (unique by [Location.placeKey]). */
    val all: List<Location> get() = HubCities.all
}
