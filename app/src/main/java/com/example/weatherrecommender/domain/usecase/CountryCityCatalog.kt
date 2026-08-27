package com.example.weatherrecommender.domain.usecase

import com.example.weatherrecommender.domain.model.Location
import kotlinx.serialization.Serializable

/**
 * Bundled country city list used to warm Room when GPS resolves a country.
 *
 * Open-Meteo cannot enumerate cities by country, so this catalog is the discovery source.
 * [citiesFor] returns admin capitals (by population) then other majors (by population).
 * Synthetic ids live in [CATALOG_ID_BASE]… so they never collide with Featured, Major, or Nominatim.
 *
 * Entries are injected from assets via Hilt ([com.example.weatherrecommender.di.CatalogModule]);
 * tests construct this with an in-memory list.
 */
class CountryCityCatalog(private val entries: List<CountryCityEntry>) {

    /**
     * Catalog cities for [countryCode] (case-insensitive ISO alpha-2), capitals first then majors,
     * each group sorted by population descending. Empty when the ISO is unknown.
     */
    fun citiesFor(countryCode: String): List<Location> {
        val code = countryCode.trim().uppercase()
        if (code.isEmpty()) return emptyList()
        return entries
            .mapIndexed { index, entry -> entry to catalogId(index) }
            .filter { (entry, _) -> entry.countryCode.uppercase() == code }
            .sortedWith(
                compareByDescending<Pair<CountryCityEntry, Long>> { it.first.isCapital }
                    .thenByDescending { it.first.population }
            )
            .map { (entry, id) -> entry.toLocation(id) }
    }

    /**
     * ISO alpha-2 for a country display name (e.g. `"Portugal"` → `"PT"`), or null when unknown.
     * Used when search/top-picks rows have a country name but no Nominatim/Open-Meteo code.
     */
    fun isoForCountryName(country: String?): String? {
        val name = country?.trim().orEmpty()
        if (name.isEmpty()) return null
        val upper = name.uppercase()
        val fromIso = upper.takeIf { it.length == 2 && COUNTRY_NAMES.containsKey(it) }
        return fromIso
            ?: COUNTRY_ALIASES[upper]
            ?: COUNTRY_NAMES.entries
                .firstOrNull { it.value.equals(name, ignoreCase = true) }
                ?.key
    }

    private companion object {
        const val CATALOG_ID_BASE = -200_000L

        /** Stable synthetic id for the JSON row at [index]. */
        fun catalogId(index: Int): Long = CATALOG_ID_BASE - index
    }
}

/**
 * One row in `country_cities.json`.
 *
 * @property countryCode ISO 3166-1 alpha-2.
 * @property name City name used for Wikipedia lookup.
 * @property admin1 State, province, or region.
 * @property latitude WGS84 latitude.
 * @property longitude WGS84 longitude.
 * @property population Inhabitant count used for warm ordering.
 * @property isCapital True for a national or first-level administrative capital.
 */
@Serializable
data class CountryCityEntry(
    val countryCode: String,
    val name: String,
    val admin1: String,
    val latitude: Double,
    val longitude: Double,
    val population: Long,
    val isCapital: Boolean = false
) {
    /** Maps this seed row onto a domain [Location] with stable catalog [id]. */
    fun toLocation(id: Long): Location = Location(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        country = COUNTRY_NAMES[countryCode.uppercase()] ?: countryCode.uppercase(),
        admin1 = admin1,
        population = population,
        featureCode = if (isCapital) "PPLA" else "PPL",
        countryCode = countryCode.uppercase()
    )
}

private val COUNTRY_ALIASES = mapOf(
    "UK" to "GB",
    "USA" to "US"
)

private val COUNTRY_NAMES = mapOf(
    "AR" to "Argentina",
    "AU" to "Australia",
    "BR" to "Brazil",
    "CH" to "Switzerland",
    "CL" to "Chile",
    "CU" to "Cuba",
    "DE" to "Germany",
    "ES" to "Spain",
    "FR" to "France",
    "GB" to "United Kingdom",
    "IS" to "Iceland",
    "IT" to "Italy",
    "JP" to "Japan",
    "MX" to "Mexico",
    "NL" to "Netherlands",
    "PT" to "Portugal",
    "US" to "United States",
    "UY" to "Uruguay",
    "ZA" to "South Africa"
)
