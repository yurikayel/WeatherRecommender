package com.example.weatherrecommender.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryCityCatalogTest {

    private val entries = listOf(
        CountryCityEntry("CU", "Artemisa", "Artemisa", 22.8, -82.8, 80_000, isCapital = true),
        CountryCityEntry("CU", "Havana", "Havana", 23.1, -82.4, 2_000_000, isCapital = true),
        CountryCityEntry("CU", "Tijuana-sized", "Holguín", 20.9, -76.3, 9_000_000, isCapital = false),
        CountryCityEntry("BR", "Brasília", "Federal District", -15.8, -47.9, 3_000_000, isCapital = true)
    )
    private val catalog = CountryCityCatalog(entries)

    @Test
    fun citiesFor_ordersCapitalsByPopulationThenMajors() {
        val cities = catalog.citiesFor("cu")
        assertEquals(listOf("Havana", "Artemisa", "Tijuana-sized"), cities.map { it.name })
        assertTrue(cities.all { it.countryCode == "CU" })
        assertEquals("Cuba", cities.first().country)
    }

    @Test
    fun citiesFor_unknownIsoIsEmpty() {
        assertTrue(catalog.citiesFor("ZZ").isEmpty())
        assertTrue(catalog.citiesFor(" ").isEmpty())
    }

    @Test
    fun citiesFor_usesStableCatalogIds() {
        val havana = catalog.citiesFor("CU").first { it.name == "Havana" }
        assertEquals(-200_001L, havana.id)
    }

    @Test
    fun isoForCountryName_matchesDisplayName() {
        assertEquals("PT", catalog.isoForCountryName("Portugal"))
        assertEquals("PT", catalog.isoForCountryName("PT"))
        assertEquals("GB", catalog.isoForCountryName("united kingdom"))
        assertEquals("GB", catalog.isoForCountryName("UK"))
        assertEquals("US", catalog.isoForCountryName("USA"))
        assertEquals(null, catalog.isoForCountryName(" "))
    }
}
