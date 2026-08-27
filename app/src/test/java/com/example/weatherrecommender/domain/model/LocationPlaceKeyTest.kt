package com.example.weatherrecommender.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPlaceKeyTest {

    @Test
    fun placeKey_normalizes_name_and_country() {
        val lisbon = Location(1, " Lisbon ", 38.7, -9.1, " Portugal ", "Lisbon")
        assertEquals("lisbon|portugal", lisbon.placeKey)
    }

    @Test
    fun placeKey_treats_missing_country_as_empty() {
        val unnamed = Location(2, "Lisbon", 38.7, -9.1, null, null)
        assertEquals("lisbon|", unnamed.placeKey)
    }

    @Test
    fun displayName_joins_present_parts() {
        val paris = Location(1, "Paris", 48.8, 2.3, "France", "Île-de-France")
        assertEquals("Paris, Île-de-France, France", paris.displayName)
    }

    @Test
    fun displayName_omits_null_admin() {
        val lisbon = Location(2, "Lisbon", 38.7, -9.1, "Portugal", null)
        assertEquals("Lisbon, Portugal", lisbon.displayName)
    }
}
