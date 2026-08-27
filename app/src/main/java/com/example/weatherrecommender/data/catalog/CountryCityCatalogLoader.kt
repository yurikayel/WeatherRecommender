package com.example.weatherrecommender.data.catalog

import android.content.Context
import com.example.weatherrecommender.domain.usecase.CountryCityEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled country-city JSON from assets. Domain [CountryCityCatalog] stays Android-free.
 */
@Singleton
class CountryCityCatalogLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json
) {
    /** Parses `country_cities.json`; empty list if the asset is missing. */
    fun load(): List<CountryCityEntry> {
        return try {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            json.decodeFromString<List<CountryCityEntry>>(text)
        } catch (_: IOException) {
            emptyList()
        }
    }

    private companion object {
        const val ASSET_NAME = "country_cities.json"
    }
}
