package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Data Transfer Object (DTO) representing a response from the MediaWiki Action API.
 *
 * @property query Encapsulated query results.
 */
@Serializable
data class WikipediaResponse(
    val query: WikipediaQuery? = null
)

/**
 * Encapsulates the map of page ID to [WikipediaPage] results.
 *
 * @property pages Map of numeric page IDs to page metadata objects.
 */
@Serializable
data class WikipediaQuery(
    val pages: Map<String, WikipediaPage>? = null
)

/**
 * Metadata for a single Wikipedia article or page.
 *
 * @property pageid Unique page identifier in the MediaWiki database.
 * @property title Title of the Wikipedia page.
 * @property thumbnail Thumbnail image descriptor (when piprop=thumbnail).
 * @property original Original high-res image descriptor (when piprop=original).
 */
@Serializable
data class WikipediaPage(
    val pageid: Long? = null,
    val title: String? = null,
    val thumbnail: WikipediaImage? = null,
    val original: WikipediaImage? = null
)

/**
 * Image resource metadata with direct source URL.
 *
 * @property source Publicly accessible CDN URL for the image.
 */
@Serializable
data class WikipediaImage(
    val source: String
)
