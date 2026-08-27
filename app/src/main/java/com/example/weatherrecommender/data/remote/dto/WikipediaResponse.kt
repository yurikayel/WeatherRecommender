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
 * Encapsulates pageimages and/or search results from a MediaWiki query.
 *
 * @property pages Map of numeric page IDs to page metadata objects.
 * @property search Ordered search hits when `list=search` was requested.
 */
@Serializable
data class WikipediaQuery(
    val pages: Map<String, WikipediaPage>? = null,
    val search: List<WikipediaSearchHit>? = null
)

/**
 * Metadata for a single Wikipedia article or page.
 *
 * @property pageid Unique page identifier in the MediaWiki database (negative when missing).
 * @property title Title of the Wikipedia page.
 * @property missing Present (often as an empty string) when the title does not resolve to a page.
 * @property thumbnail Thumbnail image descriptor (when piprop includes thumbnail).
 * @property original Original high-res image descriptor (when piprop includes original).
 */
@Serializable
data class WikipediaPage(
    val pageid: Long? = null,
    val title: String? = null,
    val missing: String? = null,
    val thumbnail: WikipediaImage? = null,
    val original: WikipediaImage? = null
)

/**
 * One hit from MediaWiki `list=search`.
 *
 * @property title Canonical page title for a follow-up pageimages request.
 * @property pageid Numeric page id when the hit resolved.
 */
@Serializable
data class WikipediaSearchHit(
    val title: String,
    val pageid: Long? = null
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
