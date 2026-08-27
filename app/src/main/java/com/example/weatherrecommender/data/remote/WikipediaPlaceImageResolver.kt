package com.example.weatherrecommender.data.remote

import com.example.weatherrecommender.data.remote.dto.WikipediaPage
import com.example.weatherrecommender.data.remote.dto.WikipediaResponse
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a city postcard URL from English Wikipedia with redirects and search fallbacks.
 *
 * Lookup order:
 * 1. `pageimages` with `redirects=1` for the local display name (e.g. `La Habana` → `Havana`)
 * 2. Same for `"$name, $country"` when a country is present
 * 3. `list=search` then `pageimages` on the top hit
 *
 * Missing pages and pages without a thumbnail/original are skipped.
 */
@Singleton
class WikipediaPlaceImageResolver @Inject constructor(
    private val wikipediaApi: WikipediaApi
) {

    /**
     * Best-effort thumbnail URL for [cityName] (and optional [country]), or null when none found.
     */
    suspend fun resolve(cityName: String, country: String? = null): String? {
        val name = cityName.trim()
        if (name.isEmpty()) return null
        return try {
            extractImageUrl(wikipediaApi.getPageImage(titles = name))
                ?: resolveWithCountry(name, country)
                ?: resolveViaSearch(name, country)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Tries `"$name, $country"` when [country] is non-blank. */
    private suspend fun resolveWithCountry(name: String, country: String?): String? {
        val countryTrimmed = country?.trim().orEmpty()
        if (countryTrimmed.isEmpty()) return null
        return extractImageUrl(wikipediaApi.getPageImage(titles = "$name, $countryTrimmed"))
    }

    /** Searches Wikipedia then loads pageimages for the first hit title. */
    private suspend fun resolveViaSearch(name: String, country: String?): String? {
        val countryTrimmed = country?.trim().orEmpty()
        val query = if (countryTrimmed.isEmpty()) name else "$name $countryTrimmed"
        val title = wikipediaApi.searchPages(srsearch = query)
            .query
            ?.search
            ?.firstOrNull()
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return extractImageUrl(wikipediaApi.getPageImage(titles = title))
    }

    /** First usable thumbnail/original from a pageimages response, or null. */
    private fun extractImageUrl(response: WikipediaResponse): String? {
        val page = response.query?.pages?.values?.firstOrNull(::isUsablePage) ?: return null
        return page.thumbnail?.source ?: page.original?.source
    }

    /** True when [page] is a real article that carries at least one image URL. */
    private fun isUsablePage(page: WikipediaPage): Boolean {
        val missingOrInvalidId = page.missing != null || (page.pageid != null && page.pageid < 0L)
        return !missingOrInvalidId && (page.thumbnail != null || page.original != null)
    }
}
