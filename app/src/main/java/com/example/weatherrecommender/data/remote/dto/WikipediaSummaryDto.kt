package com.example.weatherrecommender.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subset of the Wikipedia REST page summary payload used for city photo + extract enrichment.
 */
@Serializable
data class WikipediaSummaryDto(
    @SerialName("type") val type: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("displaytitle") val displayTitle: String? = null,
    @SerialName("extract") val extract: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("thumbnail") val thumbnail: WikipediaThumbnailDto? = null
)

@Serializable
data class WikipediaThumbnailDto(
    @SerialName("source") val source: String? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null
)
